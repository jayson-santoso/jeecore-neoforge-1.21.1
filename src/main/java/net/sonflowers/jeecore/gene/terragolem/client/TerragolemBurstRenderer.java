package net.sonflowers.jeecore.gene.terragolem.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Renders client burst beams and impact effects; bright geometry uses additive blending and dark
 * smoke uses alpha blending.
 */
public class TerragolemBurstRenderer {
    private static final int BEAM_LIFETIME_TICKS = 12;
    private static final int IMPACT_SPARK_LIFETIME_TICKS = 8;
    private static final int SMOKE_LIFETIME_TICKS = 16;
    private static final int EFFECT_LIFETIME_TICKS = 20;
    private static final int SPARK_COUNT = 9;
    private static final int HEAD_SMOKE_PUFFS = 4;
    private static final int IMPACT_SMOKE_PUFFS = 5;
    private static final double INV_SQRT_TWO = 0.7071067811865476;

    private record Burst(Vec3 start, Vec3 end, Vec3 dir, int segments,
                         boolean impact, double startTime) {}

    private static final List<Burst> BURSTS = new ArrayList<>();

    public static void addBurst(UUID playerUUID, int segments, double endX, double endY, double endZ,
                                boolean impact) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Player player = mc.level.getPlayerByUUID(playerUUID);
        if (player == null) return;

