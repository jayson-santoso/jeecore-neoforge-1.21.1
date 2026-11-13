package net.sonflowers.jeecore.gene.infernal.server;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.gene.infernal.network.AerialChargeImpactPayload;
import net.sonflowers.jeecore.gene.infernal.network.InfernalAerialChargePayload;
import net.sonflowers.jeecore.gene.infernal.network.MoltenGroundVfxPayload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs server pounce launches, dives, impacts, and per-player charge accounting.
 */
public class InfernalAerialChargeManager {
    private static final double POUNCE_SPEED = 1.08;
    private static final double POUNCE_UPWARD_SPEED = 0.88;
    private static final double POUNCE_STEER_RESPONSE = 0.11;
    private static final double MIN_DOWNWARD_AIM_SPEED_SCALE = 0.38;
    private static final int MAX_POUNCE_TICKS = 34;
    private static final double IMPACT_RADIUS = 7.0;
    private static final float IMPACT_DAMAGE = 15.0f;
    private static final int MAX_RAGE_POUNCE_CHARGES = 3;
    private static final Map<UUID, PounceState> POUNCES = new HashMap<>();
    private static final Map<UUID, Integer> RAGE_POUNCE_CHARGES = new HashMap<>();

    public static boolean activateOrDive(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        if (GeneHelper.getPlayerGene(player) != Gene.INFERNAL || !RageManager.isRaging(player.getUUID())) return false;
        if (POUNCES.containsKey(player.getUUID())) return true;
        if (RAGE_POUNCE_CHARGES.getOrDefault(player.getUUID(), 0) <= 0) return true;

        startPounce(serverPlayer);
        return true;
    }

    public static void tick(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        PounceState state = POUNCES.get(player.getUUID());
        if (state == null) return;

        if (!RageManager.isRaging(player.getUUID()) || GeneHelper.getPlayerGene(player) != Gene.INFERNAL || !player.isAlive()) {
            cancel(serverPlayer);
            return;
        }

        state.ticks++;
        tickPounceSounds(serverPlayer, state.ticks);
        player.fallDistance = 0.0f;
        player.hurtMarked = true;

        Vec3 currentVelocity = player.getDeltaMovement();
        Vec3 targetForward = getHorizontalLookDirection(serverPlayer);
        state.forward = state.forward.scale(1.0 - POUNCE_STEER_RESPONSE).add(targetForward.scale(POUNCE_STEER_RESPONSE));
        if (state.forward.lengthSqr() < 0.001) {
            state.forward = targetForward;
        } else {
            state.forward = state.forward.normalize();
        }

        double speedScale = getPitchSpeedScale(serverPlayer);
        player.setDeltaMovement(state.forward.scale(POUNCE_SPEED * speedScale).add(0.0, currentVelocity.y, 0.0));
        player.hasImpulse = true;

        if (state.ticks > 4 && (player.onGround() || player.verticalCollisionBelow || player.horizontalCollision)) {
            impact(serverPlayer);
        } else if (state.ticks >= MAX_POUNCE_TICKS) {
            impact(serverPlayer);
        }
    }

    private static void startPounce(ServerPlayer player) {
        RAGE_POUNCE_CHARGES.put(player.getUUID(), Math.max(0, RAGE_POUNCE_CHARGES.getOrDefault(player.getUUID(), 0) - 1));
        InfernalRageManager.syncRageBar(player, player.getData(GeneDataAttachment.PLAYER_GENE_DATA));

        Vec3 forward = getHorizontalLookDirection(player);
        double speedScale = getPitchSpeedScale(player);

        POUNCES.put(player.getUUID(), new PounceState(forward, player.isInvisible()));
        player.setInvisible(true);
        player.setNoGravity(false);
        player.setOnGround(false);
        player.setDeltaMovement(forward.scale(POUNCE_SPEED * speedScale).add(0.0, POUNCE_UPWARD_SPEED, 0.0));
        player.fallDistance = 0.0f;
        player.hurtMarked = true;
        player.hasImpulse = true;
        syncPounce(player, true);
        // Lead with the rush of air; the roar follows after the launch transient.
        playPounceSound(player.serverLevel(), player.position(), SoundEvents.BREEZE_SHOOT, 1.6f, 0.1f);
        playPounceSound(player.serverLevel(), player.position(), SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.25f, 0.1f);
        playPounceSound(player.serverLevel(), player.position(), SoundEvents.FIRECHARGE_USE, 0.8f, 0.65f);
    }

    private static void tickPounceSounds(ServerPlayer player, int ticks) {
        if (ticks == 2) {
            playPounceSound(player.serverLevel(), player.position(), SoundEvents.WITHER_AMBIENT, 0.65f, 0.5f);
        } else if (ticks == 4) {
            playPounceSound(player.serverLevel(), player.position(), SoundEvents.BLAZE_SHOOT, 0.55f, 0.65f);
        }
    }

    private static void playImpactSounds(ServerLevel level, Vec3 pos) {
        // Keep the heavy strike dominant, with quieter rubble and fire underneath.
        playPounceSound(level, pos, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.2f, 0.5f);
        playPounceSound(level, pos, SoundEvents.WITHER_BREAK_BLOCK, 1.15f, 0.55f);
        playPounceSound(level, pos, SoundEvents.DRAGON_FIREBALL_EXPLODE, 0.85f, 0.65f);
        playPounceSound(level, pos, SoundEvents.FIRE_AMBIENT, 0.65f, 0.75f);
    }

