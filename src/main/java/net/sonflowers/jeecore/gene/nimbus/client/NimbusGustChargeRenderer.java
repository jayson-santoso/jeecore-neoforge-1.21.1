package net.sonflowers.jeecore.gene.nimbus.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.gene.nimbus.server.NimbusGustManager;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders client additive charge ribbons in the same effect pass as the released gust.
 */
public final class NimbusGustChargeRenderer {
    private static final Map<Integer, Charge> CHARGES = new HashMap<>();
    private static ClientLevel level;

    private static final class Charge {
        final long started;
        double ended = -1;
        float endProgress;
        Charge(long started) { this.started = started; }
    }

    private NimbusGustChargeRenderer() {}

    public static void setCharging(int entityId, boolean active, long started) {
        prune();
        if (level == null) return;
        if (active) {
            CHARGES.put(entityId, new Charge(started));
        } else {
            Charge charge = CHARGES.get(entityId);
            if (charge != null && charge.ended < 0) {
                charge.endProgress = progress(charge, level.getGameTime());
                charge.ended = level.getGameTime();
            }
        }
    }

    public static void clear() { CHARGES.clear(); level = null; }

    private static void prune() {
        ClientLevel current = Minecraft.getInstance().level;
        if (level != current) { CHARGES.clear(); level = current; }
        if (current == null) return;
        CHARGES.entrySet().removeIf(entry -> {
            var entity = current.getEntity(entry.getKey());
            Charge charge = entry.getValue();
            return !(entity instanceof Player player) || !player.isAlive()
                    || GeneHelper.getPlayerGene(player) != Gene.NIMBUS
                    || (charge.ended >= 0 && current.getGameTime() - charge.ended >= 5)
                    || current.getGameTime() - charge.started > NimbusGustManager.CHARGE_TICKS + 10;
        });
    }

    public static boolean hasEffects() { prune(); return !CHARGES.isEmpty(); }

    private static float progress(Charge charge, double now) {
        return Mth.clamp((float) ((now - charge.started) / NimbusGustManager.CHARGE_TICKS), 0, 1);
    }

    public static void render(BufferBuilder buffer, Matrix4f matrix, Vec3 camera, double now, float partialTick) {
        prune();
        if (level == null) return;
        var mc = Minecraft.getInstance();
        for (var entry : CHARGES.entrySet()) {
            if (!(level.getEntity(entry.getKey()) instanceof Player player)) continue;
            Vec3 origin = player.getPosition(partialTick);
            if (!origin.closerThan(camera, 130)) continue;
            Charge state = entry.getValue();
            float charge = state.ended < 0 ? progress(state, now) : state.endProgress;
            float exit = state.ended < 0 ? 0 : Mth.clamp((float) (now - state.ended) / 5, 0, 1);
            float age = (float) (now - state.started);
            boolean firstPerson = player == mc.player && mc.options.getCameraType().isFirstPerson();
            int bands = 5;
            for (int band = 0; band < bands; band++) {
                float ringAge = age - Math.max(0, band - 2) * 6;
                if (ringAge <= 0) continue;
                float enter = Mth.clamp(ringAge / 6, 0, 1);
                enter = enter * enter * (3 - 2 * enter);
                float fade = enter * (1 - exit);
                float widthVariation = 0.50f + 1.05f * (0.5f + 0.5f * Mth.sin(band * 2.3f + 0.4f));
                float width = (0.025f + charge * 0.045f) * widthVariation
                        * Mth.lerp(enter, 0.15f, 1) * (0.95f + 0.05f * Mth.sin(age * 0.25f + band));
                float rotation = (age * (0.13f + band * 0.023f) + age * age * 0.004f)
                        * (band % 2 == 0 ? 1 : -1) + band * 1.7f;
                float tiltX = (band % 2 == 0 ? 1 : -1) * (0.18f + band * 0.065f)
                        + Mth.sin(age * 0.23f + band * 2) * charge * 0.14f;
                float tiltZ = Mth.sin(band * 2.1f + 0.7f) * 0.35f
                        + Mth.cos(age * 0.19f + band) * charge * 0.12f;
                float size = (0.53f + band * 0.025f) * Mth.lerp(enter, 0.65f, 1) + exit * 0.35f;
                // Flatten only the 3D depth; preserve the square footprint and ribbon width.
                float halfHeight = (0.015625f + band * 0.00390625f) * Mth.lerp(enter, 0.2f, 1);
                Vec3 depth = new Vec3(-Mth.cos(tiltX) * Mth.sin(tiltZ),
                        Mth.cos(tiltX) * Mth.cos(tiltZ), Mth.sin(tiltX)).scale(halfHeight);
                Vec3 center = origin.add(0, 0.25f + band * 0.30f, 0);
                // Four straight sides retain crisp square corners, with independent tilted ring
                // planes.
                for (int segment = 0; segment < 32; segment++) {
                    float t0 = segment / 32f;
                    float t1 = (segment + 1) / 32f;
                    Vec3 outer0 = squarePoint(center, t0, size + width, rotation, tiltX, tiltZ);
                    Vec3 outer1 = squarePoint(center, t1, size + width, rotation, tiltX, tiltZ);
                    Vec3 inner0 = squarePoint(center, t0, size - width, rotation, tiltX, tiltZ);
                    Vec3 inner1 = squarePoint(center, t1, size - width, rotation, tiltX, tiltZ);
                    if (firstPerson && Math.max(Math.max(outer0.y, outer1.y), Math.max(inner0.y, inner1.y)) + Math.abs(depth.y) - origin.y > 1.05) continue;
                    float shimmer = 0.55f + 0.45f * Mth.sin(t0 * Mth.TWO_PI - age * 0.35f + band);
                    int alpha = (int) ((55 + 125 * charge) * fade * shimmer);
                    extrudedBand(buffer, matrix, inner0, inner1, outer0, outer1, depth,
                            (int) (alpha * sideFade(t0)), (int) (alpha * sideFade(t1)));
                }
            }
        }
    }

