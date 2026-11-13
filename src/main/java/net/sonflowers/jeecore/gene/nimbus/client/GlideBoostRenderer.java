package net.sonflowers.jeecore.gene.nimbus.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains client glide-boost bursts, trails, and local camera feedback.
 */
public class GlideBoostRenderer {
    private static final List<WindRenderer.ActiveBurst> BURSTS = new ArrayList<>();
    private static final Map<UUID, Double> BOOSTED_PLAYERS = new ConcurrentHashMap<>();
    private static final int LIFETIME_TICKS = 16;
    private static final int BOOST_TRAIL_TICKS = 34;
    private static final float FOV_BOOST_TARGET = 0.12f;
    private static final float FOV_SNAPBACK_TARGET = -0.035f;
    public static float screenShakeIntensity = 0.0f;
    private static float fovOffset = 0.0f;
    private static int snapbackTicks = 0;
    private static boolean wasLocalBoosting = false;
    private static boolean localBoostVisualActive = false;

    public static void spawnBurst(Player player, Vec3 origin, Vec3 boostDirection) {
        Vec3 backward = boostDirection.normalize().scale(-1);
        double gameTime = player.level().getGameTime();

        BOOSTED_PLAYERS.put(player.getUUID(), gameTime + BOOST_TRAIL_TICKS);
        screenShakeIntensity = Math.max(screenShakeIntensity, 0.58f);
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null && mc.player.getUUID().equals(player.getUUID())) {
            WindRenderer.screenShakeIntensity = Math.max(WindRenderer.screenShakeIntensity, 0.45f);
            localBoostVisualActive = true;
            snapbackTicks = 0;
        }
        BURSTS.add(new WindRenderer.ActiveBurst(origin.add(backward.scale(0.35)), backward, 5.5f, 1, (long) gameTime));
        spawnBackwardClouds(player, origin, backward);
    }

    public static boolean isBoostActive(Player player) {
        Double endTime = BOOSTED_PLAYERS.get(player.getUUID());
        return endTime != null && player.level().getGameTime() <= endTime;
    }

    public static boolean isLocalBoostActive() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.player != null && localBoostVisualActive && mc.player.isFallFlying() && !mc.player.isInWater();
    }

    public static void tickLocalState(Player player) {
        boolean active = localBoostVisualActive && player.isFallFlying() && !player.isInWater();
        if (localBoostVisualActive && !active) {
            BOOSTED_PLAYERS.remove(player.getUUID());
            localBoostVisualActive = false;
            GlideTrailRenderer.clearParticles();
        }

        if (wasLocalBoosting && !active) {
            snapbackTicks = 8;
        }
        wasLocalBoosting = active;
        updateFovOffset(active);
    }

    public static float getFovMultiplier() {
        boolean active = isLocalBoostActive();
        if (wasLocalBoosting && !active) {
            snapbackTicks = Math.max(snapbackTicks, 8);
        }
        wasLocalBoosting = active;
        updateFovOffset(active);
        return 1.0f + fovOffset;
    }

    public static boolean hasBursts() {
        return !BURSTS.isEmpty() || !BOOSTED_PLAYERS.isEmpty();
    }

    public static boolean hasBoostedPlayers() {
        return !BOOSTED_PLAYERS.isEmpty();
    }

    public static void render(BufferBuilder buffer, Matrix4f matrix, double gameTime) {
        BOOSTED_PLAYERS.entrySet().removeIf(entry -> gameTime > entry.getValue());
        BURSTS.removeIf(burst -> gameTime - burst.startTime >= LIFETIME_TICKS);
        for (WindRenderer.ActiveBurst burst : BURSTS) {
            WindRenderer.render(burst, buffer, matrix, gameTime);
        }
    }

    private static void spawnBackwardClouds(Player player, Vec3 origin, Vec3 backward) {
        Vec3 right = backward.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 0.001 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(backward).normalize();
        var random = player.level().random;
        for (int i = 0; i < 12; i++) {
            Vec3 spread = right.scale((random.nextDouble() - 0.5) * 0.4)
                    .add(up.scale((random.nextDouble() - 0.5) * 0.4));
            Vec3 point = origin.add(backward.scale(0.45)).add(spread);
            Vec3 velocity = backward.scale(0.75 + random.nextDouble() * 0.4).add(spread.scale(0.45));
            player.level().addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    point.x, point.y, point.z, velocity.x, velocity.y, velocity.z);
        }
    }

    public static void clear() {
        BURSTS.clear();
        BOOSTED_PLAYERS.clear();
        screenShakeIntensity = fovOffset = 0;
        snapbackTicks = 0;
        wasLocalBoosting = localBoostVisualActive = false;
    }

    private static void updateFovOffset(boolean active) {
        float target;
        float speed;
        if (active) {
            target = FOV_BOOST_TARGET;
            speed = 0.42f;
            snapbackTicks = 0;
        } else if (snapbackTicks > 0) {
            target = FOV_SNAPBACK_TARGET;
            speed = 0.5f;
            snapbackTicks--;
        } else {
            target = 0.0f;
            speed = 0.24f;
        }

        fovOffset += (target - fovOffset) * speed;
        if (Math.abs(fovOffset) < 0.001f && !active && snapbackTicks <= 0) {
            fovOffset = 0.0f;
        }
    }
}

