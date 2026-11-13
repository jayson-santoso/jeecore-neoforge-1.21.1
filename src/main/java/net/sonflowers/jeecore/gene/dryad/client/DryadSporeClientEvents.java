package net.sonflowers.jeecore.gene.dryad.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.registry.ModEntities;

/**
 * Registers the Dryad spore entity renderer and model layer on the client mod event bus.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public final class DryadSporeClientEvents {
    private DryadSporeClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DRYAD_SPORE.get(), DryadSporeRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DryadSporeModel.LAYER, DryadSporeModel::createLayer);
    }
}
