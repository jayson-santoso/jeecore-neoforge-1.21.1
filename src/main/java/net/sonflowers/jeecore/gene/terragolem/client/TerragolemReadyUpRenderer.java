package net.sonflowers.jeecore.gene.terragolem.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mirrors ready-up and held-flare visuals on clients; local stun state drives camera and flash
 * feedback.
 */
public class TerragolemReadyUpRenderer {
    public static final int CHARGE_TICKS = 60;

    // Player UUID to client level game time at which the ready-up started (from state payload).
    private static final Map<UUID, Long> READY_UP_STARTS = new ConcurrentHashMap<>();
    // Player UUID to frozen charge of a flare held after right-button press, rendered until
    // right-button release.
    private static final Map<UUID, Float> HELD_CHARGES = new ConcurrentHashMap<>();
    // Player UUID to client level game time when the hold started (drives beam color ramp).
    private static final Map<UUID, Long> HELD_STARTS = new ConcurrentHashMap<>();
    private static float fovOffset;

    public static float whiteFlashIntensity = 0;
    // Longer, slower white flash for the timeout big stun.
    public static float bigFlashIntensity = 0;
    // Extra-long white flash for the overheat debuff.
    public static float overheatFlashIntensity = 0;

    // Local-only post-release state: keeps the FOV low for the stun duration.
    private static long aftermathEndTime = -1;
    private static float aftermathFovCharge = 0;

    // Local-only timeout big stun: deeper FOV drop for 5 seconds.
    private static long bigStunEndTime = -1;

