package net.sonflowers.jeecore.gene.infernal.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.sonflowers.jeecore.gene.infernal.server.SmokeManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders client smoke-form embers and pounce trails, retaining particles until they expire or are
 * cleared.
 */
public class SmokeRenderer {
    private static final List<SmokeParticle> SMOKE_PARTICLES = new ArrayList<>();
    private static final List<SparkParticle> SPARK_PARTICLES = new ArrayList<>();
    private static final List<CoreParticle> CORE_PARTICLES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<UUID, Long> EFFECT_DISABLED_TIME = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SPAWN_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> LAST_POUNCE_POSITIONS = new ConcurrentHashMap<>();
    private static long lastParticleTick = Long.MIN_VALUE;
    private static net.minecraft.client.multiplayer.ClientLevel particleLevel;
    private static final double FIRST_PERSON_POUNCE_CULL_RADIUS_SQR = 1.0;

    private static class SmokeParticle {
        double x, y, z, vx, vy, vz;
        int age, maxAge;
        float scale;
        float darkTone;
        boolean red;

        SmokeParticle(Vec3 pos, Vec3 motion, boolean red) {
            this.x = pos.x; this.y = pos.y; this.z = pos.z;
            this.vx = motion.x; this.vy = motion.y; this.vz = motion.z;
            this.maxAge = 35 + RANDOM.nextInt(25);
            this.scale = 0.15f + RANDOM.nextFloat() * 0.22f;
            this.darkTone = 0.05f + RANDOM.nextFloat() * 0.25f;
            this.red = red;
        }

        boolean tick() {
            x += vx; y += vy; z += vz;
            vx *= 0.95; vy *= 0.95; vz *= 0.95;
            return ++age < maxAge;
        }
    }

    private static class SparkParticle {
        double x, y, z, vx, vy, vz;
        double prevX, prevY, prevZ;
        int age, maxAge;
        float size;
        int colorSet;
        boolean red;
        final boolean originalFlame;
        final InfernalFlameShape flame = new InfernalFlameShape(RANDOM);

        SparkParticle(Vec3 pos, Vec3 motion, boolean red) {
            this.x = this.prevX = pos.x;
            this.y = this.prevY = pos.y;
            this.z = this.prevZ = pos.z;
            this.vx = motion.x; this.vy = motion.y; this.vz = motion.z;
            this.maxAge = 20 + RANDOM.nextInt(16);
            this.size = 0.02f + RANDOM.nextFloat() * 0.015f;
            this.colorSet = RANDOM.nextInt(3);
            this.red = red;
            this.originalFlame = !red && RANDOM.nextFloat() < 0.3f;
        }

        boolean tick() {
            prevX = x; prevY = y; prevZ = z;
            if (originalFlame) {
                x += vx; z += vz;
                vx *= 0.94; vz *= 0.94;
                return ++age < maxAge;
            }
            Vec3 drift = flame.turbulence(age, red ? 0.008 : 0.01);
            vx += drift.x; vz += drift.z;
            if (red) {
                vy = vy * 0.94 + drift.y + 0.002;
                y += vy;
            } else {
                // Smokeform keeps its horizontal ember drift, with a small vertical flutter.
                y += drift.y;
            }
            x += vx; z += vz;
            vx *= 0.94; vz *= 0.94;
            return ++age < maxAge;
        }
    }

    private static void ensureLevel() {
        var level = Minecraft.getInstance().level;
        if (particleLevel != level) {
            clearParticles();
            particleLevel = level;
            lastParticleTick = Long.MIN_VALUE;
        }
    }

    private static boolean beginSpawn(Player player) {
        ensureLevel();
        if (Minecraft.getInstance().isPaused()) return false;
        long tick = player.level().getGameTime();
        Long previous = LAST_SPAWN_TICKS.put(player.getUUID(), tick);
        return previous == null || previous != tick;
    }

    public static void spawnParticles(Player player) {
        if (!beginSpawn(player)) return;
        LAST_POUNCE_POSITIONS.remove(player.getUUID());
        spawnParticles(player, false);
    }

    private static class CoreParticle {
        double x, y, z, vx, vy, vz;
        int age, maxAge;
        float size;
        final InfernalFlameShape coreFlame = new InfernalFlameShape(RANDOM);

        double prevX, prevY, prevZ;

        CoreParticle(Vec3 pos, Vec3 motion) {
            this.x = this.prevX = pos.x; this.y = this.prevY = pos.y; this.z = this.prevZ = pos.z;
            this.vx = motion.x; this.vy = motion.y; this.vz = motion.z;
            this.maxAge = 12 + RANDOM.nextInt(10);
            this.size = 0.055f + RANDOM.nextFloat() * 0.045f;
        }

