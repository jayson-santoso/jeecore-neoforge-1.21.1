package net.sonflowers.jeecore.gene.dryad.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.sonflowers.jeecore.JeeCore;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Renders active healing auras and retains client fade-out state for auras that end.
 */
public class HealRenderer {
    private static final float TRANSITION_TICKS = 10.0f;
    private static final ResourceLocation INNER_RING_TEXTURE = ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "textures/effects/dryad_heal_ring_0.png");
    private static final ResourceLocation OUTER_RING_TEXTURE = ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "textures/effects/dryad_heal_ring_1.png");
    private static final Map<BlockPos, Long> ENDING_AURAS = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<HealSpark> SPARKS = new ArrayList<>();

    public static void startEndAnimations(Map<BlockPos, Long> zones, long gameTime) {
        for (BlockPos pos : zones.keySet()) {
            startEndAnimation(pos, gameTime);
        }
    }

    public static void startEndAnimation(BlockPos pos, long gameTime) {
        ENDING_AURAS.put(pos, gameTime);
    }

    public static void cancelEndAnimation(BlockPos pos) {
        ENDING_AURAS.remove(pos);
    }

    public static boolean hasEndingAuras() {
        return !ENDING_AURAS.isEmpty();
    }

    public static ResourceLocation getInnerRingTexture() {
        return INNER_RING_TEXTURE;
    }

    public static ResourceLocation getOuterRingTexture() {
        return OUTER_RING_TEXTURE;
    }

    public static void renderEndingAuraDecals(BufferBuilder buffer, Matrix4f matrix, Vec3 camera, double gameTime, boolean innerTexture) {
        for (Map.Entry<BlockPos, Long> entry : ENDING_AURAS.entrySet()) {
            float progress = (float) ((gameTime - entry.getValue()) / TRANSITION_TICKS);
            if (progress >= 1.0f) {
                continue;
            }

            Vec3 visualPos = new Vec3(entry.getKey().getX() + 0.5, entry.getKey().getY(), entry.getKey().getZ() + 0.5);
            if (visualPos.closerThan(camera, 130.0)) {
                float fade = 1.0f - progress;
                renderHealAuraDecals(visualPos, gameTime, easeOut(fade), fade, true, buffer, matrix, innerTexture);
            }
        }
    }

    public static void renderHealAuraDecals(Vec3 pos, long spawnTime, BufferBuilder buffer, Matrix4f matrix, double gameTime, boolean innerTexture) {
        float spawnProgress = Math.min((float) ((gameTime - spawnTime) / TRANSITION_TICKS), 1.0f);
        renderHealAuraDecals(pos, gameTime, easeOut(spawnProgress), 1.0f, false, buffer, matrix, innerTexture);
    }

    public static void renderEndingAuras(BufferBuilder buffer, Matrix4f matrix, Vec3 camera, double gameTime) {
        Iterator<Map.Entry<BlockPos, Long>> iterator = ENDING_AURAS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            float progress = (float) ((gameTime - entry.getValue()) / TRANSITION_TICKS);
            if (progress >= 1.0f) {
                iterator.remove();
                continue;
            }

            Vec3 visualPos = new Vec3(entry.getKey().getX() + 0.5, entry.getKey().getY(), entry.getKey().getZ() + 0.5);
            if (visualPos.closerThan(camera, 130.0)) {
                float fade = 1.0f - progress;
                renderHealAuraVisual(visualPos, gameTime, easeOut(fade), fade, true, buffer, matrix, camera);
            }
        }
    }

    public static void renderHealAura(Vec3 pos, long spawnTime, BufferBuilder buffer, Matrix4f matrix, Vec3 camera, double gameTime) {
        float spawnProgress = Math.min((float) ((gameTime - spawnTime) / TRANSITION_TICKS), 1.0f);
        renderHealAuraVisual(pos, gameTime, easeOut(spawnProgress), 1.0f, false, buffer, matrix, camera);
    }

    private static void renderHealAuraVisual(Vec3 pos, double gameTime, float scale, float alphaScale, boolean ending,
                                             BufferBuilder buffer, Matrix4f matrix, Vec3 camera) {
        float baseRadius = 3.85f * scale;
        float yBase = (float) pos.y + 0.08f;
        float exitLift = ending ? (1.0f - alphaScale) * 0.35f : 0.0f;
        float dynamicScale = ending ? (1.0f + (1.0f - alphaScale) * 0.2f) : (1.0f + (float) Math.sin(gameTime * 0.08) * 0.018f);
        float radius = baseRadius * dynamicScale;

        int projectionAlpha = (int) (62 * alphaScale);

        drawLightCylinder(buffer, matrix, pos, yBase + exitLift, radius * 1.08f, 3.3f * alphaScale, projectionAlpha, gameTime);
        if (!ending && scale > 0.95f && RANDOM.nextFloat() < 0.42f) {
            spawnSpark(pos, radius * 1.12f, gameTime);
        }
        renderSparks(buffer, matrix, pos, radius, camera, gameTime);
    }

    private static void renderHealAuraDecals(Vec3 pos, double gameTime, float scale, float alphaScale, boolean ending,
                                             BufferBuilder buffer, Matrix4f matrix, boolean innerTexture) {
        float baseRadius = 3.85f * scale;
        float yBase = (float) pos.y + 0.096f;
        float exitLift = ending ? (1.0f - alphaScale) * 0.35f : 0.0f;
        float dynamicScale = ending ? (1.0f + (1.0f - alphaScale) * 0.2f) : 1.0f;
        float pulse = 1.0f + (float) Math.sin(gameTime * 0.18) * 0.055f;
        float slowMainPulse = 1.0f + (float) Math.sin(gameTime * 0.075) * 0.04f;
        float mainWobble = (float) Math.sin(gameTime * 0.04) * 0.012f;
        float radius = baseRadius * dynamicScale;

        if (innerTexture) {
            drawDecalLayer(buffer, matrix, pos, yBase + exitLift, radius * 2.18f * pulse, 255, 244, 112, (int) (54 * alphaScale));
            drawDecalLayer(buffer, matrix, pos, yBase + 0.018f + mainWobble + exitLift, radius * 1.82f * slowMainPulse, 255, 252, 210, (int) (185 * alphaScale));
        } else {
            drawDecalLayer(buffer, matrix, pos, yBase + 0.014f + exitLift, radius * 2.82f * pulse, 255, 232, 72, (int) (48 * alphaScale));
            drawDecalLayer(buffer, matrix, pos, yBase + 0.032f + mainWobble + exitLift, radius * 2.42f * slowMainPulse, 255, 248, 178, (int) (150 * alphaScale));
        }
    }

    private static void drawDecalLayer(BufferBuilder buffer, Matrix4f matrix, Vec3 pos, float y, float diameter,
                                       int r, int g, int b, int alpha) {
        if (diameter <= 0.05f || alpha <= 0) return;
        float half = diameter * 0.5f;
        drawTexturedGroundQuad(buffer, matrix, pos, y, half, r, g, b, alpha);
    }

    private static void drawTexturedGroundQuad(BufferBuilder buffer, Matrix4f matrix, Vec3 pos, float y, float halfSize,
                                               int r, int g, int b, int alpha) {
        buffer.addVertex(matrix, (float) pos.x - halfSize, y, (float) pos.z - halfSize).setUv(0.0f, 0.0f).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, (float) pos.x - halfSize, y, (float) pos.z + halfSize).setUv(0.0f, 1.0f).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, (float) pos.x + halfSize, y, (float) pos.z + halfSize).setUv(1.0f, 1.0f).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, (float) pos.x + halfSize, y, (float) pos.z - halfSize).setUv(1.0f, 0.0f).setColor(r, g, b, alpha);
    }

    private static void drawLightCylinder(BufferBuilder buffer, Matrix4f matrix, Vec3 pos, float y,
                                          float radius, float height, int alpha, double gameTime) {
        if (radius <= 0.05f || height <= 0.05f) return;
        int segments = 64;
        float pulse = 1.0f + (float) Math.sin(gameTime * 0.16) * 0.025f;
        float r = radius * pulse;
        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (i * 2.0 * Math.PI / segments);
            float angle2 = (float) ((i + 1) * 2.0 * Math.PI / segments);
            float x1 = (float) Math.cos(angle1) * r;
            float z1 = (float) Math.sin(angle1) * r;
            float x2 = (float) Math.cos(angle2) * r;
            float z2 = (float) Math.sin(angle2) * r;

            buffer.addVertex(matrix, (float) pos.x + x1, y, (float) pos.z + z1).setColor(255, 247, 170, alpha);
            buffer.addVertex(matrix, (float) pos.x + x2, y, (float) pos.z + z2).setColor(255, 247, 170, alpha);
            buffer.addVertex(matrix, (float) pos.x + x2, y + height, (float) pos.z + z2).setColor(255, 247, 170, 0);
            buffer.addVertex(matrix, (float) pos.x + x1, y + height, (float) pos.z + z1).setColor(255, 247, 170, 0);
        }
    }

    private static void spawnSpark(Vec3 center, float radius, double gameTime) {
        double angle = RANDOM.nextDouble() * Math.PI * 2.0;
        double distance = Math.sqrt(RANDOM.nextDouble()) * radius;
        Vec3 pos = new Vec3(
                center.x + Math.cos(angle) * distance,
                center.y + 0.13 + RANDOM.nextDouble() * 0.18,
                center.z + Math.sin(angle) * distance);
        Vec3 velocity = new Vec3(
                (RANDOM.nextDouble() - 0.5) * 0.032,
                0.016 + RANDOM.nextDouble() * 0.016,
                (RANDOM.nextDouble() - 0.5) * 0.032);
        SPARKS.add(new HealSpark(pos, velocity, gameTime, 24 + RANDOM.nextInt(18), 0.025f + RANDOM.nextFloat() * 0.018f));
    }

    private static void renderSparks(BufferBuilder buffer, Matrix4f matrix, Vec3 center, float radius, Vec3 camera, double gameTime) {
        Iterator<HealSpark> iterator = SPARKS.iterator();
        while (iterator.hasNext()) {
            HealSpark spark = iterator.next();
            if (gameTime - spark.startTime > spark.maxAge || spark.pos.distanceToSqr(center) > (radius + 2.25f) * (radius + 2.25f)) {
                iterator.remove();
                continue;
            }

            spark.pos = spark.pos.add(spark.velocity);
            spark.velocity = spark.velocity.scale(0.965).add(0.0, 0.0012, 0.0);
            float life = 1.0f - (float) ((gameTime - spark.startTime) / spark.maxAge);
            drawSpark(buffer, matrix, spark.pos, camera, spark.size * (0.8f + life * 0.6f), (int) (190 * life));
        }
    }

    static void drawSpark(VertexConsumer buffer, Matrix4f matrix, Vec3 pos, Vec3 camera, float size, int alpha) {
        float halo = size * 2.0f;
        int haloAlpha = Math.max(0, alpha / 3);
        Vec3 toCamera = camera.subtract(pos).normalize();
        Vec3 right = toCamera.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() < 0.001) {
            right = new Vec3(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(toCamera).normalize();

        drawSparkSquare(buffer, matrix, pos, right, up, halo, 255, 222, 64, haloAlpha);
        drawSparkSquare(buffer, matrix, pos, right, up, size, 255, 255, 232, alpha);
    }

    private static void drawSparkSquare(VertexConsumer buffer, Matrix4f matrix, Vec3 pos, Vec3 right, Vec3 up, float size,
                                        int r, int g, int b, int alpha) {
        Vec3 rightOffset = right.scale(size);
        Vec3 upOffset = up.scale(size);
        Vec3 bottomLeft = pos.subtract(rightOffset).subtract(upOffset);
        Vec3 bottomRight = pos.add(rightOffset).subtract(upOffset);
        Vec3 topRight = pos.add(rightOffset).add(upOffset);
        Vec3 topLeft = pos.subtract(rightOffset).add(upOffset);
        buffer.addVertex(matrix, (float) bottomLeft.x, (float) bottomLeft.y, (float) bottomLeft.z).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, (float) bottomRight.x, (float) bottomRight.y, (float) bottomRight.z).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, (float) topRight.x, (float) topRight.y, (float) topRight.z).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, (float) topLeft.x, (float) topLeft.y, (float) topLeft.z).setColor(r, g, b, alpha);
    }

    private static float easeOut(float t) {
        return (float) Math.sin(Math.max(0.0f, Math.min(1.0f, t)) * Math.PI / 2.0);
    }

    private static class HealSpark {
        Vec3 pos;
        Vec3 velocity;
        final double startTime;
        final int maxAge;
        final float size;

        HealSpark(Vec3 pos, Vec3 velocity, double startTime, int maxAge, float size) {
            this.pos = pos;
            this.velocity = velocity;
            this.startTime = startTime;
            this.maxAge = maxAge;
            this.size = size;
        }
    }
}
