package net.sonflowers.jeecore.gene.terragolem.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;
import net.sonflowers.jeecore.core.registry.ModMobEffects;
import net.sonflowers.jeecore.gene.terragolem.network.TerragolemOverheatDebuffPayload;
import net.sonflowers.jeecore.gene.terragolem.network.TerragolemOverheatPayload;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks server primary-ability heat, delayed decay, backlash, and the primary lock until heat
 * drains completely.
 */
public class TerragolemOverheatManager {
    // Heat limits and rates per game tick.
    public static final int MAX_OVERHEAT = 500;

    private static final int SYNC_INTERVAL = 5; // Ticks between heat syncs while it changes.
    private static final int DECAY_DELAY_TICKS = 80; // 4 seconds of inactivity before decay starts.
    private static final float DECAY_PER_TICK = 0.4f;
    private static final float HOLD_HEAT_PER_TICK = 3.5f;
    private static final float OVERCHARGE_DECAY_MULTIPLIER = 0.5f;

    // Player UUID to current heat (float internally for smooth ramps; HUD uses the int).
    private static final Map<UUID, Float> HEAT = new ConcurrentHashMap<>();
    // Player UUID to whether the overheat debuff threshold has already been triggered.
    private static final Map<UUID, Boolean> OVERHEATED = new ConcurrentHashMap<>();
    // Player UUID to primary locked by the overheat debuff until the bar fully drains.
    private static final Map<UUID, Boolean> OVERHEAT_LOCKED = new ConcurrentHashMap<>();
    // Player UUID to game time when the "Overheated" actionbar message stops (4 seconds).
    private static final Map<UUID, Long> OVERHEAT_MSG_END = new ConcurrentHashMap<>();
    // Player UUID to last game time the primary ability was used (drives the decay delay).
    private static final Map<UUID, Long> LAST_ACTIVITY = new ConcurrentHashMap<>();
    // Player UUID to last visibility state sent for the blinking overheat action-bar message.
    private static final Map<UUID, Boolean> OVERHEAT_MESSAGE_VISIBLE = new ConcurrentHashMap<>();

    public static float getHeat(UUID uuid) {
        return HEAT.getOrDefault(uuid, 0.0f);
    }

    public static boolean isOverheated(UUID uuid) {
        return getHeat(uuid) >= MAX_OVERHEAT;
    }

    public static void reduceHeat(Player player, float amount) {
        if (player.level().isClientSide || !Float.isFinite(amount) || amount <= 0.0F) return;
        float previous = getHeat(player.getUUID());
        setHeat(player, previous - amount);
        if (getHeat(player.getUUID()) != previous) sync(player);
    }

    public static boolean isPrimaryLocked(UUID uuid) {
        return OVERHEAT_LOCKED.getOrDefault(uuid, false);
    }

    // Left-button release burst heat by segment tier: 1-2 -> 60, 3-4 -> 120, 5-6 -> 160.
    public static void addBurstHeat(Player player, int segments) {
        int amount = segments <= 0 ? 0 : segments <= 2 ? 60 : segments <= 4 ? 120 : 160;
        if (amount <= 0) return;
        LAST_ACTIVITY.put(player.getUUID(), player.level().getGameTime());
        setHeat(player, getHeat(player.getUUID()) + amount);
        sync(player);
    }

    public static void tick(Player player) {
        UUID uuid = player.getUUID();
        float heat = getHeat(uuid);
        long now = player.level().getGameTime();
        if (isPrimaryLocked(uuid) && heat > 0 && player.isAlive()) ensureOverheatedEffect(player);
        else player.removeEffect(ModMobEffects.OVERHEATED);

        // Blinking "Overheated" actionbar for 4 seconds after the debuff triggers.
        Long msgEnd = OVERHEAT_MSG_END.get(uuid);
        if (msgEnd != null) {
            if (now >= msgEnd) {
                OVERHEAT_MSG_END.remove(uuid);
                OVERHEAT_MESSAGE_VISIBLE.remove(uuid);
                player.displayClientMessage(Component.empty(), true);
            } else {
                boolean visible = (now / 4) % 2 == 0;
                Boolean previous = OVERHEAT_MESSAGE_VISIBLE.put(uuid, visible);
                if (previous == null || previous != visible) {
                    player.displayClientMessage(visible
                            ? Component.literal("⚠ Overheated ⚠").withStyle(ChatFormatting.RED)
                            : Component.empty(), true);
                }
            }
        }

        boolean forceSync = false;
        if (TerragolemReadyUpManager.isHeld(uuid)) {
            LAST_ACTIVITY.put(uuid, now);
            // Continuous linear fill while the beam is held.
            heat += HOLD_HEAT_PER_TICK;
            forceSync = setHeat(player, heat);
        } else if (TerragolemReadyUpManager.isReadyUp(uuid)) {
            LAST_ACTIVITY.put(uuid, now);
            return;
        } else if (heat > 0) {
            long decayElapsed = now - LAST_ACTIVITY.getOrDefault(uuid, 0L) - DECAY_DELAY_TICKS;
            if (decayElapsed >= 0) {
                // Passive decay accelerates the longer the bar drains; using the primary resets the
                // ramp via LAST_ACTIVITY.
                float rate = DECAY_PER_TICK * (1.0f + decayElapsed / 100.0f);
                // The lock survives dropping below maximum heat and clears only at zero.
                if (isPrimaryLocked(uuid)) rate *= OVERCHARGE_DECAY_MULTIPLIER;
                forceSync = setHeat(player, heat - rate);
            } else {
                return;
            }
        } else {
            return;
        }

        if (forceSync || player.tickCount % SYNC_INTERVAL == 0) {
            sync(player);
        }
    }