    private static void extrudedBand(BufferBuilder buffer, Matrix4f matrix, Vec3 inner0, Vec3 inner1,
                                     Vec3 outer0, Vec3 outer1, Vec3 depth, int alpha0, int alpha1) {
        Vec3 it0 = inner0.add(depth), it1 = inner1.add(depth);
        Vec3 ot0 = outer0.add(depth), ot1 = outer1.add(depth);
        Vec3 ib0 = inner0.subtract(depth), ib1 = inner1.subtract(depth);
        Vec3 ob0 = outer0.subtract(depth), ob1 = outer1.subtract(depth);
        quad(buffer, matrix, it0, it1, ot1, ot0, alpha0, alpha1);
        quad(buffer, matrix, ob0, ob1, ib1, ib0, (int) (alpha0 * 0.55f), (int) (alpha1 * 0.55f));
        quad(buffer, matrix, ot0, ot1, ob1, ob0, (int) (alpha0 * 0.75f), (int) (alpha1 * 0.75f));
        quad(buffer, matrix, ib0, ib1, it1, it0, (int) (alpha0 * 0.65f), (int) (alpha1 * 0.65f));
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int alpha0, int alpha1) {
        vertex(buffer, matrix, a, alpha0);
        vertex(buffer, matrix, b, alpha1);
        vertex(buffer, matrix, c, alpha1);
        vertex(buffer, matrix, d, alpha0);
    }

    private static float sideFade(float t) {
        // Fade each side slightly toward both corners, without breaking the square silhouette.
        float side = (t * 4) % 1;
        return 0.72f + 0.28f * Mth.sin(side * Mth.PI);
    }

    private static Vec3 squarePoint(Vec3 center, float t, float size, float yaw, float tiltX, float tiltZ) {
        float perimeter = (t * 4) % 4;
        int side = (int) perimeter;
        float u = perimeter - side;
        float x = switch (side) { case 0 -> -1 + 2 * u; case 1 -> 1; case 2 -> 1 - 2 * u; default -> -1; };
        float z = switch (side) { case 0 -> -1; case 1 -> -1 + 2 * u; case 2 -> 1; default -> 1 - 2 * u; };
        float rx = (x * Mth.cos(yaw) - z * Mth.sin(yaw)) * size;
        float rz = (x * Mth.sin(yaw) + z * Mth.cos(yaw)) * size;
        float y = -rz * Mth.sin(tiltX);
        return center.add(rx * Mth.cos(tiltZ) - y * Mth.sin(tiltZ),
                rx * Mth.sin(tiltZ) + y * Mth.cos(tiltZ), rz * Mth.cos(tiltX));
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 point, int alpha) {
        buffer.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z).setColor(225, 247, 255, alpha);
    }
}
