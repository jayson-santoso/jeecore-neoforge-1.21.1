package net.sonflowers.jeecore.gene.infernal.server;

import net.sonflowers.jeecore.core.ability.AbilityHandler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;
import net.sonflowers.jeecore.gene.infernal.network.RageEndPayload;
import net.sonflowers.jeecore.gene.infernal.network.RagePayload;
import net.sonflowers.jeecore.gene.infernal.network.RageStatePayload;
import net.sonflowers.jeecore.gene.infernal.network.RageVisualStatePayload;
import net.sonflowers.jeecore.gene.infernal.server.RageManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs server rage activation, combat gains, out-of-combat decay, and attribute synchronization.
 */
public class InfernalRageManager {
    private static final int RAGE_DURATION_TICKS = 400; // 20 seconds (20 * 20 ticks).
    private static final int OUT_OF_COMBAT_TICKS = 200; // 10 seconds without combat.
    private static final int DECAY_INTERVAL_TICKS = 20; // Decay every second.
    private static final int DECAY_AMOUNT = 2; // Slow decay.
    private static final int RAGE_GAIN_COOLDOWN_TICKS = 10; // 0.5 second cooldown between rage gains.
    private static final int ACTIVE_RAGE_DRAIN_INTERVAL_TICKS = 2;
    private static final float RAGE_FALLOFF_THRESHOLD = 0.2f;
    private static final float ACTIVE_RAGE_DAMAGE_EXTENSION_MULTIPLIER = 0.6f;

    private static final Map<UUID, Long> LAST_COMBAT_TIME = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_RAGE_GAIN_TIME = new ConcurrentHashMap<>();