        boolean tick() {
            prevX = x; prevY = y; prevZ = z;
            x += vx; y += vy; z += vz;
            Vec3 drift = coreFlame.turbulence(age, 0.01);
            vx = vx * 0.90 + drift.x; vy = vy * 0.88 + drift.y + 0.003; vz = vz * 0.90 + drift.z;
            return ++age < maxAge;
        }
    }

    public static void spawnPounceParticles(Player player) {
        if (!beginSpawn(player)) return;
        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        Vec3 previous = LAST_POUNCE_POSITIONS.put(player.getUUID(), center);
        Vec3 movement = previous == null ? Vec3.ZERO : center.subtract(previous);
        // Never connect a teleport or dimension transfer with a long flame ribbon.
        if (movement.lengthSqr() > 256.0) movement = Vec3.ZERO;
        int samples = Math.min(24, Math.max(1, (int) Math.ceil(movement.length() / 0.25)));
        Vec3 start = center.subtract(movement);
        Vec3 wake = movement.lengthSqr() > 0.001 ? movement.normalize().scale(0.06) : Vec3.ZERO;
        for (int sample = 0; sample < samples; sample++) {
            if (SMOKE_PARTICLES.size() + SPARK_PARTICLES.size() + CORE_PARTICLES.size() > 6000) break;
            double t = (sample + RANDOM.nextDouble()) / samples;
            spawnParticles(player, true, start.add(movement.scale(t)), wake);
        }
    }

    private static void spawnParticles(Player player, boolean redSmoke) {
        Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
        double vX = player.getX() - player.xo;
        double vY = player.getY() - player.yo;
        double vZ = player.getZ() - player.zo;
        Vec3 visualVel = new Vec3(vX, vY, vZ);

        spawnParticles(player, redSmoke, center, visualVel);
    }

    public static void clearPounceTrail(UUID playerUUID) {
        LAST_POUNCE_POSITIONS.remove(playerUUID);
    }

    private static void spawnParticles(Player player, boolean redSmoke, Vec3 center, Vec3 visualVel) {
        for (int i = 0; i < (redSmoke ? 2 : 6); i++) {
            Vec3 offset = new Vec3(
                    (RANDOM.nextDouble() - 0.5) * 1.6,
                    (RANDOM.nextDouble() - 0.5) * 1.5,
                    (RANDOM.nextDouble() - 0.5) * 1.6);
            Vec3 motion = new Vec3(
                    -visualVel.x * 0.25 + (RANDOM.nextDouble() - 0.5) * 0.05,
                    redSmoke ? -visualVel.y * 0.25 + 0.01 : 0.0,
                    -visualVel.z * 0.25 + (RANDOM.nextDouble() - 0.5) * 0.05);
            SMOKE_PARTICLES.add(new SmokeParticle(center.add(offset), motion, redSmoke));
        }

        // Spawn more sparks for jet stream effect.
        int sparkCount = redSmoke ? 2 : (RANDOM.nextFloat() < 0.5f ? 6 : 4);
        for (int i = 0; i < sparkCount; i++) {
            Vec3 spawnPos = center.add(
                    (RANDOM.nextDouble() - 0.5) * 1.3,
                    (RANDOM.nextDouble() - 0.5) * 1.0,
                    (RANDOM.nextDouble() - 0.5) * 1.3);
            Vec3 sparkMotion = new Vec3(
                    -visualVel.x * 0.4 + (RANDOM.nextDouble() - 0.5) * 0.15,
                    -visualVel.y * 0.4 + (RANDOM.nextDouble() - 0.5) * 0.05,
                    -visualVel.z * 0.4 + (RANDOM.nextDouble() - 0.5) * 0.15);
            SPARK_PARTICLES.add(new SparkParticle(spawnPos, sparkMotion, redSmoke));
        }

        if (redSmoke) {
            for (int i = 0; i < 3; i++) {
                Vec3 corePos = center.add(
                        (RANDOM.nextDouble() - 0.5) * 1.0,
                        (RANDOM.nextDouble() - 0.5) * 0.9,
                        (RANDOM.nextDouble() - 0.5) * 1.0);
                Vec3 coreMotion = new Vec3(
                        -visualVel.x * 0.18 + (RANDOM.nextDouble() - 0.5) * 0.04,
                        -visualVel.y * 0.18 + (RANDOM.nextDouble() - 0.5) * 0.045,
                        -visualVel.z * 0.18 + (RANDOM.nextDouble() - 0.5) * 0.04);
                CORE_PARTICLES.add(new CoreParticle(corePos, coreMotion));
            }
        }
    }

    public static void render(PoseStack poseStack, Vec3 camera, float partialTick) {
        Player localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null || localPlayer.level() == null) return;