    public static void reset(UUID playerUUID) {
        READY_UP_STARTS.remove(playerUUID);
        HELD_CHARGES.remove(playerUUID);
        HELD_STARTS.remove(playerUUID);
        net.sonflowers.jeecore.core.server.BeamManager.setBeamingClient(playerUUID, false);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getUUID().equals(playerUUID)) resetLocalEffects();
    }

    private static void resetLocalEffects() {
        fovOffset = 0;
        whiteFlashIntensity = 0;
        bigFlashIntensity = 0;
        overheatFlashIntensity = 0;
        aftermathEndTime = -1;
        aftermathFovCharge = 0;
        bigStunEndTime = -1;
    }

    public static void resetAll() {
        for (UUID id : HELD_CHARGES.keySet()) {
            net.sonflowers.jeecore.core.server.BeamManager.setBeamingClient(id, false);
        }
        READY_UP_STARTS.clear();
        HELD_CHARGES.clear();
        HELD_STARTS.clear();
        resetLocalEffects();
    }

    public static void startAftermathTicks(int ticks, float chargeAtRelease) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        aftermathFovCharge = chargeAtRelease;
        aftermathEndTime = mc.level.getGameTime() + ticks;
    }

    // Called from TerragolemBigStunPayload when the ready-up times out.
    public static void triggerBigStun() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        bigFlashIntensity = 1.3f;
        bigStunEndTime = mc.level.getGameTime() + 100;
    }

    // Called from TerragolemOverheatDebuffPayload when the overheat debuff triggers.
    public static void triggerOverheatDebuff() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        overheatFlashIntensity = 1.6f;
        bigFlashIntensity = 1.3f;
        bigStunEndTime = mc.level.getGameTime() + 100;
    }

    public static void setReadyUp(UUID playerUUID, boolean active) {
        Minecraft mc = Minecraft.getInstance();
        if (active) {
            long now = mc.level != null ? mc.level.getGameTime() : 0;
            READY_UP_STARTS.put(playerUUID, now);
        } else {
            // White screen flash for the local player when the ready-up releases.
            if (READY_UP_STARTS.remove(playerUUID) != null
                    && mc.player != null && mc.player.getUUID().equals(playerUUID)) {
                whiteFlashIntensity = 1.0f;
            }
        }
    }

    public static void setHeld(UUID playerUUID, boolean held, float charge) {
        if (held) {
            HELD_CHARGES.put(playerUUID, charge);
            Minecraft mc = Minecraft.getInstance();
            HELD_STARTS.put(playerUUID, mc.level != null ? mc.level.getGameTime() : 0);
        } else {
            HELD_CHARGES.remove(playerUUID);
            HELD_STARTS.remove(playerUUID);
        }
    }

    public static boolean isReadyUp(UUID playerUUID) {
        return READY_UP_STARTS.containsKey(playerUUID);
    }

    public static boolean isHeld(UUID playerUUID) {
        return HELD_CHARGES.containsKey(playerUUID);
    }

    /**
     * Returns elapsed hold time in game ticks, or -1 when the hold or client level is absent.
     */
    public static float getHoldElapsed(UUID playerUUID) {
        Long start = HELD_STARTS.get(playerUUID);
        Minecraft mc = Minecraft.getInstance();
        if (start == null || mc.level == null) return -1;
        return mc.level.getGameTime() - start;
    }

    /**
     * Returns the held flare RGB color, or -1 when no hold exists; color reaches its limit after
     * 120 ticks.
     */
    public static int getHoldBeamColor(UUID playerUUID) {
        float elapsed = getHoldElapsed(playerUUID);
        if (elapsed < 0) return -1;
        float t = Math.min(1.0f, elapsed / 120.0f);
        int g;
        int b;
        if (t < 0.5f) {
            float k = t * 2.0f;
            g = (int) (235 + (150 - 235) * k);
            b = (int) (70 + (30 - 70) * k);
        } else {
            float k = (t - 0.5f) * 2.0f;
            g = (int) (150 + (60 - 150) * k);
            b = (int) (30 + (10 - 30) * k);
        }
        return 0xFF0000 | (g << 8) | b;
    }

    /**
     * Returns beam range in blocks from frozen charge; an absent hold defaults to 30 blocks.
     */
    public static double getHoldBeamRange(UUID playerUUID) {
        Float charge = HELD_CHARGES.get(playerUUID);
        if (charge == null) return 30.0;
        int segments = Math.min(6, (int) (charge * 6));
        return switch (segments) {
            case 4 -> 30.0;
            case 5 -> 40.0;
            case 6 -> 50.0;
            default -> 20.0;
        };
    }

    // True while the post-release stun (after LEFT or right-button release) keeps the FOV low.
    public static boolean isAftermathActive() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && aftermathEndTime >= 0 && mc.level.getGameTime() < aftermathEndTime;
    }

    public static boolean isFlareVisible(UUID playerUUID) {
        return isReadyUp(playerUUID) || isHeld(playerUUID);
    }

    public static boolean hasVisibleFlares() {
        return !READY_UP_STARTS.isEmpty() || !HELD_CHARGES.isEmpty();
    }

    public static float getElapsed(UUID playerUUID) {
        Long start = READY_UP_STARTS.get(playerUUID);
        Minecraft mc = Minecraft.getInstance();
        if (start == null || mc.level == null) return -1;
        return mc.level.getGameTime() - start;
    }

    public static float getCharge(UUID playerUUID) {
        float elapsed = getElapsed(playerUUID);
        if (elapsed < 0) return 0;
        return Math.min(1.0f, elapsed / CHARGE_TICKS);
    }

    // Charge to display for effects: frozen while held, live while charging.
    public static float getRenderCharge(UUID playerUUID) {
        Float held = HELD_CHARGES.get(playerUUID);
        if (held != null) return held;
        return getCharge(playerUUID);
    }

    // Shared pulse so the head flare and the screen overlay throb in sync.
    public static float getPulse(double gameTime) {
        return (float) (Math.sin(gameTime * 0.35) * 0.5 + 0.5);
    }

    public static float getFovMultiplier() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return 1.0f;
        UUID uuid = mc.player.getUUID();
        boolean localReadyUp = isReadyUp(uuid);
        boolean localHeld = isHeld(uuid);
        boolean aftermath = mc.level != null && aftermathEndTime >= 0 && mc.level.getGameTime() < aftermathEndTime;
        if (!aftermath) {
            aftermathEndTime = -1;
            aftermathFovCharge = 0;
        }
        boolean bigStun = mc.level != null && bigStunEndTime >= 0 && mc.level.getGameTime() < bigStunEndTime;
        if (!bigStun) bigStunEndTime = -1;
        float target;
        if (localReadyUp) {
            float elapsed = getElapsed(uuid);
            float charge = Math.min(1.0f, elapsed / CHARGE_TICKS);
            // Once the low-FOV state is reached, keep closing in slowly for drama.
            float overshoot = Math.max(0.0f, elapsed - CHARGE_TICKS) * 0.0015f;
            target = -(0.15f * charge + overshoot);
        } else if (localHeld) {
            // Hold the low FOV at the frozen charge for the duration of the hold.
            target = -0.15f * HELD_CHARGES.getOrDefault(uuid, 1.0f);
        } else if (bigStun) {
            // Timeout big stun: much deeper FOV drop.
            target = -0.28f;
        } else if (aftermath) {
            target = -0.15f * aftermathFovCharge;
        } else {
            target = 0.0f;
        }
        float speed = (localReadyUp || localHeld || aftermath || bigStun) ? 0.12f : 0.2f;
        fovOffset += (target - fovOffset) * speed;
        if (target == 0.0f && Math.abs(fovOffset) < 0.001f) {
            fovOffset = 0.0f;
        }
        return 1.0f + fovOffset;
    }

    public static void render(Player player, BufferBuilder buffer, Matrix4f matrix, float partialTick, double gameTime) {
        float elapsed = getElapsed(player.getUUID());
        Float heldCharge = HELD_CHARGES.get(player.getUUID());
        if (elapsed < 0 && heldCharge == null) return;
        boolean held = elapsed < 0;
        float charge = held ? heldCharge : Math.min(1.0f, elapsed / CHARGE_TICKS);
        // Held flares keep the frozen base size and color; only the jitter stays live.
        float pulse = held ? 0.5f : getPulse(gameTime);

        // Plane aligned to the player's head (yaw and pitch), floating just in front of the face.
        Vec3 look = player.getViewVector(partialTick).normalize();
        Vec3 center = player.getEyePosition(partialTick).add(look.scale(0.5));
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 0.001) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(look).normalize();

        // Color heats toward deeper red as charge builds.
        int r = 255;
        int g = (int) (90 - 70 * charge);
        int b = (int) (15 - 10 * charge);
        int a = (int) Math.min(255, 30 + 50 * charge + 25 * pulse);
        int coreG = (int) Math.min(255, (120 + 50 * pulse) * (1.0f - 0.45f * charge));
        int coreB = (int) ((30 + 40 * charge) * (1.0f - 0.5f * charge));

        // While held (after right-button press), the flare follows the beam color ramp.
        if (held) {
            int holdColor = getHoldBeamColor(player.getUUID());
            if (holdColor >= 0) {
                g = holdColor >> 8 & 0xFF;
                b = holdColor & 0xFF;
                coreG = Math.min(255, g + 100);
                coreB = Math.min(255, b + 80);
            }
        }

        float size = (0.36f + 0.20f * charge) * (1.0f + 0.10f * pulse);
        drawQuad(buffer, matrix, jittered(center, gameTime, 0.7f, 0.012f), right, up, size, size, r, g, b, a);

        float coreSize = size * (0.45f + 0.15f * pulse);
        drawQuad(buffer, matrix, jittered(center, gameTime, 2.9f, 0.015f), right, up, coreSize, coreSize, 255, coreG, coreB, Math.min(255, a + 60));

        // Horizontal flare streaks: progressively wider and thinner layers smooth out the slope
        // from the base square to the widest part; the thinner/wider (more centered) a layer is,
        // the whiter and more translucent it gets.
        int hLayers = 4;
        for (int i = 0; i < hLayers; i++) {
            float t = (float) i / (hLayers - 1);
            float w = size * (1.5f + 1.7f * t) * (1.0f + jitter(gameTime, i * 3.1f, 0.05f));
            float h = size * (0.46f - 0.32f * t);
            int la = (int) Math.max(14, (a + 60) * (1.0f - 0.35f * t));
            drawQuad(buffer, matrix, jittered(center, gameTime, 5.3f + i * 1.7f, 0.018f),
                    right, up, w, h, 255, whiten(g, t), whiten(b, t), la);
        }

        // Vertical streaks: tall and thin, but shorter than the horizontal ones are wide, keeping
        // the overall star-flare shape wider than it is tall.
        int vLayers = 3;
        for (int i = 0; i < vLayers; i++) {
            float t = (float) i / (vLayers - 1);
            float h = size * (0.9f + 0.6f * t) * (1.0f + jitter(gameTime, 11.7f + i * 2.3f, 0.05f));
            float w = size * (0.38f - 0.24f * t);
            int la = (int) Math.max(14, (a + 50) * (1.0f - 0.35f * t));
            drawQuad(buffer, matrix, jittered(center, gameTime, 17.1f + i * 1.9f, 0.018f),
                    right, up, w, h, 255, whiten(g, t), whiten(b, t), la);
        }
    }

    private static int whiten(int colorChannel, float t) {
        return (int) (colorChannel + (255 - colorChannel) * t * 0.7f);
    }

    // Slight dynamic instability shared by all flare layers; seed decorrelates layers.
    private static float jitter(double gameTime, float seed, float amplitude) {
        return (float) (Math.sin(gameTime * 1.9 + seed * 12.9) * Math.sin(gameTime * 3.7 + seed * 7.1)) * amplitude;
    }

    private static Vec3 jittered(Vec3 center, double gameTime, float seed, float amplitude) {
        return center.add(
                jitter(gameTime, seed, amplitude),
                jitter(gameTime, seed + 4.3f, amplitude * 0.7f),
                jitter(gameTime, seed + 8.6f, amplitude));
    }

    private static void drawQuad(BufferBuilder buffer, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up,
                                 float width, float height, int r, int g, int b, int a) {
        double hx = right.x * width * 0.5;
        double hy = right.y * width * 0.5;
        double hz = right.z * width * 0.5;
        double vx = up.x * height * 0.5;
        double vy = up.y * height * 0.5;
        double vz = up.z * height * 0.5;

        buffer.addVertex(matrix, (float) (center.x - hx - vx), (float) (center.y - hy - vy), (float) (center.z - hz - vz)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) (center.x + hx - vx), (float) (center.y + hy - vy), (float) (center.z + hz - vz)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) (center.x + hx + vx), (float) (center.y + hy + vy), (float) (center.z + hz + vz)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) (center.x - hx + vx), (float) (center.y - hy + vy), (float) (center.z - hz + vz)).setColor(r, g, b, a);
    }
}
