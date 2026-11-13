package net.sonflowers.jeecore.gene.infernal.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns transient client smoke-exit bursts and renders smoke and sparks in separate blend passes.
 */
public class SmokeExitRenderer {
    private static final Map<UUID, ExitBurst> ACTIVE_BURSTS = new ConcurrentHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int BURST_DURATION_TICKS = 30;

    private record ExitBurst(long startTick, Vec3 position) {
        boolean isExpired(long currentTick) {
            return currentTick - startTick > BURST_DURATION_TICKS;
        }

        float getAgeFactor(long currentTick) {
            return (float) (currentTick - startTick) / BURST_DURATION_TICKS;
        }
    }

    private static class SmokeParticle {
        double x, y, z, vx, vy, vz;
        int age, maxAge;
        float scale;
        float darkTone;

        SmokeParticle(Vec3 pos, Vec3 motion) {
            this.x = pos.x; this.y = pos.y; this.z = pos.z;
            this.vx = motion.x; this.vy = motion.y; this.vz = motion.z;
            this.maxAge = 30 + RANDOM.nextInt(20);
            this.scale = 0.2f + RANDOM.nextFloat() * 0.25f;
            this.darkTone = 0.06f + RANDOM.nextFloat() * 0.22f;
        }

        boolean tick() {
            x += vx; y += vy; z += vz;
            vx *= 0.94; vy *= 0.94; vz *= 0.94;
            return ++age < maxAge;
        }
    }

    private static class SparkParticle {
        double x, y, z, vx, vy, vz;
        double prevX, prevY, prevZ;
        int age, maxAge;
        float size;
        int colorSet;

        SparkParticle(Vec3 pos, Vec3 motion) {
            this.x = this.prevX = pos.x;
            this.y = this.prevY = pos.y;
            this.z = this.prevZ = pos.z;
            this.vx = motion.x;
            this.vy = motion.y;
            this.vz = motion.z;
            this.maxAge = 18 + RANDOM.nextInt(12);
            this.size = 0.022f + RANDOM.nextFloat() * 0.018f;
            this.colorSet = RANDOM.nextInt(3);
        }

        boolean tick() {
            prevX = x; prevY = y; prevZ = z;
            x += vx; y += vy; z += vz;
            vy += 0.006;
            vx *= 0.90; vy *= 0.90; vz *= 0.90;
            return ++age < maxAge;
        }
    }

    private static final List<SmokeParticle> SMOKE_PARTICLES = new ArrayList<>();
    private static final List<SparkParticle> SPARK_PARTICLES = new ArrayList<>();

