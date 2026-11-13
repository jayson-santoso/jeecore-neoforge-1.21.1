package net.sonflowers.jeecore.gene.infernal.client;

import net.sonflowers.jeecore.core.client.BeamRenderer;
import net.sonflowers.jeecore.gene.nimbus.client.WindRenderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;
import net.sonflowers.jeecore.gene.infernal.server.RageManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders client rage particles and activation feedback; particle lifetimes outlast changes in
 * emission.
 */
public class RageRenderer {
    private static final List<MistParticle> PARTICLES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<UUID, Vec3> LAST_POSITIONS = new ConcurrentHashMap<>();
    private static long lastParticleTick = Long.MIN_VALUE;
    private static net.minecraft.client.multiplayer.ClientLevel particleLevel;

    private static class MistParticle {
        double x, y, z, vx, vy, vz;
        int age, maxAge;
        float scale;
        double prevX, prevY, prevZ;
        final InfernalFlameShape flame = new InfernalFlameShape(RANDOM);
        boolean isFootTrail;
        boolean isExplosion;

        MistParticle(Vec3 pos, Vec3 motion, boolean isFootTrail, boolean isExplosion) {
            this.x = this.prevX = pos.x; this.y = this.prevY = pos.y; this.z = this.prevZ = pos.z;
            this.vx = motion.x; this.vy = motion.y; this.vz = motion.z;
            this.isFootTrail = isFootTrail;
            this.isExplosion = isExplosion;
            if (isExplosion) {
                this.maxAge = 40 + RANDOM.nextInt(20);
                this.scale = 0.5f + RANDOM.nextFloat() * 0.4f;
            } else {
                this.maxAge = isFootTrail ? 35 + RANDOM.nextInt(15) : 20 + RANDOM.nextInt(8);
                this.scale = isFootTrail ? 0.18f + RANDOM.nextFloat() * 0.1f : 0.12f + RANDOM.nextFloat() * 0.1f;
            }
        }

        boolean tick() {
            prevX = x; prevY = y; prevZ = z;
            if (!isExplosion) {
                Vec3 drift = flame.turbulence(age, isFootTrail ? 0.002 : 0.008);
                vx += drift.x; vy += drift.y; vz += drift.z;
            }
            x += vx; y += vy; z += vz;
            if (isExplosion) {
                vx *= 0.92; vy *= 0.92; vz *= 0.92;
                vy += 0.005;
            } else if (isFootTrail) {
                vy += 0.0005;
                vx *= 0.90; vz *= 0.90;
            } else {
                vy += 0.004;
                vx *= 0.96; vz *= 0.96;
            }
            return ++age < maxAge;
        }
    }

    public static void triggerExplosion(Player player) {
        for (int i = 0; i < 40; i++) {
            Vec3 pos = player.position().add(0, player.getBbHeight() / 2.0, 0);
            double theta = RANDOM.nextDouble() * Math.PI * 2;
            double phi = RANDOM.nextDouble() * Math.PI;
            double speed = 0.4 + RANDOM.nextDouble() * 0.6;
            Vec3 motion = new Vec3(
                    Math.cos(theta) * Math.sin(phi) * speed,
                    Math.cos(phi) * speed,
                    Math.sin(theta) * Math.sin(phi) * speed
            );
            PARTICLES.add(new MistParticle(pos, motion, false, true));
        }
        // Only apply screen shake and flash for local player.
        if (player.level().isClientSide() && player == Minecraft.getInstance().player) {
            BeamRenderer.rageFlashIntensity = 1.0f;
            WindRenderer.screenShakeIntensity = 0.8f;
        }
    }

    public static void spawnParticles(Player player) {
        if (InfernalAerialChargeRenderer.isPouncing(player.getUUID())) return;

        Vec3 current = player.position();
        Vec3 previous = LAST_POSITIONS.put(player.getUUID(), current);
        Vec3 movement = previous == null ? Vec3.ZERO : current.subtract(previous);
        if (movement.lengthSqr() > 256.0) movement = Vec3.ZERO;
        int samples = Math.min(16, Math.max(1, (int) Math.ceil(movement.length() / 0.35)));
        Vec3 start = current.subtract(movement);
        Vec3 wake = movement.lengthSqr() > 0.001 ? movement.normalize().scale(-0.025) : Vec3.ZERO;
        for (int sample = 0; sample < samples; sample++) {
            for (int i = 0; i < (samples == 1 ? 5 : 3); i++) {
                if (PARTICLES.size() >= 4000) break;
                Vec3 pos = start.add(movement.scale((sample + RANDOM.nextDouble()) / samples)).add(
                        (RANDOM.nextDouble() - 0.5) * 0.4,
                        RANDOM.nextDouble() * player.getBbHeight(),
                        (RANDOM.nextDouble() - 0.5) * 0.4);
                Vec3 motion = new Vec3((RANDOM.nextDouble() - 0.5) * 0.1,
                        0.08 + RANDOM.nextDouble() * 0.08,
                        (RANDOM.nextDouble() - 0.5) * 0.1);
                PARTICLES.add(new MistParticle(pos, motion.add(wake), false, false));
            }
        }
        // Foot trail particles only when on ground.
        if (player.onGround()) {
            for (int i = 0; i < 1; i++) {
                Vec3 footPos = player.position().add(
                        (RANDOM.nextDouble() - 0.5) * 0.5, 0.02, (RANDOM.nextDouble() - 0.5) * 0.5);
                Vec3 footMotion = new Vec3(
                        (RANDOM.nextDouble() - 0.5) * 0.01, 0.002, (RANDOM.nextDouble() - 0.5) * 0.01);
                PARTICLES.add(new MistParticle(footPos, footMotion, true, false));
            }
        }
    }

