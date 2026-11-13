package net.sonflowers.jeecore.gene.nimbus.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.sonflowers.jeecore.gene.nimbus.server.CloudManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders client cloud particles and short backward-cone bursts for Windrunner promotions.
 */
public class CloudRenderer {
    private static final List<CloudParticle> CLOUD_PARTICLES = new ArrayList<>();
    private static final List<CoreParticle> CORE_PARTICLES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<UUID, Long> EFFECT_DISABLED_TIME = new ConcurrentHashMap<>();
    private record PromotionPuff(UUID owner, Vec3 origin, Vec3 velocity, float size, float shade,
                                 long started, int lifetime, boolean core) {}
    private static final List<PromotionPuff> PROMOTION_PUFFS = new ArrayList<>();
    private static net.minecraft.client.multiplayer.ClientLevel burstLevel;

    /**
     * The dash's layered cloud quads, emitted as a short backward cone on promotion.
     */
    public static void spawnWindrunnerBurst(UUID owner, Vec3 feet, Vec3 motion, int stage) {
        pruneWindrunnerBursts();
        if (burstLevel == null) return;
        Vec3 backward = motion.multiply(1, 0, 1).normalize().scale(-1);
        if (backward.lengthSqr() < .001) backward = new Vec3(0, 0, -1);
        Vec3 side = new Vec3(-backward.z, 0, backward.x);
        int clouds = 20 + stage * 6;
        for (int i = 0; i < clouds + 4 + stage; i++) {
            boolean core = i >= clouds;
            double spread = (RANDOM.nextDouble() - .5) * .75;
            Vec3 origin = feet.add(0, .06 + RANDOM.nextDouble() * .18, 0)
                    .add(backward.scale(.05 + RANDOM.nextDouble() * .15)).add(side.scale(spread));
            Vec3 velocity = backward.scale(.20 + RANDOM.nextDouble() * .15 + stage * .02)
                    .add(side.scale(spread * .06 + (RANDOM.nextDouble() - .5) * .025))
                    .add(0, .08 + RANDOM.nextDouble() * .07 + stage * .012, 0);
            float size = core ? .025f + RANDOM.nextFloat() * .025f
                    : RANDOM.nextFloat() < .65f ? .045f + RANDOM.nextFloat() * .085f
                    : .13f + RANDOM.nextFloat() * .10f;
            if (PROMOTION_PUFFS.size() >= 512) PROMOTION_PUFFS.removeFirst();
            PROMOTION_PUFFS.add(new PromotionPuff(owner, origin, velocity,
                    size, RANDOM.nextFloat(), burstLevel.getGameTime(), (core ? 24 : 32) + stage * 4, core));
        }
    }

    private static void pruneWindrunnerBursts() {
        var current = Minecraft.getInstance().level;
        if (current != burstLevel) { PROMOTION_PUFFS.clear(); burstLevel = current; }
        if (current != null) PROMOTION_PUFFS.removeIf(p -> current.getGameTime() - p.started >= p.lifetime);
    }

    public static void clearWindrunnerBurst(UUID owner) { PROMOTION_PUFFS.removeIf(p -> p.owner.equals(owner)); }
    public static void clearWindrunnerBursts() { PROMOTION_PUFFS.clear(); burstLevel = null; }

    private static class CloudParticle {
        double x, y, z, vx, vy, vz;
        double[] trailX, trailY, trailZ;
        int age, maxAge;
        float scale;
        float colorVariant;
        int trailLength;
        boolean isSpark;

        CloudParticle(Vec3 pos, Vec3 motion, boolean isSpark) {
            this.x = pos.x; this.y = pos.y; this.z = pos.z;
            this.vx = motion.x; this.vy = motion.y; this.vz = motion.z;
            this.isSpark = isSpark;
            this.maxAge = isSpark ? 15 + RANDOM.nextInt(10) : 50 + RANDOM.nextInt(30);
            this.scale = isSpark ? 0.03f + RANDOM.nextFloat() * 0.04f : 0.22f + RANDOM.nextFloat() * 0.28f;
            this.colorVariant = RANDOM.nextFloat();
            this.trailLength = isSpark ? 5 : 6;
            this.trailX = new double[trailLength];
            this.trailY = new double[trailLength];
            this.trailZ = new double[trailLength];
            for (int i = 0; i < trailLength; i++) {
                trailX[i] = pos.x; trailY[i] = pos.y; trailZ[i] = pos.z;
            }
        }

        boolean tick() {
            System.arraycopy(trailX, 0, trailX, 1, trailLength - 1);
            System.arraycopy(trailY, 0, trailY, 1, trailLength - 1);
            System.arraycopy(trailZ, 0, trailZ, 1, trailLength - 1);
            trailX[0] = x; trailY[0] = y; trailZ[0] = z;
            x += vx; y += vy; z += vz;
            double drag = isSpark ? 0.94 : 0.95;
            vx *= drag; vz *= drag; vy *= drag;
            return ++age < maxAge;
        }
    }

