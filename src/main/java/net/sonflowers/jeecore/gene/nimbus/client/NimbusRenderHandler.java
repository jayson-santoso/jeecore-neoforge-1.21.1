package net.sonflowers.jeecore.gene.nimbus.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;
import net.sonflowers.jeecore.gene.infernal.client.InfernalAerialChargeRenderer;
import net.sonflowers.jeecore.gene.infernal.server.SmokeManager;

/**
 * Temporarily adjusts client player equipment rendering while Nimbus glides, then restores it.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public class NimbusRenderHandler {

    private static ItemStack originalChestplate = ItemStack.EMPTY;

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (SmokeManager.isSmoking(player.getUUID()) || shouldHideInvisibleExtras(player)) {
            event.setCanceled(true);
            return;
        }

        // Hide elytra when Nimbus is gliding.
        if (GeneHelper.getPlayerGene(player) == Gene.NIMBUS) {
            PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
            if (data.isGlideToggled() && player.isFallFlying()) {
                // Store original chestplate and temporarily remove it for rendering.
                originalChestplate = player.getItemBySlot(EquipmentSlot.CHEST);
                player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();

        // Restore chestplate after rendering.
        if (GeneHelper.getPlayerGene(player) == Gene.NIMBUS) {
            PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
            if (data.isGlideToggled() && player.isFallFlying() && !originalChestplate.isEmpty()) {
                player.setItemSlot(EquipmentSlot.CHEST, originalChestplate);
                originalChestplate = ItemStack.EMPTY;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && (SmokeManager.isSmoking(player.getUUID()) || shouldHideInvisibleExtras(player))) {
            event.setCanceled(true);
        }
    }

    private static boolean shouldHideInvisibleExtras(Player player) {
        return InfernalAerialChargeRenderer.isPouncing(player.getUUID()) || isNimbusDashInvisible(player)
                || NimbusDodgeRenderer.isDodging(player.getUUID());
    }

    private static boolean isNimbusDashInvisible(Player player) {
        return GeneHelper.getPlayerGene(player) == Gene.NIMBUS && player.hasEffect(MobEffects.INVISIBILITY);
    }
}
