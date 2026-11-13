package net.sonflowers.jeecore.core.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.nimbus.network.ToggleGlidePayload;
import net.sonflowers.jeecore.core.network.UseAbilityPayload;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.gene.nimbus.client.NimbusGustClient;

/**
 * Connects client setup and player ticks to local ability input and HUD initialization.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
    }

    private static void onClientTick(PlayerTickEvent.Post event) {
        if (event.getEntity() != net.minecraft.client.Minecraft.getInstance().player
                || !event.getEntity().level().isClientSide) return;
        // Reset HUD state on first tick after respawn/login (client-side detection).
        if (event.getEntity().tickCount == 1) {
            ClientHudState.reset();
        }

        if (KeyBindings.PRIMARY_ABILITY.consumeClick()) {
            PacketDistributor.sendToServer(new UseAbilityPayload(true));
        }
        boolean secondaryPressed = KeyBindings.SECONDARY_ABILITY.consumeClick();
        boolean nimbus = GeneHelper.getPlayerGene(event.getEntity()) == Gene.NIMBUS;
        // Drain repeat events during a hold so none remain queued after key release.
        if (nimbus) while (KeyBindings.SECONDARY_ABILITY.consumeClick()) secondaryPressed = true;
        NimbusGustClient.updateInput(secondaryPressed && nimbus);
        if (secondaryPressed && !nimbus) PacketDistributor.sendToServer(new UseAbilityPayload(false));
        if (KeyBindings.TOGGLE_ABILITY.consumeClick()) {
            if (net.sonflowers.jeecore.core.gene.GeneHelper.getPlayerGene(event.getEntity())
                    == net.sonflowers.jeecore.core.gene.Gene.TERRAGOLEM) {
                PacketDistributor.sendToServer(new net.sonflowers.jeecore.gene.terragolem.network.OpenTerragolemRepairMenuPayload());
            } else {
                PacketDistributor.sendToServer(new ToggleGlidePayload());
            }
        }
    }
}