        ensureLevel();
        if (!Minecraft.getInstance().isPaused() && lastParticleTick != localPlayer.level().getGameTime()) {
            lastParticleTick = localPlayer.level().getGameTime();
            SMOKE_PARTICLES.removeIf(p -> !p.tick());
            SPARK_PARTICLES.removeIf(p -> !p.tick());
            CORE_PARTICLES.removeIf(p -> !p.tick());
            LAST_POUNCE_POSITIONS.keySet().removeIf(id -> !InfernalAerialChargeRenderer.isPouncing(id));
            LAST_SPAWN_TICKS.entrySet().removeIf(entry -> localPlayer.level().getGameTime() - entry.getValue() > 100);
        }

        if (SMOKE_PARTICLES.isEmpty() && SPARK_PARTICLES.isEmpty() && CORE_PARTICLES.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        renderSmokeParticles(poseStack, camera);
        renderSparkParticles(poseStack, camera);
        renderCoreParticles(poseStack, camera);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(true);
    }

    private static void renderSmokeParticles(PoseStack poseStack, Vec3 camera) {
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f matrix = poseStack.last().pose();
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        boolean isFirstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();
        boolean cullLocalPounce = shouldCullLocalPounceParticles(isFirstPerson);
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();

        for (SmokeParticle p : SMOKE_PARTICLES) {
            double distSq = camera.distanceToSqr(p.x, p.y, p.z);
            if (isFirstPerson && distSq < 0.6) continue;
            if (cullLocalPounce && p.red && distSq < FIRST_PERSON_POUNCE_CULL_RADIUS_SQR) continue;
            float lifeFactor = 1.0f - ((float) p.age / p.maxAge);
            int colorVal = (int)(p.darkTone * 255 * lifeFactor);
            int alpha = (int)(lifeFactor * 180);
            float size = p.scale * (1.1f + (1.0f - lifeFactor) * 0.5f);
            if (p.red) {
                int red = (int) (210 * lifeFactor);
                int green = (int) (36 * lifeFactor);
                int blue = (int) (18 * lifeFactor);
                drawQuad(buffer, matrix, (float)p.x, (float)p.y, (float)p.z, left, up, size * 1.08f, Math.min(220, alpha + 35), red, green, blue);
            } else {
                drawQuad(buffer, matrix, (float)p.x, (float)p.y, (float)p.z, left, up, size, alpha, colorVal, colorVal, colorVal);
            }
        }
        MeshData smokeData = buffer.build();
        if (smokeData != null) BufferUploader.drawWithShader(smokeData);
    }

    private static void renderSparkParticles(PoseStack poseStack, Vec3 camera) {
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f matrix = poseStack.last().pose();
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        boolean isFirstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();
        boolean cullLocalPounce = shouldCullLocalPounceParticles(isFirstPerson);
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();

        for (SparkParticle p : SPARK_PARTICLES) {
            double distSq = camera.distanceToSqr(p.x, p.y, p.z);
            if (isFirstPerson && distSq < 0.25) continue;
            if (cullLocalPounce && p.red && distSq < FIRST_PERSON_POUNCE_CULL_RADIUS_SQR) continue;
            float lifeFactor = 1.0f - ((float) p.age / p.maxAge);
            int alpha = (int)(lifeFactor * 235);
            int[] color = getSparkColor(p.colorSet);
            if (p.originalFlame) {
                // Preserve the original fixed ember shape and its softer trailing copy.
                drawHorizontalRect(buffer, matrix, (float) p.prevX, (float) p.prevY, (float) p.prevZ,
                        left, up, p.size * 0.8f, alpha / 2, color[0], color[1], color[2]);
                drawHorizontalRect(buffer, matrix, (float) p.x, (float) p.y, (float) p.z,
                        left, up, p.size, alpha, color[0], color[1], color[2]);
                continue;
            }
            {
                float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
                float flameSize = p.red ? 1.8f : 2.5f;
                p.flame.draw(buffer, matrix,
                        (float) (p.prevX + (p.x - p.prevX) * partial),
                        (float) (p.prevY + (p.y - p.prevY) * partial),
                        (float) (p.prevZ + (p.z - p.prevZ) * partial), left, up,
                        p.size * flameSize, p.age + partial, alpha, color[0], color[1], color[2]);
            }
        }
        MeshData sparkData = buffer.build();
        if (sparkData != null) BufferUploader.drawWithShader(sparkData);
    }