    private static final ResourceLocation RAGE_ATTACK_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "infernal_rage_attack_speed");
    private static final ResourceLocation RAGE_MOVE_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "infernal_rage_move_speed");
    private static final ResourceLocation RAGE_KNOCKBACK_RES_ID =
            ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "infernal_rage_knockback_res");
    private static final ResourceLocation RAGE_JUMP_BOOST_ID =
            ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "infernal_rage_jump_boost");

    public static void activate(Player player) {
        if (GeneHelper.getPlayerGene(player) != Gene.INFERNAL) return;
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        if (data.getRageBar() < data.getMaxRageBar()) return;
        if (InfernalSmokeManager.isSmokeFormActive(player)) return;

        data.setRageBar(data.getMaxRageBar());
        applyRageAttributes(player, 1.0f);
        InfernalAerialChargeManager.resetPounceCharges(player);

        // Active rage lasts until the rage bar drains to zero.
        RageManager.startRage(player.getUUID(), 0);
        syncRageBar(player, data);
        syncRageVisual(player, 1.0f);
        if (player instanceof ServerPlayer sp) {
            RagePayload payload = new RagePayload(player.getUUID(), 0);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, payload);
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 1.0f);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0, player.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0);
        }

        AbilityHandler.setSecondaryCooldown(player);
    }

    public static void tick(Player player) {
        if (GeneHelper.getPlayerGene(player) != Gene.INFERNAL) return;

        boolean raging = RageManager.isRaging(player.getUUID());

        if (raging) {
            PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
            if (player.tickCount % ACTIVE_RAGE_DRAIN_INTERVAL_TICKS == 0) {
                addRageBar(player, -1);
            }

            float ragePercent = getRageEffectPercent(getActiveRagePercent(player));
            applyRageAttributes(player, ragePercent);
            syncRageVisual(player, ragePercent);

            if (data.getRageBar() <= 0) {
                removeRageAttributes(player);
                RageManager.endRage(player.getUUID());
                InfernalAerialChargeManager.clearPounceCharges(player);

                if (player instanceof ServerPlayer sp) {
                    RageEndPayload endPayload = new RageEndPayload(player.getUUID());
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, endPayload);
                }
            }
        } else {

            removeRageAttributes(player);

            // Decay rage bar when out of combat.
            tickRageDecay(player);
        }
    }

    private static void tickRageDecay(Player player) {
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        if (data.getRageBar() <= 0) return;
        if (data.getRageBar() >= data.getMaxRageBar()) return;

        long currentTime = player.level().getGameTime();
        Long lastCombat = LAST_COMBAT_TIME.get(player.getUUID());

        if (lastCombat != null) {
            long timeSinceCombat = currentTime - lastCombat;

            // Start decaying after 10 seconds out of combat.
            if (timeSinceCombat > OUT_OF_COMBAT_TICKS) {
                if (currentTime % DECAY_INTERVAL_TICKS == 0) {
                    addRageBar(player, -DECAY_AMOUNT);
                }
            }
        }
    }

    public static void onDealDamage(Player player, float amount) {
        if (GeneHelper.getPlayerGene(player) != Gene.INFERNAL) return;
        if (RageManager.isRaging(player.getUUID())) {
            extendActiveRage(player, amount);
            return;
        }

        // Check cooldown to prevent spam clicking.
        long currentTime = player.level().getGameTime();
        Long lastGainTime = LAST_RAGE_GAIN_TIME.get(player.getUUID());
        if (lastGainTime != null && (currentTime - lastGainTime) < RAGE_GAIN_COOLDOWN_TICKS) {
            return; 
        }

        LAST_COMBAT_TIME.put(player.getUUID(), currentTime);
        LAST_RAGE_GAIN_TIME.put(player.getUUID(), currentTime);

        // Rage gain scales with damage dealt.
        int rageGain = Math.round(amount * 1.5f);
        addRageBar(player, Math.max(1, rageGain)); // Minimum 1 rage.
    }

    public static void onTakeDamage(Player player, float amount) {
        if (GeneHelper.getPlayerGene(player) != Gene.INFERNAL) return;
        if (RageManager.isRaging(player.getUUID())) return;

        // Check cooldown to prevent rapid rage gain.
        long currentTime = player.level().getGameTime();
        Long lastGainTime = LAST_RAGE_GAIN_TIME.get(player.getUUID());
        if (lastGainTime != null && (currentTime - lastGainTime) < RAGE_GAIN_COOLDOWN_TICKS) {
            return; 
        }

        LAST_COMBAT_TIME.put(player.getUUID(), currentTime);
        LAST_RAGE_GAIN_TIME.put(player.getUUID(), currentTime);

        // Rage gain scales with damage taken.
        int rageGain = Math.round(amount * 3.0f);
        addRageBar(player, Math.max(1, rageGain)); // Minimum 1 rage.
    }

    public static void onSuccessfulIgnitionCharge(Player player) {
        if (GeneHelper.getPlayerGene(player) != Gene.INFERNAL) return;
        if (RageManager.isRaging(player.getUUID())) return;
        // Award rage for a successful quick-time hit.
        addRageBar(player, 45);
    }

    private static void applyRageHitEffects(Player player) {
        // Handled by GeneEventHandler on hit.
    }

    private static void applyRageAttributes(Player player, float percent) {
        percent = Math.max(0.0f, Math.min(1.0f, percent));

        AttributeInstance atkSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (atkSpeed != null) {
            atkSpeed.removeModifier(RAGE_ATTACK_SPEED_ID);
            if (percent > 0.0f) {
                atkSpeed.addTransientModifier(new AttributeModifier(RAGE_ATTACK_SPEED_ID, 1.2 * percent, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        AttributeInstance moveSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null) {
            moveSpeed.removeModifier(RAGE_MOVE_SPEED_ID);
            if (percent > 0.0f) {
                moveSpeed.addTransientModifier(new AttributeModifier(RAGE_MOVE_SPEED_ID, 0.5 * percent, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        AttributeInstance knockbackRes = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackRes != null) {
            knockbackRes.removeModifier(RAGE_KNOCKBACK_RES_ID);
            if (percent > 0.0f) {
                knockbackRes.addTransientModifier(new AttributeModifier(RAGE_KNOCKBACK_RES_ID, 1.0 * percent, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        AttributeInstance jumpStrength = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jumpStrength != null) {
            jumpStrength.removeModifier(RAGE_JUMP_BOOST_ID);
            if (percent > 0.0f) {
                jumpStrength.addTransientModifier(new AttributeModifier(RAGE_JUMP_BOOST_ID, 0.5 * percent, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    public static void removeRageAttributes(Player player) {
        AttributeInstance atkSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (atkSpeed != null) atkSpeed.removeModifier(RAGE_ATTACK_SPEED_ID);
        AttributeInstance moveSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null) moveSpeed.removeModifier(RAGE_MOVE_SPEED_ID);
        AttributeInstance knockbackRes = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackRes != null) knockbackRes.removeModifier(RAGE_KNOCKBACK_RES_ID);
        AttributeInstance jumpStrength = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jumpStrength != null) jumpStrength.removeModifier(RAGE_JUMP_BOOST_ID);
    }

    public static boolean isRaging(Player player) {
        return RageManager.isRaging(player.getUUID());
    }

    private static void addRageBar(Player player, int amount) {
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        int old = data.getRageBar();
        data.addRageBar(amount);
        int newValue = data.getRageBar();

        if (old != newValue) {
            syncRageBar(player, data);
            if (RageManager.isRaging(player.getUUID())) {
                syncRageVisual(player, getRageEffectPercent(getActiveRagePercent(player)));
            }
        }
    }

    private static void extendActiveRage(Player player, float amount) {
        LAST_COMBAT_TIME.put(player.getUUID(), player.level().getGameTime());
        int rageGain = Math.round(amount * ACTIVE_RAGE_DAMAGE_EXTENSION_MULTIPLIER);
        addRageBar(player, Math.max(1, rageGain));
    }

    private static float getActiveRagePercent(Player player) {
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        return data.getMaxRageBar() > 0 ? (float) data.getRageBar() / data.getMaxRageBar() : 0.0f;
    }

    private static float getRageEffectPercent(float percent) {
        percent = Math.max(0.0f, Math.min(1.0f, percent));
        if (percent >= RAGE_FALLOFF_THRESHOLD) {
            float highPercent = (percent - RAGE_FALLOFF_THRESHOLD) / (1.0f - RAGE_FALLOFF_THRESHOLD);
            return RAGE_FALLOFF_THRESHOLD + (1.0f - RAGE_FALLOFF_THRESHOLD) * (float) Math.pow(highPercent, 0.35);
        }

        float lowPercent = percent / RAGE_FALLOFF_THRESHOLD;
        return RAGE_FALLOFF_THRESHOLD * (float) Math.pow(lowPercent, 2.2);
    }

    public static void syncRageBar(Player player, PlayerGeneData data) {
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new RageStatePayload(data.getRageBar(), data.getMaxRageBar(),
                    InfernalAerialChargeManager.getPounceCharges(player)));
        }
    }

    private static void syncRageVisual(Player player, float ragePercent) {
        RageManager.setRagePercent(player.getUUID(), ragePercent);
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new RageVisualStatePayload(player.getUUID(), ragePercent));
        }
    }
}
