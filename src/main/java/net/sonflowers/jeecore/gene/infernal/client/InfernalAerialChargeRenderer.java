package net.sonflowers.jeecore.gene.infernal.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains client pounce visuals, impact particles, and camera feedback from server state.
 */
public class InfernalAerialChargeRenderer {
    private static final Set<UUID> POUNCING_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<ImpactSmokeParticle> IMPACT_SMOKE = new ArrayList<>();
    private static final List<ImpactSparkParticle> IMPACT_SPARKS = new ArrayList<>();
    private static float fovOffset;
    private static float impactFrameFlash;
    private static float heavyImpactShakeTicks;

    public static void setPouncing(UUID playerUUID, boolean active) {
        if (active) {
            POUNCING_PLAYERS.add(playerUUID);
        } else {
            POUNCING_PLAYERS.remove(playerUUID);
            SmokeRenderer.clearPounceTrail(playerUUID);
        }
    }

    public static boolean isPouncing(UUID playerUUID) {
        return POUNCING_PLAYERS.contains(playerUUID);
    }

    public static boolean isActive() {
        return !POUNCING_PLAYERS.isEmpty();
    }

    public static boolean hasImpactEffects() {
        return !IMPACT_SMOKE.isEmpty() || !IMPACT_SPARKS.isEmpty();
    }