    public static void trigger(UUID playerUUID, Vec3 position) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        long currentTick = level.getGameTime();
        ACTIVE_BURSTS.put(playerUUID, new ExitBurst(currentTick, position));
        spawnParticles(position);
    }

    private static void spawnParticles(Vec3 pos) {
        // Spawn dark smoke particles exploding outward.
        for (int i = 0; i < 48; i++) {
            double theta = RANDOM.nextDouble() * Math.PI * 2.0;
            double phi = RANDOM.nextDouble() * Math.PI;
            double speed = 0.12 + RANDOM.nextDouble() * 0.18;

            Vec3 motion = new Vec3(
                    Math.sin(phi) * Math.cos(theta) * speed,
                    Math.cos(phi) * speed * 0.6 + 0.04,
                    Math.sin(phi) * Math.sin(theta) * speed
            );
            SMOKE_PARTICLES.add(new SmokeParticle(pos.add(0, 0.5, 0), motion));
        }

        // Spawn bright sparks shooting outward.
        for (int i = 0; i < 28; i++) {
            double theta = RANDOM.nextDouble() * Math.PI * 2.0;
            double phi = RANDOM.nextDouble() * Math.PI;
            double speed = 0.18 + RANDOM.nextDouble() * 0.28;

            Vec3 motion = new Vec3(
                    Math.sin(phi) * Math.cos(theta) * speed,
                    Math.cos(phi) * speed * 0.5 + 0.05,
                    Math.sin(phi) * Math.sin(theta) * speed
            );
            SPARK_PARTICLES.add(new SparkParticle(pos.add(0, 0.8, 0), motion));
        }
    }

    public static void render(Matrix4f matrix, double gameTime) {
        // Clean up expired bursts.
        long currentTick = (long) gameTime;
        ACTIVE_BURSTS.entrySet().removeIf(entry -> entry.getValue().isExpired(currentTick));

        SMOKE_PARTICLES.removeIf(p -> !p.tick());
        SPARK_PARTICLES.removeIf(p -> !p.tick());

        if (SMOKE_PARTICLES.isEmpty() && SPARK_PARTICLES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        var cam = mc.gameRenderer.getMainCamera();
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();

        // Render smoke particles with dark blending like SmokeRenderer.
        if (!SMOKE_PARTICLES.isEmpty()) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder smokeBuffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            for (SmokeParticle p : SMOKE_PARTICLES) {
                if (firstPerson && camera.distanceToSqr(p.x, p.y, p.z) < 0.6) continue;
                float lifeFactor = 1.0f - ((float) p.age / p.maxAge);
                int colorVal = (int)(p.darkTone * 255 * lifeFactor);
                int alpha = (int)(lifeFactor * 180);
                float size = p.scale * (1.1f + (1.0f - lifeFactor) * 0.5f);
                drawQuad(smokeBuffer, matrix, (float) p.x, (float) p.y, (float) p.z,
                        left, up, size, alpha, colorVal, colorVal, colorVal);
            }
            MeshData smokeData = smokeBuffer.build();
            if (smokeData != null) BufferUploader.drawWithShader(smokeData);
        }

        // Render spark particles with additive blending.
        if (!SPARK_PARTICLES.isEmpty()) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder sparkBuffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            for (SparkParticle p : SPARK_PARTICLES) {
                if (firstPerson && camera.distanceToSqr(p.x, p.y, p.z) < 0.25) continue;
                float lifeFactor = 1.0f - ((float) p.age / p.maxAge);
                int alpha = (int)(lifeFactor * 235);
                int[] color = getSparkColor(p.colorSet);

                // Draw trail from previous position.
                drawQuad(sparkBuffer, matrix, (float)p.prevX, (float)p.prevY, (float)p.prevZ, 
                        left, up, p.size * 0.8f, alpha / 2, color[0], color[1], color[2]);

                // Draw current position.
                drawQuad(sparkBuffer, matrix, (float)p.x, (float)p.y, (float)p.z, 
                        left, up, p.size, alpha, color[0], color[1], color[2]);
            }
            MeshData sparkData = sparkBuffer.build();
            if (sparkData != null) BufferUploader.drawWithShader(sparkData);
        }
    }

    private static int[] getSparkColor(int colorSet) {
        // Match SmokeRenderer spark colors.
        return switch (colorSet) {
            case 1 -> new int[]{255, 230, 84};
            case 2 -> new int[]{255, 104, 16};
            default -> new int[]{255, 44, 8};
        };
    }

    private static void drawQuad(BufferBuilder b, Matrix4f m, float x, float y, float z,
                                 Vector3f l, Vector3f u, float s, int a, int r, int g, int bl) {
        b.addVertex(m, x + (-l.x() - u.x()) * s, y + (-l.y() - u.y()) * s, z + (-l.z() - u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (l.x() - u.x()) * s, y + (l.y() - u.y()) * s, z + (l.z() - u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (l.x() + u.x()) * s, y + (l.y() + u.y()) * s, z + (l.z() + u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (-l.x() + u.x()) * s, y + (-l.y() + u.y()) * s, z + (-l.z() + u.z()) * s).setColor(r, g, bl, a);
    }

    public static boolean hasParticles() {
        return !SMOKE_PARTICLES.isEmpty() || !SPARK_PARTICLES.isEmpty() || !ACTIVE_BURSTS.isEmpty();
    }
}
