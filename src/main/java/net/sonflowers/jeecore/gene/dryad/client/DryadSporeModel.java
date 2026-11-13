package net.sonflowers.jeecore.gene.dryad.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;
import net.sonflowers.jeecore.JeeCore;

/**
 * Defines client spore model geometry and renders it with the supplied animation state.
 */
public final class DryadSporeModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "dryad_spore"), "main");
    private final ModelPart core;
    private final ModelPart husk;

    public DryadSporeModel(ModelPart root) {
        core = root.getChild("core");
        husk = root.getChild("husk");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3), PartPose.ZERO);
        var husk = root.addOrReplaceChild("husk", CubeListBuilder.create().texOffs(0, 8)
                .addBox(-1, 1.5f, -1, 2, 1, 2), PartPose.ZERO);
        for (int i = 0; i < 3; i++) {
            husk.addOrReplaceChild("fin_" + i, CubeListBuilder.create().texOffs(12, 0)
                    .addBox(-1.5f, -2.5f, 1, 3, 5, 0),
                    PartPose.rotation(-0.3f, (float) (i * Math.PI * 2 / 3), 0));
        }
        return LayerDefinition.create(mesh, 32, 32);
    }

    public void render(PoseStack pose, VertexConsumer buffer, int light, int overlay, int coreLight) {
        husk.render(pose, buffer, light, overlay);
        core.render(pose, buffer, coreLight, overlay);
    }
}