    public static void clearHeat(Player player) {
        player.removeEffect(ModMobEffects.OVERHEATED);
        HEAT.remove(player.getUUID());
        OVERHEATED.remove(player.getUUID());
        OVERHEAT_LOCKED.remove(player.getUUID());
        OVERHEAT_MSG_END.remove(player.getUUID());
        LAST_ACTIVITY.remove(player.getUUID());
        OVERHEAT_MESSAGE_VISIBLE.remove(player.getUUID());
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        data.setOverheatBar(0);
        sync(player);
    }

    private static boolean setHeat(Player player, float value) {
        UUID uuid = player.getUUID();
        float previous = getHeat(uuid);
        float clamped = Math.max(0.0f, Math.min(MAX_OVERHEAT, value));
        boolean wasOverheated = OVERHEATED.getOrDefault(uuid, false);
        boolean forceSync = false;

        if (clamped > 0.0f) {
            HEAT.put(uuid, clamped);
        } else {
            HEAT.remove(uuid);
            LAST_ACTIVITY.remove(uuid);
        }

        // Bar fully drained: release the debuff's primary lock and push a final sync so the client
        // sees the empty bar and the unlocked primary.
        if (clamped <= 0.0f) {
            player.removeEffect(ModMobEffects.OVERHEATED);
            OVERHEAT_LOCKED.remove(uuid);
            OVERHEATED.remove(uuid);
            if (previous > 0.0f) {
                PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
                data.setOverheatBar(0);
                forceSync = true;
            }
        }

        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        data.setOverheatBar((int) clamped);

        boolean overheated = clamped >= MAX_OVERHEAT;
        if (overheated && !wasOverheated) {
            OVERHEATED.put(uuid, true);
            // Max heat automatically stops the held beam, then applies the debuff.
            TerragolemReadyUpManager.forceReleaseHold(player);
            applyOverheatDebuff(player);
            forceSync = true;
        } else if (!overheated && wasOverheated) {
            OVERHEATED.remove(uuid);
        }
        return forceSync;
    }

    // Fired once when heat reaches the cap: big flash + nausea (like the ready-up timeout), 4
    // hearts of backlash damage, and the primary locks until the bar hits 0.
    private static void applyOverheatDebuff(Player player) {
        TerragolemAbilityFuelManager.consume(player, TerragolemAbilityFuelManager.OVERCHARGE_HUNGER_COST);
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new TerragolemOverheatDebuffPayload());
        }
        // 10s of extreme nausea, the 8s big stun, and 8s of darkness — all without visible
        // particles or icons.
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 3, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0, false, false, false));
        TerragolemReadyUpManager.applyBigStun(player, 160, -0.98);
        // Overheat backlash: 4 hearts of damage.
        player.hurt(player.damageSources().generic(), 8.0f);
        if (!player.isAlive()) {
            clearHeat(player);
            return;
        }
        OVERHEAT_LOCKED.put(player.getUUID(), true);
        ensureOverheatedEffect(player);
        OVERHEAT_MSG_END.put(player.getUUID(), player.level().getGameTime() + 80);
        OVERHEAT_MESSAGE_VISIBLE.remove(player.getUUID());
    }

    private static void ensureOverheatedEffect(Player player) {
        if (!player.hasEffect(ModMobEffects.OVERHEATED)) {
            // The heat bar owns expiry; a fixed duration would diverge when cooling fuel is used.
            player.addEffect(new MobEffectInstance(ModMobEffects.OVERHEATED, -1, 0, false, false, true));
        }
    }

    private static void sync(Player player) {
        if (player instanceof ServerPlayer sp) {
            PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
            PacketDistributor.sendToPlayer(sp, new TerragolemOverheatPayload(
                    data.getOverheatBar(), data.getMaxOverheatBar(), isPrimaryLocked(player.getUUID())));
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                    new net.sonflowers.jeecore.gene.terragolem.network.TerragolemMalfunctionPayload(
                            sp.getUUID(), isPrimaryLocked(sp.getUUID())
                            ? data.getOverheatBar() / (float) data.getMaxOverheatBar() : 0.0f, 0));
        }
    }
}
