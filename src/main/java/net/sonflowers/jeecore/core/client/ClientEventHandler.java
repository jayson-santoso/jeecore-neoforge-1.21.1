package net.sonflowers.jeecore.core.client;

import net.sonflowers.jeecore.gene.infernal.client.InfernalSmokeBarRenderer;
import net.sonflowers.jeecore.gene.terragolem.client.TerragolemOverheatBarRenderer;
import net.sonflowers.jeecore.gene.nimbus.client.DashOverlayRenderer;
import net.sonflowers.jeecore.gene.nimbus.client.GlideBarRenderer;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.sonflowers.jeecore.JeeCore;

/**
 * Registers client key bindings, menu screens, and HUD layers on the mod event bus.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public class ClientEventHandler {
    @SubscribeEvent
    public static void registerMenuScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(net.sonflowers.jeecore.core.registry.ModMenus.TERRAGOLEM_REPAIR.get(),
                net.sonflowers.jeecore.gene.terragolem.client.TerragolemRepairScreen::new);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.PRIMARY_ABILITY);
        event.register(KeyBindings.SECONDARY_ABILITY);
        event.register(KeyBindings.TOGGLE_ABILITY);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "windrunner"),
                new net.sonflowers.jeecore.gene.nimbus.client.WindrunnerHud());
        event.registerAboveAll(
            ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "dash_overlay"), 
            new DashOverlayRenderer());
        event.registerAboveAll(
            ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "glide_bar"),
            new GlideBarRenderer());
        event.registerAboveAll(
            ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "ability_cooldowns"),
            new AbilityCooldownRenderer());
        event.registerAboveAll(
            ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "infernal_smoke_bar"),
            new InfernalSmokeBarRenderer());
        event.registerAboveAll(
            ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "terragolem_overheat_bar"),
            new TerragolemOverheatBarRenderer());
    }
}