    private static class CoreParticle {
        double x, y, z;
        int age, maxAge;
        float pulsePhase;
        UUID playerUUID;

        CoreParticle(Vec3 pos, UUID playerUUID) {
            this.x = pos.x; this.y = pos.y; this.z = pos.z;
            this.maxAge = 15;
            this.pulsePhase = RANDOM.nextFloat() * (float)(2 * Math.PI);
            this.playerUUID = playerUUID;
        }

        boolean tick() {
            pulsePhase += 0.4f;
            return ++age < maxAge && CloudManager.isClouded(playerUUID);
        }
    }

    public static void spawnParticles(Player player) {
        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        double vX = player.getX() - player.xo;
        double vY = player.getY() - player.yo;
        double vZ = player.getZ() - player.zo;
        Vec3 visualVel = new Vec3(vX, vY, vZ);

        for (int i = 0; i < 2; i++) {
            Vec3 offset = new Vec3(
                    (RANDOM.nextDouble() - 0.5) * 1.8,
                    (RANDOM.nextDouble() - 0.5) * 1.6,
                    (RANDOM.nextDouble() - 0.5) * 1.8);
            Vec3 motion = new Vec3(
                    -visualVel.x * 0.25 + (RANDOM.nextDouble() - 0.5) * 0.04,
                    0.01 + (RANDOM.nextDouble() * 0.01),
                    -visualVel.z * 0.25 + (RANDOM.nextDouble() - 0.5) * 0.04);
            CLOUD_PARTICLES.add(new CloudParticle(center.add(offset), motion, false));
        }

        if (RANDOM.nextInt(2) == 0) {
            double theta = RANDOM.nextDouble() * 2.0 * Math.PI;
            double phi = Math.acos(2.0 * RANDOM.nextDouble() - 1.0);
            double r = 0.3 + (RANDOM.nextDouble() * 0.4);
            double dx = r * Math.sin(phi) * Math.cos(theta);
            double dy = r * Math.sin(phi) * Math.sin(theta);
            double dz = r * Math.cos(phi);
            Vec3 sparkPos = center.add(dx, dy, dz);
            Vec3 radialMotion = new Vec3(dx, dy, dz).scale(0.15);
            Vec3 finalMotion = radialMotion.add(visualVel.scale(-0.4));
            CLOUD_PARTICLES.add(new CloudParticle(sparkPos, finalMotion, true));
        }

        if (RANDOM.nextInt(3) == 0) {
            Vec3 corePos = center.add(
                    (RANDOM.nextDouble() - 0.5) * 0.6,
                    (RANDOM.nextDouble() - 0.5) * 0.4,
                    (RANDOM.nextDouble() - 0.5) * 0.6);
            CORE_PARTICLES.add(new CoreParticle(corePos, player.getUUID()));
        }
    }

    public static void render(PoseStack poseStack, Vec3 camera, float partialTick) {
        Player localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null || localPlayer.level() == null) return;

        CLOUD_PARTICLES.removeIf(p -> !p.tick());
        CORE_PARTICLES.removeIf(p -> !p.tick());
        pruneWindrunnerBursts();

        if (CLOUD_PARTICLES.isEmpty() && CORE_PARTICLES.isEmpty() && PROMOTION_PUFFS.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        renderCloudParticles(poseStack, camera, false, partialTick);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        renderCloudParticles(poseStack, camera, true, partialTick);
        renderCoreParticles(poseStack, camera);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(true);
    }

