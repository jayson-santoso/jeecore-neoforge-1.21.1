package net.sonflowers.jeecore.gene.infernal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;
import net.sonflowers.jeecore.gene.infernal.server.RageManager;

/**
 * Updates the local rage heartbeat; accumulated phase keeps combat extensions from restarting the
 * pulse.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public final class RageHeartbeat {
    private static double beatPhase = 1.0;
    private static SimpleSoundInstance heartbeat;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isAlive()
                || GeneHelper.getPlayerGene(mc.player) != Gene.INFERNAL
                || !RageManager.isRaging(mc.player.getUUID())) {
            beatPhase = 1.0;
            if (heartbeat != null) {
                mc.getSoundManager().stop(heartbeat);
                heartbeat = null;
            }
            return;
        }
        if (mc.isPaused()) return;

        PlayerGeneData data = mc.player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        float remaining = data.getMaxRageBar() > 0
                ? (float) data.getRageBar() / data.getMaxRageBar() : 0.0f;
        remaining = Math.max(0.0f, Math.min(1.0f, remaining));
        // Accumulate phase so combat extensions smoothly speed the pulse back up.
        double intervalTicks = 22.0 - 15.0 * remaining;
        beatPhase += 1.0 / intervalTicks;
        if (beatPhase >= 1.0) {
            beatPhase -= 1.0;
            if (heartbeat != null) mc.getSoundManager().stop(heartbeat);
            heartbeat = SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT,
                    0.8f + 0.2f * remaining, 0.55f + 0.2f * remaining);
            mc.getSoundManager().play(heartbeat);
        }
    }
}