    public static float getFovMultiplier() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        boolean localPouncing = mc.player != null && isPouncing(mc.player.getUUID());
        float target = localPouncing ? -0.08f : 0.0f;
        float speed = localPouncing ? 0.12f : 0.2f;
        fovOffset += (target - fovOffset) * speed;
        if (!localPouncing && Math.abs(fovOffset) < 0.001f) {
            fovOffset = 0.0f;
        }
        return 1.0f + fovOffset;
    }

    public static void triggerImpactFrame(float intensity) {
        impactFrameFlash = Math.max(impactFrameFlash, intensity);
        heavyImpactShakeTicks = Math.max(heavyImpactShakeTicks, 14.0f * intensity);
    }

    public static void triggerImpactEffects(Vec3 pos, float intensity) {
        triggerImpactFrame(intensity * 0.72f);
        float effectIntensity = intensity * 0.76f;
        spawnExplosionParticles(pos, effectIntensity);
    }

    public static float getImpactFrameFlash() {
        return impactFrameFlash;
    }

    public static void decayImpactFrameFlash() {
        impactFrameFlash *= 0.62f;
        if (impactFrameFlash < 0.01f) {
            impactFrameFlash = 0.0f;
        }
    }

    public static boolean shouldUseHeavyShakeDecay() {
        return heavyImpactShakeTicks > 0.0f;
    }

    public static void tickHeavyShakeDecay() {
        if (heavyImpactShakeTicks > 0.0f) {
            heavyImpactShakeTicks -= 1.0f;
        }
    }

    public static void render(PoseStack poseStack, Vec3 camera, float partialTick) {
        if (!hasImpactEffects()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        IMPACT_SMOKE.removeIf(particle -> !particle.tick());
        IMPACT_SPARKS.removeIf(particle -> !particle.tick());
        if (!hasImpactEffects()) return;

        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        renderImpactSmoke(poseStack, camera);
        renderImpactSparks(poseStack, camera);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(true);
    }

    private static void spawnExplosionParticles(Vec3 pos, float intensity) {
        Vec3 center = pos.add(0.0, 0.55, 0.0);
        for (int i = 0; i < 34; i++) {
            double theta = RANDOM.nextDouble() * Math.PI * 2.0;
            double phi = RANDOM.nextDouble() * Math.PI;
            double speed = (0.12 + RANDOM.nextDouble() * 0.24) * intensity;
            Vec3 motion = new Vec3(
                    Math.sin(phi) * Math.cos(theta) * speed,
                    Math.cos(phi) * speed * 0.62 + 0.08,
                    Math.sin(phi) * Math.sin(theta) * speed);
            IMPACT_SMOKE.add(new ImpactSmokeParticle(center, motion));
        }

        for (int i = 0; i < 48; i++) {
            double theta = RANDOM.nextDouble() * Math.PI * 2.0;
            double phi = RANDOM.nextDouble() * Math.PI;
            double speed = (0.24 + RANDOM.nextDouble() * 0.46) * intensity;
            Vec3 motion = new Vec3(
                    Math.sin(phi) * Math.cos(theta) * speed,
                    Math.cos(phi) * speed * 0.45 + 0.06,
                    Math.sin(phi) * Math.sin(theta) * speed);
            IMPACT_SPARKS.add(new ImpactSparkParticle(center.add(0.0, 0.2, 0.0), motion));
        }
    }

    private static void renderImpactSmoke(PoseStack poseStack, Vec3 camera) {
        if (IMPACT_SMOKE.isEmpty()) return;
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();
        boolean firstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();

        for (ImpactSmokeParticle p : IMPACT_SMOKE) {
            if (firstPerson && camera.distanceToSqr(p.x, p.y, p.z) < 0.7) continue;
            float life = 1.0f - (float) p.age / p.maxAge;
            int alpha = (int) (160 * life);
            float size = p.scale * (1.0f + (1.0f - life) * 0.8f);
            drawBillboard(buffer, matrix, (float) p.x, (float) p.y, (float) p.z, left, up, size, alpha,
                    (int) (210 * life), (int) (34 * life), (int) (16 * life));
        }

        MeshData data = buffer.build();
        if (data != null) BufferUploader.drawWithShader(data);
    }

    private static void renderImpactSparks(PoseStack poseStack, Vec3 camera) {
        if (IMPACT_SPARKS.isEmpty()) return;
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();
        boolean firstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();

        for (ImpactSparkParticle p : IMPACT_SPARKS) {
            if (firstPerson && camera.distanceToSqr(p.x, p.y, p.z) < 0.35) continue;
            float life = 1.0f - (float) p.age / p.maxAge;
            int alpha = (int) (205 * life);
            int[] color = getSparkColor(p.colorSet);
            drawBillboard(buffer, matrix, (float) p.prevX, (float) p.prevY, (float) p.prevZ, left, up, p.size * 0.8f, alpha / 2, color[0], color[1], color[2]);
            drawBillboard(buffer, matrix, (float) p.x, (float) p.y, (float) p.z, left, up, p.size, alpha, color[0], color[1], color[2]);
        }

        MeshData data = buffer.build();
        if (data != null) BufferUploader.drawWithShader(data);
    }

    private static int[] getSparkColor(int colorSet) {
        return switch (colorSet) {
            case 1 -> new int[]{255, 248, 150};
            case 2 -> new int[]{255, 190, 42};
            default -> new int[]{255, 118, 12};
        };
    }

    private static void drawBillboard(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z,
                                      Vector3f left, Vector3f up, float size, int alpha, int r, int g, int b) {
        buffer.addVertex(matrix, x + (-left.x() - up.x()) * size, y + (-left.y() - up.y()) * size, z + (-left.z() - up.z()) * size).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + (left.x() - up.x()) * size, y + (left.y() - up.y()) * size, z + (left.z() - up.z()) * size).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + (left.x() + up.x()) * size, y + (left.y() + up.y()) * size, z + (left.z() + up.z()) * size).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + (-left.x() + up.x()) * size, y + (-left.y() + up.y()) * size, z + (-left.z() + up.z()) * size).setColor(r, g, b, alpha);
    }

    private static class ImpactSmokeParticle {
        double x, y, z, vx, vy, vz;
        int age, maxAge;
        float scale;

        ImpactSmokeParticle(Vec3 pos, Vec3 motion) {
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            this.vx = motion.x;
            this.vy = motion.y;
            this.vz = motion.z;
            this.maxAge = 34 + RANDOM.nextInt(24);
            this.scale = 0.18f + RANDOM.nextFloat() * 0.26f;
        }

        boolean tick() {
            x += vx;
            y += vy;
            z += vz;
            vx *= 0.91;
            vy = vy * 0.91 + 0.003;
            vz *= 0.91;
            return ++age < maxAge;
        }
    }

    private static class ImpactSparkParticle {
        double x, y, z, vx, vy, vz;
        double prevX, prevY, prevZ;
        int age, maxAge;
        float size;
        int colorSet;

        ImpactSparkParticle(Vec3 pos, Vec3 motion) {
            this.x = this.prevX = pos.x;
            this.y = this.prevY = pos.y;
            this.z = this.prevZ = pos.z;
            this.vx = motion.x;
            this.vy = motion.y;
            this.vz = motion.z;
            this.maxAge = 18 + RANDOM.nextInt(14);
            this.size = 0.026f + RANDOM.nextFloat() * 0.02f;
            this.colorSet = RANDOM.nextInt(3);
        }

        boolean tick() {
            prevX = x;
            prevY = y;
            prevZ = z;
            x += vx;
            y += vy;
            z += vz;
            vy += 0.005;
            vx *= 0.88;
            vy *= 0.88;
            vz *= 0.88;
            return ++age < maxAge;
        }
    }
}
