package net.sonflowers.jeecore.gene.nimbus.server;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.gene.nimbus.network.CloudPayload;
import net.sonflowers.jeecore.gene.nimbus.network.DashEffectPayload;
import net.sonflowers.jeecore.gene.nimbus.server.CloudManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runs server dash protection and invisibility, then clears synchronized effects after landing or
 * interruption.
 */
public class DashEffectManager {
    private static final Map<UUID, Long> DASH_EFFECTS = new HashMap<>();
    private static final int MIN_DURATION_TICKS = 10; // Minimum 0.5 seconds before dash can end.

    public static void startDashEffect(Player player) {
        DASH_EFFECTS.put(player.getUUID(), player.level().getGameTime());

        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 200, 0, false, false));

        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new DashEffectPayload(true));
        }
    }

    public static boolean hasDashEffect(UUID uuid) {
        return DASH_EFFECTS.containsKey(uuid);
    }

    public static boolean isDashing(UUID uuid) {
        return DASH_EFFECTS.containsKey(uuid);
    }

    public static void tick(Player player) {
        UUID uuid = player.getUUID();
        if (!DASH_EFFECTS.containsKey(uuid)) return;

        long startTime = DASH_EFFECTS.get(uuid);
        long current = player.level().getGameTime();
        long elapsed = current - startTime;

        // Only allow ending after minimum duration threshold.
        if (elapsed < MIN_DURATION_TICKS) return;

        // Check if should end (on ground, in water, or elytra flying).
        if (player.onGround() || player.isInWater() || player.isFallFlying()) {
            endDashEffect(player);
        }
    }

    private static void spawnDashParticles(ServerLevel level, Player player) {
        // Try to use species:poof particle if available, otherwise use cloud.
        ParticleOptions particle;
        try {
            ResourceLocation speciesPoofId = ResourceLocation.parse("species:poof");
            if (BuiltInRegistries.PARTICLE_TYPE.containsKey(speciesPoofId)) {
                particle = (ParticleOptions) BuiltInRegistries.PARTICLE_TYPE.get(speciesPoofId);
            } else {
                particle = ParticleTypes.CLOUD;
            }
        } catch (Exception e) {
            particle = ParticleTypes.CLOUD;
        }

        // Spawn particle at player position (slightly above feet).
        Vec3 pos = player.position().add(0, 0.1, 0);
        level.sendParticles(particle, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
    }

    private static void endDashEffect(Player player) {
        DASH_EFFECTS.remove(player.getUUID());

        // Spawn landing particle effect.
        if (player.level() instanceof ServerLevel serverLevel) {
            spawnDashParticles(serverLevel, player);
        }

        player.removeEffect(MobEffects.INVISIBILITY);

        CloudManager.setClouded(player.getUUID(), false);
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayersTrackingEntity(sp, new CloudPayload(player.getUUID(), false));
            PacketDistributor.sendToPlayer(sp, new CloudPayload(player.getUUID(), false));
        }

        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new DashEffectPayload(false));
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), 
            SoundEvents.PUFFER_FISH_BLOW_OUT, SoundSource.PLAYERS, 1.0f, 0.6f);
    }
}