    private static void renderCloudParticles(PoseStack poseStack, Vec3 camera, boolean renderSparks, float partialTick) {
        if (!renderSparks) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f matrix = poseStack.last().pose();
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        boolean isFirstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();

        for (CloudParticle p : CLOUD_PARTICLES) {
            if (p.isSpark != renderSparks) continue;
            if (isFirstPerson && camera.distanceToSqr(p.x, p.y, p.z) < (p.isSpark ? 0.2 : 0.6)) continue;

            float lifeFactor = 1.0f - ((float) p.age / p.maxAge);
            int r, g, b, alpha;
            if (p.isSpark) {
                r = 255; g = 255; b = 255;
                alpha = (int)(lifeFactor * 255);
            } else {
                if (p.colorVariant < 0.33f) { r = 210; g = 230; b = 255; }
                else if (p.colorVariant < 0.66f) { r = 220; g = 255; b = 220; }
                else { r = 255; g = 225; b = 240; }
                alpha = (int)(lifeFactor * 80);
            }

            float size = p.scale * (1.1f + (1.0f - lifeFactor) * 0.4f);
            if (!p.isSpark) {
                drawQuad(buffer, matrix, (float)p.x, (float)p.y, (float)p.z, left, up, size * 1.4f, alpha / 3, r, g, b);
            }
            drawQuad(buffer, matrix, (float)p.x, (float)p.y, (float)p.z, left, up, size, alpha, r, g, b);

            for (int i = 1; i < p.trailLength; i++) {
                float trailFactor = 1.0f - ((float) i / p.trailLength);
                int trailAlpha = (int)(alpha * trailFactor * 0.5f);
                drawQuad(buffer, matrix, (float)p.trailX[i], (float)p.trailY[i], (float)p.trailZ[i],
                        left, up, size * trailFactor, trailAlpha, r, g, b);
            }
        }
        if (burstLevel != null) {
            double now = burstLevel.getGameTime() + partialTick;
            for (PromotionPuff p : PROMOTION_PUFFS) {
                if (p.core != renderSparks) continue;
                float age = Math.max(0, (float)(now - p.started));
                float life = Math.clamp(1 - age / p.lifetime, 0, 1);
                if (life <= 0) continue;
                double travel = (1 - Math.pow(.92, age)) / .08;
                Vec3 center = p.origin.add(p.velocity.scale(travel))
                        .add(0, Math.sin(age * .25 + p.shade * 6) * .045 * (1 - life), 0);
                if (isFirstPerson && center.distanceToSqr(camera) < .8) continue;
                int red = p.shade < .33 ? 210 : p.shade < .66 ? 220 : 255;
                int green = p.shade < .33 ? 230 : p.shade < .66 ? 255 : 225;
                int blue = p.shade < .33 ? 255 : p.shade < .66 ? 220 : 240;
                if (p.core) {
                    float pulse = .8f + .2f * (float)Math.sin(age * .7 + p.shade * 6);
                    drawQuad(buffer, matrix, (float)center.x, (float)center.y, (float)center.z,
                            left, up, p.size * 4.5f * pulse, (int)(36 * life), red, green, blue);
                    drawQuad(buffer, matrix, (float)center.x, (float)center.y, (float)center.z,
                            left, up, p.size * pulse, (int)(190 * life * life), 255, 255, 255);
                } else {
                    float size = p.size * (1 + (1 - life) * .45f);
                    int alpha = (int)(72 * life * life * Math.min(1, (age + .5f) / 2));
                    drawQuad(buffer, matrix, (float)center.x, (float)center.y, (float)center.z,
                            left, up, size * 1.4f, alpha / 4, red, green, blue);
                    drawQuad(buffer, matrix, (float)center.x, (float)center.y, (float)center.z,
                            left, up, size, alpha, red, green, blue);
                }
            }
        }
        MeshData cloudData = buffer.build();
        if (cloudData != null) BufferUploader.drawWithShader(cloudData);
    }

    private static void renderCoreParticles(PoseStack poseStack, Vec3 camera) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f matrix = poseStack.last().pose();
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();

        for (CoreParticle p : CORE_PARTICLES) {
            float lifeFactor = 1.0f - ((float) p.age / p.maxAge);
            float pulse = (float)(0.8f + 0.2f * Math.sin(p.pulsePhase));
            int r = 255, g = 255, b = 255;
            int coreAlpha = (int)(lifeFactor * 200);
            int glowAlpha = (int)(lifeFactor * 60);
            drawQuad(buffer, matrix, (float)p.x, (float)p.y, (float)p.z, left, up, 0.4f * pulse, glowAlpha, r, g, b);
            drawQuad(buffer, matrix, (float)p.x, (float)p.y, (float)p.z, left, up, 0.12f * pulse, coreAlpha, r, g, b);
        }
        MeshData coreData = buffer.build();
        if (coreData != null) BufferUploader.drawWithShader(coreData);
    }

    private static void drawQuad(BufferBuilder b, Matrix4f m, float x, float y, float z,
                                  Vector3f l, Vector3f u, float s, int a, int r, int g, int bl) {
        b.addVertex(m, x + (-l.x() - u.x()) * s, y + (-l.y() - u.y()) * s, z + (-l.z() - u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (l.x() - u.x()) * s, y + (l.y() - u.y()) * s, z + (l.z() - u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (l.x() + u.x()) * s, y + (l.y() + u.y()) * s, z + (l.z() + u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (-l.x() + u.x()) * s, y + (-l.y() + u.y()) * s, z + (-l.z() + u.z()) * s).setColor(r, g, bl, a);
    }

    public static void clearParticles() {
        CLOUD_PARTICLES.clear();
        CORE_PARTICLES.clear();
        EFFECT_DISABLED_TIME.clear();
        clearWindrunnerBursts();
    }

    public static boolean hasParticles() {
        pruneWindrunnerBursts();
        return !CLOUD_PARTICLES.isEmpty() || !CORE_PARTICLES.isEmpty() || !PROMOTION_PUFFS.isEmpty();
    }
}
