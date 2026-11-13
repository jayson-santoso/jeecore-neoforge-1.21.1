package net.sonflowers.jeecore.gene.dryad.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.dryad.entity.DryadSporeEntity;

/**
 * Renders synchronized Dryad spore entities on the client, including their blink state.
 */
public final class DryadSporeRenderer extends EntityRenderer<DryadSporeEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            JeeCore.MOD_ID, "textures/entity/dryad_spore.png");
    private final DryadSporeModel model;

    public DryadSporeRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new DryadSporeModel(context.bakeLayer(DryadSporeModel.LAYER));
        shadowRadius = 0.18f;
        shadowStrength = 0.3f;
    }

    @Override
    public void render(DryadSporeEntity spore, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        boolean hidden = spore.isBlinkHidden();
        shadowRadius = hidden ? 0 : 0.18f;
        if (hidden) return;
        float age = spore.getSporeAge() + partialTick;
        float phase = (spore.getUUID().hashCode() & 255) / 255.0f * Mth.TWO_PI;
        float remaining = DryadSporeEntity.LIFETIME_TICKS - age;
        float scale = 1.5f * Mth.clamp(remaining / 40.0f, 0.2f, 1.0f);
        pose.pushPose();
        float bob = spore.hasLanded() ? Mth.sin(age * 0.09f + phase) * 0.04f : 0;
        pose.translate(0, 0.30 + bob, 0);
        pose.mulPose(Axis.YP.rotation(age * 0.035f + phase));
        pose.scale(scale, -scale, scale);
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light,
                OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT);
        pose.popPose();
        super.render(spore, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(DryadSporeEntity entity) {
        return TEXTURE;
    }
}
