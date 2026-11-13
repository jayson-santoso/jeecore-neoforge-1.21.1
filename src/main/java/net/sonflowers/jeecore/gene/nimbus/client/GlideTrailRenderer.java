package net.sonflowers.jeecore.gene.nimbus.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders client trails for gliding players and retains particles until expiry or explicit
 * clearing.
 */
public class GlideTrailRenderer {
    private static final List<TrailParticle> PARTICLES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private static class TrailParticle {
        double x, y, z, vx, vy, vz;
        double[] trailX, trailY, trailZ;
        int age, maxAge;
        float scale;
        float alphaScale;
        boolean core;

        TrailParticle(Vec3 pos, Vec3 motion, boolean core) {
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            this.vx = motion.x;
            this.vy = motion.y;
            this.vz = motion.z;
            this.core = core;
            this.maxAge = core ? 20 + RANDOM.nextInt(9) : 34 + RANDOM.nextInt(13);
            this.scale = core ? 0.04f + RANDOM.nextFloat() * 0.032f : 0.07f + RANDOM.nextFloat() * 0.1f;
            this.alphaScale = core ? 1.35f : 0.42f + RANDOM.nextFloat() * 0.5f;
            this.trailX = new double[5];
            this.trailY = new double[5];
            this.trailZ = new double[5];
            for (int i = 0; i < trailX.length; i++) {
                trailX[i] = pos.x;
                trailY[i] = pos.y;
                trailZ[i] = pos.z;
            }
        }

        boolean tick() {
            System.arraycopy(trailX, 0, trailX, 1, trailX.length - 1);
            System.arraycopy(trailY, 0, trailY, 1, trailY.length - 1);
            System.arraycopy(trailZ, 0, trailZ, 1, trailZ.length - 1);
            trailX[0] = x;
            trailY[0] = y;
            trailZ[0] = z;
            x += vx;
            y += vy;
            z += vz;
            vx *= 0.93;
            vy *= 0.93;
            vz *= 0.93;
            return ++age < maxAge;
        }
    }

    public static boolean shouldRenderTrail(Player player) {
        return player.isFallFlying() && GlideBoostRenderer.isBoostActive(player);
    }

    public static void spawnParticles(Player player) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 velocity = new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo);
        Vec3 side = look.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 0.001) {
            side = look.cross(new Vec3(1, 0, 0));
        }
        side = side.normalize();

        for (int i = 0; i < 3; i++) {
            double sideOffset = (RANDOM.nextDouble() - 0.5) * 0.55;
            double verticalOffset = (RANDOM.nextDouble() - 0.5) * 0.16;
            Vec3 pos = player.position()
                    .add(0, verticalOffset, 0)
                    .subtract(look.scale(0.75 + RANDOM.nextDouble() * 0.4))
                    .add(side.scale(sideOffset));
            Vec3 motion = velocity.scale(-0.28)
                    .add(look.scale(-0.06))
                    .add(side.scale((RANDOM.nextDouble() - 0.5) * 0.018))
                    .add(0, (RANDOM.nextDouble() - 0.5) * 0.014, 0);
            PARTICLES.add(new TrailParticle(pos, motion, false));
        }

        if (RANDOM.nextInt(2) == 0) {
            Vec3 pos = player.position()
                    .add(0, (RANDOM.nextDouble() - 0.5) * 0.12, 0)
                    .subtract(look.scale(0.45 + RANDOM.nextDouble() * 0.35))
                    .add(side.scale((RANDOM.nextDouble() - 0.5) * 0.28));
            Vec3 motion = velocity.scale(-0.18)
                    .add(look.scale(-0.04))
                    .add(0, (RANDOM.nextDouble() - 0.5) * 0.01, 0);
            PARTICLES.add(new TrailParticle(pos, motion, true));
        }
    }

    public static void render(PoseStack poseStack, Vec3 camera) {
        PARTICLES.removeIf(p -> !p.tick());
        if (PARTICLES.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        boolean isFirstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();

        for (TrailParticle p : PARTICLES) {
            if (isFirstPerson && camera.distanceToSqr(p.x, p.y, p.z) < 0.55) continue;

            float life = 1.0f - (float) p.age / p.maxAge;
            float fade = (float) Math.sqrt(Math.max(0.0f, life));
            int alpha = (int) (fade * (p.core ? 205 : 120) * p.alphaScale);
            float size = p.scale * (1.0f + (1.0f - life) * 0.25f);
            int r = p.core ? 252 : 238;
            int g = p.core ? 255 : 248;
            int b = 255;
            drawQuad(buffer, matrix, (float) p.x, (float) p.y, (float) p.z, left, up, size, alpha, r, g, b);

            for (int i = 1; i < p.trailX.length; i++) {
                float trail = 1.0f - (float) i / p.trailX.length;
                drawQuad(buffer, matrix, (float) p.trailX[i], (float) p.trailY[i], (float) p.trailZ[i],
                        left, up, size * trail * (p.core ? 0.65f : 0.9f), (int) (alpha * trail * 0.45f), r, g, b);
            }
        }

        MeshData meshData = buffer.build();
        if (meshData != null) BufferUploader.drawWithShader(meshData);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(true);
    }

    public static void clearParticles() {
        PARTICLES.clear();
    }

    public static boolean hasParticles() {
        return !PARTICLES.isEmpty();
    }

    private static void drawQuad(BufferBuilder b, Matrix4f m, float x, float y, float z,
                                 Vector3f l, Vector3f u, float s, int a, int r, int g, int bl) {
        b.addVertex(m, x + (-l.x() - u.x()) * s, y + (-l.y() - u.y()) * s, z + (-l.z() - u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (l.x() - u.x()) * s, y + (l.y() - u.y()) * s, z + (l.z() - u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (l.x() + u.x()) * s, y + (l.y() + u.y()) * s, z + (l.z() + u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (-l.x() + u.x()) * s, y + (-l.y() + u.y()) * s, z + (-l.z() + u.z()) * s).setColor(r, g, bl, a);
    }
}
