package net.sonflowers.jeecore.gene.nimbus.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.gene.nimbus.network.WindrunnerAttackPayload;

/**
 * Reports client empty swings to the server so missed attacks still spend Nimbus momentum.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public final class WindrunnerClientEvents {
    @SubscribeEvent public static void emptyAttack(PlayerInteractEvent.LeftClickEmpty event) {
        if (GeneHelper.getPlayerGene(event.getEntity()) == Gene.NIMBUS)
            PacketDistributor.sendToServer(WindrunnerAttackPayload.INSTANCE);
    }
}