        Vec3 start = player.getEyePosition();
        Vec3 end = new Vec3(endX, endY, endZ);
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.01) return;
        BURSTS.add(new Burst(start, end, delta.scale(1.0 / length), segments,
                impact, mc.level.getGameTime()));
    }

    public static boolean hasBursts() {
        return !BURSTS.isEmpty();
    }

    /**
     * Additive pass: beam flash plus the compact flare/spark impact explosion.
     */
    public static void render(BufferBuilder buffer, Matrix4f matrix, double gameTime) {
        if (BURSTS.isEmpty()) return;
        BURSTS.removeIf(burst -> gameTime - burst.startTime > EFFECT_LIFETIME_TICKS);

        for (Burst burst : BURSTS) {
            double age = gameTime - burst.startTime;
            int color = colorForSegments(burst.segments);
            int r = color >> 16 & 0xFF;
            int g = color >> 8 & 0xFF;
            int b = color & 0xFF;

            if (age < BEAM_LIFETIME_TICKS) {
                renderBeam(buffer, matrix, burst, gameTime,
                        1.0f - (float) (age / BEAM_LIFETIME_TICKS), r, g, b);
            }
            if (burst.impact && age < IMPACT_SPARK_LIFETIME_TICKS) {
                renderImpactExplosion(buffer, matrix, burst.end, age, burst.startTime, r, g, b);
            }
        }
    }

    /**
     * Standard-alpha pass: dense, fast smoke at the muzzle and real impact point.
     */
    public static void renderSmoke(BufferBuilder buffer, Matrix4f matrix, Vec3 camera, double gameTime) {
        if (BURSTS.isEmpty()) return;
        for (Burst burst : BURSTS) {
            double age = gameTime - burst.startTime;
            renderHeadSmoke(buffer, matrix, camera, burst, age);
            if (burst.impact) renderImpactSmoke(buffer, matrix, camera, burst, age);
        }
    }

    private static void renderBeam(BufferBuilder buffer, Matrix4f matrix, Burst burst,
                                   double gameTime, float life, int r, int g, int b) {
        int beamSections = 24;
        for (int i = 0; i < beamSections; i++) {
            double step = (double) i / beamSections;
            double nextStep = (double) (i + 1) / beamSections;
            Vec3 p1 = burst.start.lerp(burst.end, step);
            Vec3 p2 = burst.start.lerp(burst.end, nextStep);
            float wave = (float) Math.sin((step * 16.0) - (gameTime * 0.6));
            float baseWidth = 0.1f * (1.0f + wave * 0.6f) * (0.4f + 0.6f * life);

            drawBeamLayers(buffer, matrix, p1, p2, baseWidth, r, g, b,
                    (int) (30 * life), (int) (60 * life), (int) (90 * life),
                    (int) (150 * life), (int) (255 * life));
        }
    }

    private static void renderImpactExplosion(BufferBuilder buffer, Matrix4f matrix, Vec3 point,
                                              double age, double seed, int r, int g, int b) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 view = point.subtract(camera).normalize();
        Vec3 right = view.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 0.001) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(view).normalize();

        float progress = (float) (age / IMPACT_SPARK_LIFETIME_TICKS);
        float life = 1.0f - progress;
        float flashSize = 0.24f + 0.42f * life;
        drawFacingQuad(buffer, matrix, point, right, up, flashSize * 2.5f, flashSize * 0.22f,
                r, g, b, (int) (145 * life));
        drawFacingQuad(buffer, matrix, point, right, up, flashSize * 0.22f, flashSize * 2.1f,
                r, g, b, (int) (145 * life));
        drawFacingQuad(buffer, matrix, point, right, up, flashSize, flashSize,
                255, Math.min(255, g + 105), Math.min(255, b + 90), (int) (220 * life));

        double phase = seed * 0.731;
        for (int i = 0; i < SPARK_COUNT; i++) {
            double angle = phase + i * (Math.PI * 2.0 / SPARK_COUNT);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vec3 radial = right.scale(cos).add(up.scale(sin));
            float distance = 0.06f + progress * (0.48f + 0.07f * (i % 3));
            Vec3 center = point.add(radial.scale(distance));
            float flicker = 0.78f + 0.22f * (float) Math.sin(age * 3.4 + i * 1.73);
            float coreSize = (0.018f + 0.018f * life) * flicker;
            drawFacingQuad(buffer, matrix, center, right, up, coreSize * 2.8f, coreSize * 2.8f,
                    r, Math.min(255, g + 75), Math.min(255, b + 45), (int) (72 * life));
            drawFacingQuad(buffer, matrix, center, right, up, coreSize, coreSize,
                    255, 255, 225, (int) (250 * life));
        }
    }

    private static void renderHeadSmoke(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
                                        Burst burst, double age) {
        for (int i = 0; i < HEAD_SMOKE_PUFFS; i++) {
            double puffAge = age - i * 0.85;
            if (puffAge < 0.0 || puffAge > SMOKE_LIFETIME_TICKS - 3.0) continue;
            float progress = (float) (puffAge / (SMOKE_LIFETIME_TICKS - 3.0));
            double sway = Math.sin(burst.startTime * 0.37 + i * 2.11) * (0.025 + progress * 0.045);
            Vec3 center = burst.start.add(burst.dir.scale(0.48 + progress * 0.42))
                    .add(sway, 0.04 + progress * 0.32, -sway * 0.6);
            float size = 0.085f + progress * 0.19f;
            int alpha = (int) (112 * (1.0f - progress) * (1.0f - progress));
            drawSmokePuff(buffer, matrix, center, camera, size, 25 + i * 3, 22 + i * 2, 20, alpha);
        }
    }

    private static void renderImpactSmoke(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
                                          Burst burst, double age) {
        for (int i = 0; i < IMPACT_SMOKE_PUFFS; i++) {
            double puffAge = age - i * 0.62;
            if (puffAge < 0.0 || puffAge > SMOKE_LIFETIME_TICKS) continue;
            float progress = (float) (puffAge / SMOKE_LIFETIME_TICKS);
            double angle = burst.startTime * 0.29 + i * 2.399;
            double spread = 0.025 + progress * 0.16;
            Vec3 center = burst.end.subtract(burst.dir.scale(0.035 + progress * 0.08))
                    .add(Math.cos(angle) * spread, 0.025 + progress * 0.28,
                            Math.sin(angle) * spread);
            float size = 0.10f + progress * 0.25f;
            int alpha = (int) (128 * (1.0f - progress) * (1.0f - progress));
            drawSmokePuff(buffer, matrix, center, camera, size, 22 + i * 2, 20 + i * 2, 19, alpha);
        }
    }

    private static void drawSmokePuff(BufferBuilder buffer, Matrix4f matrix, Vec3 center, Vec3 camera,
                                      float size, int r, int g, int b, int alpha) {
        if (alpha <= 0) return;
        Vec3 view = center.subtract(camera).normalize();
        Vec3 right = view.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 0.001) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(view).normalize();

        drawFacingQuad(buffer, matrix, center, right, up, size, size * 0.82f, r, g, b, alpha);
        Vec3 diagonalRight = right.add(up).scale(INV_SQRT_TWO);
        Vec3 diagonalUp = up.subtract(right).scale(INV_SQRT_TWO);
        drawFacingQuad(buffer, matrix, center, diagonalRight, diagonalUp,
                size * 0.82f, size * 0.82f, r + 7, g + 7, b + 7, alpha * 2 / 3);
    }

    private static int colorForSegments(int segments) {
        if (segments <= 2) return 0xFFEB46;
        if (segments <= 4) return 0xFF961E;
        return 0xFF3C0A;
    }

    private static void drawBeamLayers(BufferBuilder buffer, Matrix4f matrix, Vec3 start, Vec3 end,
                                       float baseWidth, int r, int g, int b,
                                       int aOuter, int aMid, int aMain, int aBright, int aCore) {
        Vec3 direction = end.subtract(start).normalize();
        Vec3 side = direction.cross(new Vec3(0, 1, 0)).normalize();
        if (side.lengthSqr() < 0.001) side = direction.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 up = direction.cross(side).normalize();

        emitBeamCross(buffer, matrix, start, end, side, up, baseWidth * 8,
                r * 3 / 5, g * 3 / 5, b * 3 / 5, aOuter);
        emitBeamCross(buffer, matrix, start, end, side, up, baseWidth * 7,
                r * 4 / 5, g * 4 / 5, b * 4 / 5, aMid);
        emitBeamCross(buffer, matrix, start, end, side, up, baseWidth * 6,
                r, g, b, aMain);
        emitBeamCross(buffer, matrix, start, end, side, up, baseWidth * 3,
                r, Math.min(255, g + 80), Math.min(255, b + 30), aBright);
        emitBeamCross(buffer, matrix, start, end, side, up, baseWidth,
                255, Math.min(255, g + 100), Math.min(255, b + 150), aCore);
    }

    private static void emitBeamCross(BufferBuilder buffer, Matrix4f matrix, Vec3 start, Vec3 end,
                                      Vec3 side, Vec3 up, float width, int r, int g, int b, int a) {
        if (a <= 0) return;
        double half = width * 0.5;
        double sx = side.x * half;
        double sy = side.y * half;
        double sz = side.z * half;
        double ux = up.x * half;
        double uy = up.y * half;
        double uz = up.z * half;

        buffer.addVertex(matrix, (float) (start.x - ux), (float) (start.y - uy), (float) (start.z - uz)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) (end.x - ux), (float) (end.y - uy), (float) (end.z - uz)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) (end.x + ux), (float) (end.y + uy), (float) (end.z + uz)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) (start.x + ux), (float) (start.y + uy), (float) (start.z + uz)).setColor(r, g, b, a);

        buffer.addVertex(matrix, (float) (start.x - sx), (float) (start.y - sy), (float) (start.z - sz)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) (end.x - sx), (float) (end.y - sy), (float) (end.z - sz)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) (end.x + sx), (float) (end.y + sy), (float) (end.z + sz)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) (start.x + sx), (float) (start.y + sy), (float) (start.z + sz)).setColor(r, g, b, a);
    }

    private static void drawFacingQuad(BufferBuilder buffer, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up,
                                       float width, float height, int r, int g, int b, int a) {
        if (a <= 0) return;
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
