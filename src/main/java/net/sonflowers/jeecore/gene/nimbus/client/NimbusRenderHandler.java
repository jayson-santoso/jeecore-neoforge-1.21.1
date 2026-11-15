package net.sonflowers.jeecore.gene.nimbus.client;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.gene.infernal.client.InfernalAerialChargeRenderer;
import net.sonflowers.jeecore.gene.infernal.server.SmokeManager;

/**
 * Hides normal player and hand rendering while a cloud or charge silhouette is active.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public class NimbusRenderHandler {

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (SmokeManager.isSmoking(player.getUUID()) || shouldHideInvisibleExtras(player)) {
            event.setCanceled(true);
            return;
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
