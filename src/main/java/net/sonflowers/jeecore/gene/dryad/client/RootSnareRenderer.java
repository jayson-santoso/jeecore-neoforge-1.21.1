package net.sonflowers.jeecore.gene.dryad.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.sonflowers.jeecore.gene.dryad.visual.RootSnareModel;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Renders client ground debris and sparkles; snare bindings use native display renderers.
 */
public class RootSnareRenderer {
    private static final Map<Integer, RootSnareVisual> ACTIVE_ROOTS = new HashMap<>();
    private static final int GROW_TICKS = RootSnareModel.GROW_TICKS;
    private static final int RELEASE_TICKS = RootSnareModel.WITHDRAW_LEAD_TICKS;
    private static ClientLevel activeLevel;

    public static void start(int entityId, int durationTicks, float width, float height) {
        if (durationTicks <= 0) {
            ACTIVE_ROOTS.remove(entityId);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (activeLevel != mc.level) ACTIVE_ROOTS.clear();
        activeLevel = mc.level;
        ACTIVE_ROOTS.put(entityId, new RootSnareVisual(entityId, mc.level.getGameTime(), durationTicks, width));
    }

    public static boolean hasEffects() {
        return !ACTIVE_ROOTS.isEmpty();
    }

    public static void render(PoseStack poseStack, Vec3 camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != activeLevel) {
            ACTIVE_ROOTS.clear();
            activeLevel = mc.level;
        }
        if (mc.level == null || ACTIVE_ROOTS.isEmpty()) return;
        long tick = mc.level.getGameTime();
        ACTIVE_ROOTS.entrySet().removeIf(entry -> {
            Entity entity = mc.level.getEntity(entry.getKey());
            return entity == null || !entity.isAlive() || tick - entry.getValue().startTick >= entry.getValue().durationTicks;
        });
        if (ACTIVE_ROOTS.isEmpty()) return;
        for (Map.Entry<Integer, RootSnareVisual> entry : ACTIVE_ROOTS.entrySet()) {
            Entity entity = mc.level.getEntity(entry.getKey());
            if (entity != null && camera.distanceToSqr(entity.position()) <= 96 * 96) {
                spawnBurrowingParticles(mc, entity, entry.getValue(), tick);
            }
        }
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        for (Map.Entry<Integer, RootSnareVisual> entry : ACTIVE_ROOTS.entrySet()) {
            Entity entity = mc.level.getEntity(entry.getKey());
            if (entity == null || camera.distanceToSqr(entity.position()) > 96 * 96) continue;
            drawMotes(buffer, matrix, camera, entity, entry.getValue(), tick + partialTick, partialTick);
        }
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.defaultBlendFunc();
    }

    private static Vec3 entityOrigin(Entity entity, float partialTick) {
        return new Vec3(entity.xOld + (entity.getX() - entity.xOld) * partialTick,
                entity.yOld + (entity.getY() - entity.yOld) * partialTick + 0.03,
                entity.zOld + (entity.getZ() - entity.zOld) * partialTick);
    }

    private static float release(RootSnareVisual visual, double age) {
        return (float) Math.max(0, Math.min(1, (age - Math.max(GROW_TICKS,
                visual.durationTicks - RELEASE_TICKS)) / RELEASE_TICKS));
    }

    private static void drawMotes(VertexConsumer consumer, Matrix4f matrix, Vec3 camera, Entity entity,
                                  RootSnareVisual visual, double time, float partialTick) {
        double age = time - visual.startTick;
        Vec3 center = entityOrigin(entity, partialTick).add(0, 0.29, 0);
        float fade = 1 - release(visual, age);
        for (int i = 0; i < visual.motes.length; i++) {
            Mote mote = visual.motes[i];
            double elapsed = age - i * 2;
            if (elapsed < 0) continue;
            double life = elapsed % mote.lifetime;
            double progress = life / mote.lifetime;
            double angle = mote.angle + Math.floor(elapsed / mote.lifetime) * 2.399963;
            double radius = mote.radius + life * mote.outwardSpeed;
            Vec3 point = center.add(Math.cos(angle) * radius,
                    0.025 + life * mote.riseSpeed + life * life * 0.001,
                    Math.sin(angle) * radius);
            float twinkle = (float) (0.5 + 0.5 * Math.sin(life * 2.2 + i * 2.13));
            int alpha = (int) (230 * Math.sin(Math.PI * progress) * fade * (0.45f + twinkle * 0.55f));
            SporeRingRenderer.drawSpark(consumer, matrix, point, camera,
                    mote.size * (0.7f + twinkle * 0.65f), alpha);
        }
    }

