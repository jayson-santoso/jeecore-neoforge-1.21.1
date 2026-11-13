package net.sonflowers.jeecore.gene.infernal.server;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;
import net.sonflowers.jeecore.gene.infernal.network.IgnitionChargeStatePayload;
import net.sonflowers.jeecore.gene.infernal.network.IgnitionChargeVisualStatePayload;
import net.sonflowers.jeecore.gene.infernal.network.IgnitionChargeVfxPayload;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Runs server ignition charge timing and quick-time hits; delayed explosions are scoped to level
 * and entity UUID.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public class InfernalChargeManager {
    private static final int MAX_CHARGE = 100;
    private static final int MAX_TIMER = 100;
    private static final int HIT_COOLDOWN_TICKS = 20;
    private static final int QUICK_TIME_CHANCE = 100;
    private static final int QUICK_TIME_ZONE_WIDTH = 18;
    private static final int QUICK_TIME_POINTER_SPEED = 5;
    private static final int LANDING_EXPLOSION_DELAY_TICKS = 6;
    private static final int LANDING_EXPLOSION_TIMEOUT_TICKS = 120;
    // Server-thread state scoped to a level and entity UUID, never a reused numeric ID.
    private static final Map<ServerLevel, Map<UUID, LandingExplosion>> LANDING_EXPLOSIONS = new WeakHashMap<>();

    private record LandingExplosion(long earliestDetonationTick, long timeoutTick) {
    }

    public static void toggle(Player player) {
        if (GeneHelper.getPlayerGene(player) != Gene.INFERNAL) return;
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        boolean newState = !data.isIgnitionChargeEnabled();
        data.setIgnitionChargeEnabled(newState);
        if (!newState && !isCharged(data)) {
            resetCharge(data);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                newState ? SoundEvents.NOTE_BLOCK_BELL.value() : SoundEvents.NOTE_BLOCK_BASS.value(),
                SoundSource.AMBIENT, 1.0f, 2.0f);
        sync(player, data);
    }

    public static void tick(Player player) {
        if (GeneHelper.getPlayerGene(player) != Gene.INFERNAL) return;
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        boolean changed = false;

        if (data.getIgnitionCooldownTicks() > 0) {
            data.setIgnitionCooldownTicks(data.getIgnitionCooldownTicks() - 1);
            changed = true;
        }

        // Cannot charge during rage mode.
        if (InfernalRageManager.isRaging(player)) {
            if (data.getIgnitionCharge() > 0 || data.getIgnitionTimer() > 0 || data.isIgnitionQuickTimeActive()) {
                resetCharge(data);
                syncVisualState(player, data, false);
                changed = true;
            }
            if (changed) sync(player, data);
            return;
        }

        if (!data.isIgnitionChargeEnabled() && !isCharged(data) || InfernalSmokeManager.isSmokeFormActive(player)) {
            if (data.getIgnitionCharge() > 0 || data.getIgnitionTimer() > 0 || data.isIgnitionQuickTimeActive()) {
                resetCharge(data);
                syncVisualState(player, data, false);
                changed = true;
            }
            if (changed) sync(player, data);
            return;
        }

        if (isCharged(data)) {
            data.addIgnitionTimer(1);
            changed = true;
            InfernalPassiveHandler.drainAbilityHunger(player, 0.02f);
            tickArmedEffects(player, data);
            syncVisualState(player, data, true);
            if (data.getIgnitionTimer() >= MAX_TIMER) {
                punishTimeout(player, data);
                changed = false;
            }
        } else if (canCharge(player, data)) {
            data.addIgnitionCharge(isMoving(player) ? 3 : 4);
            InfernalPassiveHandler.drainAbilityHunger(player, 0.015f);
            changed = true;
            if (data.getIgnitionCharge() >= MAX_CHARGE) {
                data.setIgnitionCharge(MAX_CHARGE);
                data.setIgnitionTimer(0);
                maybeStartQuickTime(player, data);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
            } else if (player.level().getGameTime() % 5 == 0) {
                playChargingEffects(player);
            }
        } else if (data.getIgnitionCharge() > 0) {
            data.addIgnitionCharge(-6);
            changed = true;
        }

        if (changed) sync(player, data);
    }

    public static boolean isReady(Player player) {
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        return GeneHelper.getPlayerGene(player) == Gene.INFERNAL && isCharged(data);
    }

    public static void applyChargedHit(Player attacker, LivingEntity target) {
        if (!(attacker.level() instanceof ServerLevel)) return;
        PlayerGeneData data = attacker.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        if (!isCharged(data)) return;
        boolean quickTimeHit = isQuickTimeSuccess(data);

        resetCharge(data);
        data.setIgnitionCooldownTicks(HIT_COOLDOWN_TICKS);
        sync(attacker, data);
        syncVisualState(attacker, data, false);

        // Increase rage bar significantly on successful QTE hit.
        if (quickTimeHit) {
            InfernalRageManager.onSuccessfulIgnitionCharge(attacker);
        }

        target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 80));
        Vec3 knockback = target.position().subtract(attacker.position());
        if (knockback.lengthSqr() < 0.001) {
            knockback = attacker.getLookAngle();
        }
        knockback = knockback.normalize().scale(quickTimeHit ? 6.4 : 1.45).add(0.0, quickTimeHit ? 1.05 : 0.32, 0.0);
        target.setDeltaMovement(target.getDeltaMovement().add(knockback));
        target.hurtMarked = true;

        // Add recoil knockback to attacker on successful QTE hit.
        if (quickTimeHit) {
            Vec3 recoil = knockback.normalize().scale(-0.8).add(0.0, 0.15, 0.0);
            attacker.setDeltaMovement(attacker.getDeltaMovement().add(recoil));
            attacker.hurtMarked = true;

            long gameTime = attacker.level().getGameTime();
            LANDING_EXPLOSIONS.computeIfAbsent((ServerLevel) attacker.level(), level -> new HashMap<>())
                    .put(target.getUUID(), new LandingExplosion(
                    gameTime + LANDING_EXPLOSION_DELAY_TICKS,
                    gameTime + LANDING_EXPLOSION_TIMEOUT_TICKS
            ));
        }

        if (attacker.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + 1.0, target.getZ(),
                    quickTimeHit ? 5 : 1, 0.0, 0.0, 0.0, 0.0);
            serverLevel.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 0.5, target.getZ(),
                    quickTimeHit ? 56 : 20, 0.7, 1.0, 0.7, 0.32);
            serverLevel.sendParticles(ParticleTypes.LAVA, target.getX(), target.getY() + 0.5, target.getZ(),
                    quickTimeHit ? 18 : 0, 0.5, 0.6, 0.5, 0.8);
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, target.getX(), target.getY() + 0.6, target.getZ(),
                    quickTimeHit ? 22 : 0, 0.7, 0.8, 0.7, 0.08);
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 0.5, target.getZ(),
                    quickTimeHit ? 5 : 2, 0.18, 0.18, 0.18, 0.0);
            if (quickTimeHit) {
                IgnitionChargeVfxPayload payload = new IgnitionChargeVfxPayload(
                        target.getId(),
                        target.position().add(0, target.getBbHeight() * 0.5, 0),
                        false,
                        false
                );
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, payload);
                if (attacker instanceof ServerPlayer sp) {
                    PacketDistributor.sendToPlayer(sp, payload);
                }
            }
        }
        if (quickTimeHit) {
            attacker.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 2.6f, 1.85f);
            attacker.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.8f, 1.9f);
        }
        attacker.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, quickTimeHit ? 2.4f : 1.0f, quickTimeHit ? 0.55f : 0.2f);
        attacker.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, quickTimeHit ? 1.6f : 0.6f, quickTimeHit ? 1.15f : 1.8f);
    }

    public static void reset(Player player) {
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        resetCharge(data);
        data.setIgnitionCooldownTicks(0);
        sync(player, data);
    }

    public static void sync(Player player) {
        sync(player, player.getData(GeneDataAttachment.PLAYER_GENE_DATA));
    }

    private static boolean canCharge(Player player, PlayerGeneData data) {
        return data.getIgnitionCooldownTicks() <= 0
                && player.isShiftKeyDown()
                && player.onGround();
    }

    private static boolean isMoving(Player player) {
        Vec3 movement = player.getDeltaMovement();
        return movement.x * movement.x + movement.z * movement.z > 0.003;
    }

    private static boolean isCharged(PlayerGeneData data) {
        return data.getIgnitionCharge() >= MAX_CHARGE;
    }

    private static void resetCharge(PlayerGeneData data) {
        data.setIgnitionCharge(0);
        data.setIgnitionTimer(0);
        data.setIgnitionQuickTimeActive(false);
    }

    private static void maybeStartQuickTime(Player player, PlayerGeneData data) {
        // The quick-time prompt appears on every eligible hit.
        data.setIgnitionQuickTimeActive(true);
        data.setIgnitionQuickTimeZoneWidth(QUICK_TIME_ZONE_WIDTH);
        data.setIgnitionQuickTimeZoneStart(player.getRandom().nextInt(100 - QUICK_TIME_ZONE_WIDTH));

        // Play musical note when QTE appears.
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    public static float getQuickTimePointer(PlayerGeneData data) {
        return (data.getIgnitionTimer() * QUICK_TIME_POINTER_SPEED % 100) / 100.0f;
    }

    public static float getQuickTimePointer(PlayerGeneData data, float partialTick) {
        return ((data.getIgnitionTimer() + partialTick) * QUICK_TIME_POINTER_SPEED % 100) / 100.0f;
    }

    public static float getQuickTimePointer(double elapsedTicks) {
        return (float) ((elapsedTicks * QUICK_TIME_POINTER_SPEED % 100) / 100.0);
    }

    private static boolean isQuickTimeSuccess(PlayerGeneData data) {
        if (!data.isIgnitionQuickTimeActive()) return false;
        int pointer = data.getIgnitionTimer() * QUICK_TIME_POINTER_SPEED % 100;
        int start = data.getIgnitionQuickTimeZoneStart();
        int end = start + data.getIgnitionQuickTimeZoneWidth();
        return pointer >= start && pointer <= end;
    }

    private static void playChargingEffects(Player player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.1, player.getZ(),
                    4, 0.4, 0.0, 0.4, 0.8);
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY() + 0.1, player.getZ(),
                    4, 0.2, 0.0, 0.2, 0.02);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.PLAYERS, 0.65f, 2.0f);
    }

    private static void tickArmedEffects(Player player, PlayerGeneData data) {
        int remaining = Math.max(0, MAX_TIMER - data.getIgnitionTimer());
        int interval = Math.max(2, 10 - data.getIgnitionTimer() / 12);
        if (remaining % interval == 0) {
            float pitch = 1.1f + (data.getIgnitionTimer() / (float) MAX_TIMER) * 1.2f;
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_BUTTON_CLICK, SoundSource.PLAYERS, 1.0f, pitch);
        }

        // Add campfire smoke particles during ticking state.
        if (player.level().getGameTime() % 4 == 0) {
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY() + 0.1, player.getZ(),
                        3, 0.3, 0.0, 0.3, 0.03);
            }
        }
    }

    private static void punishTimeout(Player player, PlayerGeneData data) {
        resetCharge(data);
        syncVisualState(player, data, false);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 1.0, player.getZ(),
                    3, 1.0, 0.5, 1.0, 0.0);
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(),
                    12, 1.0, 0.5, 1.0, 0.08);
            // Apply damage explicitly for this visual explosion.
            player.hurt(player.damageSources().explosion(null, null), 6.0f);
            serverLevel.explode(null, player.getX(), player.getY(), player.getZ(), 1.0f, false, Level.ExplosionInteraction.NONE);
        }
        player.setDeltaMovement(player.getDeltaMovement().add(0.0, 0.3, 0.0));
        player.hurtMarked = true;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.3f);
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new IgnitionChargeVfxPayload(
                    player.getId(),
                    player.position().add(0, player.getBbHeight() * 0.5, 0),
                    true,
                    true
            ));
        }
        sync(player, data);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            tickLandingExplosions(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LANDING_EXPLOSIONS.clear();
    }

    private static void tickLandingExplosions(ServerLevel serverLevel) {
        Map<UUID, LandingExplosion> pending = LANDING_EXPLOSIONS.get(serverLevel);
        if (pending == null || pending.isEmpty()) return;
        long gameTime = serverLevel.getGameTime();
        // Explosions invoke damage callbacks. Remove a due entry before detonating, and iterate a
        // snapshot so callbacks cannot invalidate this traversal.
        for (var entry : List.copyOf(pending.entrySet())) {
            Entity entity = serverLevel.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity target)) {
                pending.remove(entry.getKey(), entry.getValue());
                continue;
            }
            LandingExplosion landingExplosion = entry.getValue();
            if (target.isAlive() && gameTime < landingExplosion.earliestDetonationTick()) {
                continue;
            }
            if (!target.isAlive() || target.onGround() || target.horizontalCollision || target.verticalCollision || target.isInWall()
                    || target.isInWater()
                    || hasHitLivingEntity(serverLevel, target)
                    || gameTime >= landingExplosion.timeoutTick()) {
                if (pending.remove(entry.getKey(), landingExplosion)) {
                    triggerDetonation(serverLevel, target);
                }
            }
        }
        if (pending.isEmpty()) LANDING_EXPLOSIONS.remove(serverLevel);
    }

    private static void triggerDetonation(ServerLevel serverLevel, LivingEntity target) {
        Vec3 pos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        serverLevel.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z,
                32, 0.8, 0.8, 0.8, 0.25);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z,
                4, 0.35, 0.25, 0.35, 0.0);
        IgnitionChargeVfxPayload payload = new IgnitionChargeVfxPayload(
                target.getId(),
                pos,
                true,
                false
        );
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, payload);
        serverLevel.explode(null, pos.x, pos.y, pos.z, 2.8f, true, Level.ExplosionInteraction.TNT);
    }

    private static boolean hasHitLivingEntity(ServerLevel serverLevel, LivingEntity target) {
        return !serverLevel.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(0.18),
                entity -> entity != target && entity.isAlive()).isEmpty();
    }

    private static void sync(Player player, PlayerGeneData data) {
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new IgnitionChargeStatePayload(
                    data.isIgnitionChargeEnabled(),
                    data.getIgnitionCharge(),
                    data.getIgnitionTimer(),
                    data.getIgnitionCooldownTicks(),
                    data.isIgnitionQuickTimeActive(),
                    data.getIgnitionQuickTimeZoneStart(),
                    data.getIgnitionQuickTimeZoneWidth()
            ));
        }
    }

    private static void syncVisualState(Player player, PlayerGeneData data, boolean armed) {
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new IgnitionChargeVisualStatePayload(
                    player.getUUID(),
                    armed,
                    data.getIgnitionTimer(),
                    data.getMaxIgnitionTimer()
            ));
        }
    }
}
