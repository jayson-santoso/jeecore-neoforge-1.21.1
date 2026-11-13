package net.sonflowers.jeecore.gene.terragolem.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Schedules server Terragolem sound cues, movement cadence, and delayed echoes.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public final class TerragolemSoundManager {
    private static final Map<UUID, Stride> STRIDES = new HashMap<>();
    private static final Map<UUID, Echo> ECHOES = new HashMap<>();
    private static class Stride {
        Vec3 previous;
        double distance;
        int ticks;
        Stride(Vec3 position) { previous = position; }
    }
    private record Echo(ServerLevel level, Vec3 position, long start) {}

    public static void play(Player player, SoundEvent sound, float volume, float pitch) {
        if (player.level() instanceof ServerLevel level) playAt(level, player.position(), sound, volume, pitch);
    }

    public static void playAt(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void burst(Player player, int segments) {
        ECHOES.remove(player.getUUID());
        float strength = segments / 6.0f;
        if (segments < 5) {
            play(player, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.7f + strength * 0.3f, 1.65f);
            play(player, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.8f + strength * 0.3f, 1.8f);
            return;
        }
        play(player, SoundEvents.WARDEN_SONIC_BOOM, 0.9f + strength * 0.7f, 1.05f - strength * 0.35f);
        play(player, SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.6f + strength * 0.65f, 0.75f - strength * 0.2f);
        if (player.level() instanceof ServerLevel level) {
            ECHOES.put(player.getUUID(), new Echo(level, player.position(), level.getGameTime()));
        }
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        Echo echo = ECHOES.get(id);
        if (echo != null) {
            long age = echo.level.getGameTime() - echo.start;
            if (player.level() != echo.level || !player.isAlive() || GeneHelper.getPlayerGene(player) != Gene.TERRAGOLEM) {
                ECHOES.remove(id);
            } else if (age == 5 || age == 11 || age == 18) {
                float volume = age == 5 ? 0.55f : age == 11 ? 0.28f : 0.12f;
                playAt(echo.level, echo.position, SoundEvents.WARDEN_SONIC_BOOM, volume, 0.7f);
                if (age == 18) ECHOES.remove(id);
            } else if (age > 18) ECHOES.remove(id);
        }
        if (!player.isAlive() || GeneHelper.getPlayerGene(player) != Gene.TERRAGOLEM
                || !player.isSprinting() || !player.onGround() || player.isPassenger() || player.isInWater()) {
            STRIDES.remove(id);
            return;
        }
        Stride stride = STRIDES.computeIfAbsent(id, ignored -> new Stride(player.position()));
        Vec3 movement = player.position().subtract(stride.previous);
        stride.previous = player.position();
        double travelled = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        if (travelled > 2.0) { stride.distance = 0; return; }
        stride.distance += travelled;
        stride.ticks++;
        // Distance-driven cadence follows speed buffs without playing while stationary.
        if (stride.distance >= 1.85 && stride.ticks >= 4 && travelled > 0.01) {
            stride.distance %= 1.85;
            stride.ticks = 0;
            float variation = player.getRandom().nextFloat() * 0.08f;
            play(player, SoundEvents.IRON_GOLEM_STEP, 0.9f, 0.6f + variation);
            play(player, SoundEvents.DEEPSLATE_STEP, 0.7f, 0.65f + variation);
            play(player, SoundEvents.NETHERITE_BLOCK_STEP, 0.4f, 0.55f + variation);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        STRIDES.remove(event.getEntity().getUUID());
        ECHOES.remove(event.getEntity().getUUID());
    }
}