    public static void render(PoseStack poseStack, Vec3 camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (particleLevel != mc.level) {
            clearParticles();
            particleLevel = mc.level;
            lastParticleTick = Long.MIN_VALUE;
        }
        boolean update = !mc.isPaused() && lastParticleTick != mc.level.getGameTime();
        if (update) {
            lastParticleTick = mc.level.getGameTime();
            PARTICLES.removeIf(p -> !p.tick());
            LAST_POSITIONS.keySet().removeIf(id -> !RageManager.isRaging(id) || InfernalAerialChargeRenderer.isPouncing(id));
        }
        List<Player> nearbyPlayers = mc.level.getEntitiesOfClass(Player.class, mc.player.getBoundingBox().inflate(64.0));
        float maxGlobalFade = 0.0f;
        boolean anyPlayerRaging = false;

        for (Player player : nearbyPlayers) {
            // Pouncing stops emission in spawnParticles, but existing aura particles keep their
            // rage intensity and expire through their normal lifetimes.
            if (RageManager.isRaging(player.getUUID())) {
                anyPlayerRaging = true;
                // For local player, use rage bar percentage for fade. For other players, use full
                // intensity since we don't have their rage bar synced.
                float fade = RageManager.getRagePercent(player.getUUID());
                if (player == mc.player) {
                    PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
                    int rageBar = data.getRageBar();
                    int maxRage = data.getMaxRageBar();
                    float ragePercent = maxRage > 0 ? (float) rageBar / maxRage : 0.0f;
                    fade = getRageEffectPercent(ragePercent);
                }
                fade = Math.min(1.0f, Math.max(0.08f, fade));
                maxGlobalFade = Math.max(maxGlobalFade, fade);
                if (update) spawnParticles(player);
            }
        }

        if (!anyPlayerRaging) {
            PARTICLES.clear();
        }
        if (PARTICLES.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f matrix = poseStack.last().pose();
        var cam = mc.gameRenderer.getMainCamera();
        boolean isFirstPerson = mc.options.getCameraType().isFirstPerson();
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();

        final float finalMaxGlobalFade = maxGlobalFade;
        for (MistParticle p : PARTICLES) {
            double distSq = camera.distanceToSqr(p.x, p.y, p.z);
            if (isFirstPerson && distSq < 0.3) continue;

            float lifeFactor = 1.0f - ((float) p.age / p.maxAge);
            int r, g, b;
            if (p.isExplosion) {
                if (lifeFactor > 0.92f) { r = 255; g = 255; b = 255; }
                else if (lifeFactor > 0.80f) { r = 100; g = 200; b = 255; }
                else if (lifeFactor > 0.40f) { r = 255; g = (int)(120 + (80 * lifeFactor)); b = 20; }
                else { r = (int)(255 * (lifeFactor * 2)); g = 20; b = 0; }
            } else if (p.isFootTrail) {
                r = 255; g = (int)(150 * lifeFactor); b = (int)(30 * lifeFactor);
            } else {
                if (lifeFactor > 0.85f) { r = 180; g = 220; b = 255; }
                else if (lifeFactor > 0.5f) { r = 255; g = 255; b = 150; }
                else { r = 255; g = (int)(200 * lifeFactor); b = 0; }
            }

            int alpha = (int)(lifeFactor * (p.isExplosion ? 255 : 160) * finalMaxGlobalFade);
            float size = p.scale * (p.isExplosion ? 1.0f + lifeFactor : 0.7f + lifeFactor * 0.3f);
            float x = (float) (p.prevX + (p.x - p.prevX) * partialTick);
            float y = (float) (p.prevY + (p.y - p.prevY) * partialTick);
            float z = (float) (p.prevZ + (p.z - p.prevZ) * partialTick);
            if (p.isExplosion) {
                drawQuad(buffer, matrix, x, y, z, left, up, size, alpha, r, g, b);
            } else {
                p.flame.draw(buffer, matrix, x, y, z, left, up, size, p.age + partialTick, alpha, r, g, b);
            }
        }

        MeshData rageData = buffer.build();
        if (rageData != null) BufferUploader.drawWithShader(rageData);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(true);
    }

    private static void drawQuad(BufferBuilder b, Matrix4f m, float x, float y, float z,
                                  Vector3f l, Vector3f u, float s, int a, int r, int g, int bl) {
        b.addVertex(m, x + (-l.x() - u.x()) * s, y + (-l.y() - u.y()) * s, z + (-l.z() - u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (l.x() - u.x()) * s, y + (l.y() - u.y()) * s, z + (l.z() - u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (l.x() + u.x()) * s, y + (l.y() + u.y()) * s, z + (l.z() + u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (-l.x() + u.x()) * s, y + (-l.y() + u.y()) * s, z + (-l.z() + u.z()) * s).setColor(r, g, bl, a);
    }

    private static float getRageEffectPercent(float percent) {
        percent = Math.max(0.0f, Math.min(1.0f, percent));
        if (percent >= 0.2f) {
            float highPercent = (percent - 0.2f) / 0.8f;
            return 0.2f + 0.8f * (float) Math.pow(highPercent, 0.35);
        }

        float lowPercent = percent / 0.2f;
        return 0.2f * (float) Math.pow(lowPercent, 2.2);
    }

    public static void clearParticles() {
        PARTICLES.clear();
        LAST_POSITIONS.clear();
    }
}