    private static void spawnBurrowingParticles(Minecraft mc, Entity entity, RootSnareVisual visual, long tick) {
        if (visual.lastParticleTick == tick) return;
        visual.lastParticleTick = tick;
        long age = tick - visual.startTick;
        if (age < 0 || age >= GROW_TICKS || mc.level == null) return;
        // Emit debris from four ground contacts around the binding.
        for (int i = 0; i < 4; i++) {
            if (age < i * 2 || (age + i) % 3 != 0) continue;
            double angle = visual.rotation + i * Math.PI / 2;
            double x = entity.getX() + Math.cos(angle) * visual.contactRadius;
            double z = entity.getZ() + Math.sin(angle) * visual.contactRadius;
            BlockPos probe = BlockPos.containing(x, entity.getY() + 0.8, z);
            for (int depth = 0; depth < 4; depth++) {
                BlockPos ground = probe.below(depth);
                BlockState state = mc.level.getBlockState(ground);
                if (!state.isSolidRender(mc.level, ground)) continue;
                for (int debris = 0; debris < 2; debris++) {
                    mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
                            x + (mc.level.random.nextDouble() - 0.5) * 0.18, ground.getY() + 1.02,
                            z + (mc.level.random.nextDouble() - 0.5) * 0.18,
                            (mc.level.random.nextDouble() - 0.5) * 0.06,
                            0.04 + mc.level.random.nextDouble() * 0.05,
                            (mc.level.random.nextDouble() - 0.5) * 0.06);
                }
                // One brief leaf-textured pillar burst as each ground contact emerges.
                if ((visual.leafContacts & (1 << i)) == 0) {
                    visual.leafContacts |= 1 << i;
                    for (int leaf = 0; leaf < 3; leaf++) {
                        mc.level.addParticle(new BlockParticleOption(ParticleTypes.DUST_PILLAR,
                                        Blocks.OAK_LEAVES.defaultBlockState()),
                                x + (mc.level.random.nextDouble() - 0.5) * 0.24, ground.getY() + 1.03,
                                z + (mc.level.random.nextDouble() - 0.5) * 0.24, 0, 0.12, 0);
                    }
                }
                break;
            }
        }
    }

    private record Mote(double angle, double radius, double riseSpeed, double outwardSpeed, int lifetime, float size) {}

    private static class RootSnareVisual {
        final long startTick;
        final int durationTicks;
        final float contactRadius;
        final double rotation;
        final Mote[] motes;
        long lastParticleTick = Long.MIN_VALUE;
        int leafContacts;

        RootSnareVisual(int entityId, long startTick, int durationTicks, float width) {
            this.startTick = startTick;
            this.durationTicks = durationTicks;
            float radius = RootSnareModel.horizontalScale(width) * 0.72f;
            contactRadius = RootSnareModel.horizontalScale(width) * 0.8f;
            Random random = new Random(entityId * 73428767L ^ startTick * 912271L);
            rotation = random.nextDouble() * Math.PI * 2;
            motes = new Mote[8];
            for (int i = 0; i < motes.length; i++) {
                motes[i] = new Mote(random.nextDouble() * Math.PI * 2,
                        (0.55 + random.nextDouble() * 0.6) * Math.min(0.85, radius + 0.15),
                        0.035 + random.nextDouble() * 0.035, 0.045 + random.nextDouble() * 0.035,
                        10 + random.nextInt(5), 0.009f + random.nextFloat() * 0.005f);
            }
        }
    }
}
