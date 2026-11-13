package net.sonflowers.jeecore.core.ability;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.gene.nimbus.server.DashEffectManager;
import net.sonflowers.jeecore.gene.dryad.server.DryadPassiveHandler;
import net.sonflowers.jeecore.gene.infernal.server.InfernalChargeManager;
import net.sonflowers.jeecore.gene.infernal.server.InfernalAerialChargeManager;
import net.sonflowers.jeecore.gene.infernal.server.InfernalPassiveHandler;
import net.sonflowers.jeecore.gene.infernal.server.InfernalRageManager;
import net.sonflowers.jeecore.gene.infernal.server.InfernalSmokeManager;
import net.sonflowers.jeecore.gene.nimbus.server.NimbusPassiveHandler;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemOverheatManager;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemPassiveHandler;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemReadyUpManager;

/**
 * Advances gene resources and ability state during server player ticks; periodically synchronizes
 * cooldowns.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public class AbilityTickHandler {
    private static final int SYNC_INTERVAL = 40; // Sync every 2 seconds (40 ticks).

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            DashEffectManager.tick(player);
            InfernalPassiveHandler.tick(player);

            Gene gene = GeneHelper.getPlayerGene(player);
            if (gene != Gene.NIMBUS && player.hasData(net.sonflowers.jeecore.core.gene.GeneDataAttachment.WINDRUNNER)) {
                var windrunner = player.getData(net.sonflowers.jeecore.core.gene.GeneDataAttachment.WINDRUNNER);
                if (windrunner.initialized || windrunner.rules.momentum() > 0) {
                    net.sonflowers.jeecore.gene.nimbus.server.NimbusWindrunnerManager.clear(player);
                }
            }
            switch (gene) {
                case NIMBUS -> NimbusPassiveHandler.tick(player);
                case INFERNAL -> {
                    InfernalSmokeManager.tick(player);
                    InfernalChargeManager.tick(player);
                    InfernalRageManager.tick(player);
                    InfernalAerialChargeManager.tick(player);
                }
                case DRYAD -> DryadPassiveHandler.tick(player);
                case TERRAGOLEM -> {
                    TerragolemPassiveHandler.tick(player);
                    TerragolemReadyUpManager.tick(player);
                    TerragolemOverheatManager.tick(player);
                }
                default -> {}
            }

            // Periodically sync cooldowns to keep HUD updated and handle any missed syncs.
            if (player.tickCount % SYNC_INTERVAL == 0) {
                net.sonflowers.jeecore.core.ability.AbilityHandler.syncCooldowns(player);
            }
        }
    }

}