    private static void playPounceSound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static double getPitchSpeedScale(ServerPlayer player) {
        float pitch = Math.max(0.0f, player.getXRot());
        double downwardAim = Math.min(1.0, pitch / 75.0);
        double easedAim = downwardAim * downwardAim;
        return 1.0 - (1.0 - MIN_DOWNWARD_AIM_SPEED_SCALE) * easedAim;
    }

    private static Vec3 getHorizontalLookDirection(ServerPlayer player) {
        Vec3 forward = player.getViewVector(1.0f);
        forward = new Vec3(forward.x, 0.0, forward.z);
        if (forward.lengthSqr() < 0.001) {
            forward = Vec3.directionFromRotation(0.0f, player.getYRot());
            forward = new Vec3(forward.x, 0.0, forward.z);
        }
        return forward.normalize();
    }

    private static void impact(ServerPlayer player) {
        Vec3 pos = player.position();
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(false);
        player.fallDistance = 0.0f;
        player.hurtMarked = true;
        player.hasImpulse = true;

        if (player.level() instanceof ServerLevel level) {
            playImpactSounds(level, pos);
            spawnMagmaDustPillar(level, pos);
            spawnMoltenGroundVfx(level, pos);
            spawnScreenShake(level, player, pos);
            applyImpactBlast(level, player, pos);
        }

        cancel(player);
    }

    private static void applyImpactBlast(ServerLevel level, ServerPlayer player, Vec3 pos) {
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(IMPACT_RADIUS),
                entity -> entity != player && entity.isAlive());

        for (LivingEntity target : targets) {
            double distance = target.position().distanceTo(pos);
            if (distance > IMPACT_RADIUS) continue;

            double closeness = 1.0 - (distance / IMPACT_RADIUS);
            double easedCloseness = closeness * closeness;
            float damage = (float) (5.0 + IMPACT_DAMAGE * easedCloseness);
            target.hurt(player.damageSources().explosion(player, player), damage);
            target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 100));

            Vec3 away = target.position().subtract(pos);
            away = new Vec3(away.x, 0.0, away.z);
            if (away.lengthSqr() < 0.001) {
                away = player.getLookAngle();
                away = new Vec3(away.x, 0.0, away.z);
            }
            away = away.normalize();

            double horizontalPush = 0.25 + 0.85 * easedCloseness;
            double verticalPush = 0.32 + 0.85 * easedCloseness;
            target.push(away.x * horizontalPush, verticalPush, away.z * horizontalPush);
            target.hurtMarked = true;
        }
    }

    private static void spawnMagmaDustPillar(ServerLevel level, Vec3 pos) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, Blocks.MAGMA_BLOCK.defaultBlockState()),
                pos.x, pos.y, pos.z, 32, 2.0, 0.0, 2.0, 1.0);
    }

    private static void spawnMoltenGroundVfx(ServerLevel level, Vec3 pos) {
        for (ServerPlayer nearbyPlayer : level.getPlayers(player -> player.position().distanceTo(pos) < 100.0)) {
            PacketDistributor.sendToPlayer(nearbyPlayer, new MoltenGroundVfxPayload(pos.x, pos.y, pos.z));
        }
    }

    private static void spawnScreenShake(ServerLevel level, ServerPlayer source, Vec3 pos) {
        for (ServerPlayer nearbyPlayer : level.getPlayers(player -> player.position().distanceTo(pos) < 20.0)) {
            PacketDistributor.sendToPlayer(nearbyPlayer, new AerialChargeImpactPayload(source.getUUID(), pos.x, pos.y, pos.z));
        }
    }

    private static void cancel(ServerPlayer player) {
        clearPounce(player);
    }

    private static void syncPounce(ServerPlayer player, boolean active) {
        InfernalAerialChargePayload payload = new InfernalAerialChargePayload(player.getUUID(), active);
        PacketDistributor.sendToPlayersTrackingEntity(player, payload);
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void resetPounceCharges(Player player) {
        RAGE_POUNCE_CHARGES.put(player.getUUID(), MAX_RAGE_POUNCE_CHARGES);
    }

    public static int getPounceCharges(Player player) {
        return RAGE_POUNCE_CHARGES.getOrDefault(player.getUUID(), 0);
    }

    public static void clearPounceCharges(Player player) {
        RAGE_POUNCE_CHARGES.remove(player.getUUID());
        clearPounce(player);
    }

    private static void clearPounce(Player player) {
        PounceState state = POUNCES.remove(player.getUUID());
        if (state != null) {
            player.setInvisible(state.wasInvisible);
        }
        player.setNoGravity(false);
        player.fallDistance = 0.0f;
        player.hurtMarked = true;
        player.hasImpulse = true;
        if (player instanceof ServerPlayer serverPlayer) {
            syncPounce(serverPlayer, false);
        }
    }

    private static class PounceState {
        Vec3 forward;
        final boolean wasInvisible;
        int ticks;

        PounceState(Vec3 forward, boolean wasInvisible) {
            this.forward = forward;
            this.wasInvisible = wasInvisible;
        }
    }
}
