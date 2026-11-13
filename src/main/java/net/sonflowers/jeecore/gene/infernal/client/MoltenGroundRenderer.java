package net.sonflowers.jeecore.gene.infernal.client;

import net.sonflowers.jeecore.gene.infernal.network.MoltenGroundVfxPayload;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.sonflowers.jeecore.JeeCore;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Renders transient client molten-ground decals using jeecore:textures/effects/molten_ground.png.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = JeeCore.MOD_ID)
public class MoltenGroundRenderer {
    private static final List<MoltenGroundEffect> ACTIVE_EFFECTS = new ArrayList<>();
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "textures/effects/molten_ground.png");
    private static final float EFFECT_SIZE = 6.0f; // Size of the molten ground decal.
    private static final int EFFECT_DURATION_TICKS = 100; // 5 seconds at 20 ticks/second.

    /**
     * Add a molten ground effect at the specified position. Called from MoltenGroundVfxPayload
     * when receiving packet from server.
     */
    public static void addMoltenGround(double x, double y, double z) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        ACTIVE_EFFECTS.add(new MoltenGroundEffect(x, y, z, level.getGameTime()));
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (ACTIVE_EFFECTS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long currentGameTime = mc.level.getGameTime();

        Iterator<MoltenGroundEffect> iterator = ACTIVE_EFFECTS.iterator();
        while (iterator.hasNext()) {
            MoltenGroundEffect effect = iterator.next();
            if (currentGameTime - effect.spawnTime > EFFECT_DURATION_TICKS) {
                iterator.remove();
            }
        }

        if (ACTIVE_EFFECTS.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        Vector3f cameraPos = event.getCamera().getPosition().toVector3f();

        // First render all molten ground textures.
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));
        for (MoltenGroundEffect effect : ACTIVE_EFFECTS) {
            renderMoltenGround(poseStack, vertexConsumer, effect, cameraPos, currentGameTime);
        }
        bufferSource.endBatch(RenderType.entityTranslucent(TEXTURE));
    }

    private static void renderMoltenGround(PoseStack poseStack, VertexConsumer vertexConsumer, 
                                           MoltenGroundEffect effect, Vector3f cameraPos, long currentGameTime) {
        poseStack.pushPose();

        long age = currentGameTime - effect.spawnTime;
        float ageProgress = (float) age / EFFECT_DURATION_TICKS;
        float alpha = 1.0f - ageProgress; 

        // Pulsating glow effect.
        float glowIntensity = 0.8f + 0.2f * (float) Math.sin(currentGameTime * 0.3);
        alpha *= glowIntensity;

        // Pulsating size effect - slightly grows and shrinks.
        float sizePulse = 1.0f + 0.08f * (float) Math.sin(currentGameTime * 0.25);

        float x = (float) (effect.x - cameraPos.x);
        float y = (float) (effect.y - cameraPos.y + 0.16); // High enough to render above snow layers.
        float z = (float) (effect.z - cameraPos.z);

        poseStack.translate(x, y, z);

        poseStack.scale(sizePulse, 1.0f, sizePulse);

        Matrix4f matrix = poseStack.last().pose();
        float halfSize = EFFECT_SIZE / 2.0f;
        int light = 0xF000F0; // Full brightness for glow effect.
        int overlay = OverlayTexture.NO_OVERLAY;

        // Vertex positions (flat quad facing up). UV coordinates for full texture.
        float u0 = 0.0f, u1 = 1.0f;
        float v0 = 0.0f, v1 = 1.0f;

        // Orange/red color for molten ground with enhanced brightness for glow.
        int r = 255, g = 140, b = 70; 

        vertexConsumer.addVertex(matrix, -halfSize, 0.0f, -halfSize).setColor(r, g, b, (int)(alpha * 255)).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(0, 1, 0);
        vertexConsumer.addVertex(matrix, -halfSize, 0.0f, halfSize).setColor(r, g, b, (int)(alpha * 255)).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(0, 1, 0);
        vertexConsumer.addVertex(matrix, halfSize, 0.0f, halfSize).setColor(r, g, b, (int)(alpha * 255)).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(0, 1, 0);
        vertexConsumer.addVertex(matrix, halfSize, 0.0f, -halfSize).setColor(r, g, b, (int)(alpha * 255)).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(0, 1, 0);

        poseStack.popPose();
    }

    private static class MoltenGroundEffect {
        final double x, y, z;
        final long spawnTime;

        MoltenGroundEffect(double x, double y, double z, long spawnTime) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.spawnTime = spawnTime;
        }
    }
}
