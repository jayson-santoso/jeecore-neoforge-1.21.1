package net.sonflowers.jeecore.gene.dryad.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.dryad.network.HealPayload;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks healing zones on both logical sides and synchronizes server zone changes to clients.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public class HealManager {
    private static final Map<BlockPos, Long> ACTIVE_ZONES = new HashMap<>();

    public static void addZone(BlockPos pos, long worldTime) {
        if (!ACTIVE_ZONES.containsKey(pos)) {
            ACTIVE_ZONES.put(pos, worldTime);
            PacketDistributor.sendToAllPlayers(new HealPayload(pos, worldTime, false));
        }
    }

    public static void removeZones() {
        ACTIVE_ZONES.clear();
        PacketDistributor.sendToAllPlayers(new HealPayload(BlockPos.ZERO, 0L, true));
    }

    public static void removeZone(BlockPos pos) {
        if (ACTIVE_ZONES.remove(pos) != null) {
            PacketDistributor.sendToAllPlayers(new HealPayload(pos, 0L, true));
        }
    }

    public static Map<BlockPos, Long> getZones() {
        return ACTIVE_ZONES;
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (Map.Entry<BlockPos, Long> entry : ACTIVE_ZONES.entrySet()) {
                PacketDistributor.sendToPlayer(player, new HealPayload(entry.getKey(), entry.getValue(), false));
            }
        }
    }
}