    private static void renderCoreParticles(PoseStack poseStack, Vec3 camera) {
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f matrix = poseStack.last().pose();
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        boolean isFirstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();
        boolean cullLocalPounce = shouldCullLocalPounceParticles(isFirstPerson);
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();

        for (CoreParticle p : CORE_PARTICLES) {
            double distSq = camera.distanceToSqr(p.x, p.y, p.z);
            if (isFirstPerson && distSq < 0.25) continue;
            if (cullLocalPounce && distSq < FIRST_PERSON_POUNCE_CULL_RADIUS_SQR) continue;
            float lifeFactor = 1.0f - ((float) p.age / p.maxAge);
            int alpha = (int) (lifeFactor * 245);
            float size = p.size * (0.75f + lifeFactor * 1.15f);
            float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            float x = (float) (p.prevX + (p.x - p.prevX) * partial);
            float y = (float) (p.prevY + (p.y - p.prevY) * partial);
            float z = (float) (p.prevZ + (p.z - p.prevZ) * partial);
            p.coreFlame.draw(buffer, matrix, x, y, z, left, up, size * 2.4f, p.age + partial, alpha / 3, 255, 20, 8);
            p.coreFlame.draw(buffer, matrix, x, y, z, left, up, size, p.age + partial, alpha, 255, 225, 115);
        }

        MeshData coreData = buffer.build();
        if (coreData != null) BufferUploader.drawWithShader(coreData);
    }

    private static boolean shouldCullLocalPounceParticles(boolean isFirstPerson) {
        Player localPlayer = Minecraft.getInstance().player;
        return isFirstPerson && localPlayer != null && InfernalAerialChargeRenderer.isPouncing(localPlayer.getUUID());
    }

    private static int[] getSparkColor(int colorSet) {
        return switch (colorSet) {
            case 1 -> new int[]{255, 230, 84};
            case 2 -> new int[]{255, 104, 16};
            default -> new int[]{255, 44, 8};
        };
    }

    private static void drawHorizontalRect(BufferBuilder b, Matrix4f m, float x, float y, float z,
                                           Vector3f l, Vector3f u, float s, int a, int r, int g, int bl) {
        // Create horizontal rectangle by scaling left vector more than up vector.
        float horizontalScale = s * 3.5f;
        float verticalScale = s * 0.4f;
        b.addVertex(m, x + (-l.x() - u.x()) * horizontalScale + (-u.x()) * verticalScale, 
                       y + (-l.y() - u.y()) * horizontalScale + (-u.y()) * verticalScale, 
                       z + (-l.z() - u.z()) * horizontalScale + (-u.z()) * verticalScale).setColor(r, g, bl, a);
        b.addVertex(m, x + (l.x() - u.x()) * horizontalScale + (-u.x()) * verticalScale, 
                       y + (l.y() - u.y()) * horizontalScale + (-u.y()) * verticalScale, 
                       z + (l.z() - u.z()) * horizontalScale + (-u.z()) * verticalScale).setColor(r, g, bl, a);
        b.addVertex(m, x + (l.x() + u.x()) * horizontalScale + (u.x()) * verticalScale, 
                       y + (l.y() + u.y()) * horizontalScale + (u.y()) * verticalScale, 
                       z + (l.z() + u.z()) * horizontalScale + (u.z()) * verticalScale).setColor(r, g, bl, a);
        b.addVertex(m, x + (-l.x() + u.x()) * horizontalScale + (u.x()) * verticalScale, 
                       y + (-l.y() + u.y()) * horizontalScale + (u.y()) * verticalScale, 
                       z + (-l.z() + u.z()) * horizontalScale + (u.z()) * verticalScale).setColor(r, g, bl, a);
    }

    private static void drawQuad(BufferBuilder b, Matrix4f m, float x, float y, float z,
                                  Vector3f l, Vector3f u, float s, int a, int r, int g, int bl) {
        b.addVertex(m, x + (-l.x() - u.x()) * s, y + (-l.y() - u.y()) * s, z + (-l.z() - u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (l.x() - u.x()) * s, y + (l.y() - u.y()) * s, z + (l.z() - u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (l.x() + u.x()) * s, y + (l.y() + u.y()) * s, z + (l.z() + u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (-l.x() + u.x()) * s, y + (-l.y() + u.y()) * s, z + (-l.z() + u.z()) * s).setColor(r, g, bl, a);
    }

    public static void clearParticles() {
        SMOKE_PARTICLES.clear();
        SPARK_PARTICLES.clear();
        CORE_PARTICLES.clear();
        EFFECT_DISABLED_TIME.clear();
        LAST_SPAWN_TICKS.clear();
        LAST_POUNCE_POSITIONS.clear();
    }

    public static boolean hasParticles() {
        return !SMOKE_PARTICLES.isEmpty() || !SPARK_PARTICLES.isEmpty() || !CORE_PARTICLES.isEmpty();
    }
}
