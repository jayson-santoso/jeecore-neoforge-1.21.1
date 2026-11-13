package net.sonflowers.jeecore.gene.infernal.server;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.infernal.network.RagePayload;
import net.sonflowers.jeecore.gene.infernal.network.RageVisualStatePayload;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shares per-player rage timing and visual intensity across logical sides; synchronizes active
 * rage to joining players.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public class RageManager {
    private static final Map<UUID, Long> RAGING_PLAYERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> RAGE_PERCENTAGES = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // Sync all currently raging players to the newly joined player.
        if (event.getEntity() instanceof ServerPlayer sp) {
            for (Map.Entry<UUID, Long> entry : RAGING_PLAYERS.entrySet()) {
                if (isRaging(entry.getKey())) {
                    int remainingSeconds = entry.getValue() == Long.MAX_VALUE
                            ? 0
                            : (int) (getRemainingMillis(entry.getKey()) / 1000);
                    PacketDistributor.sendToPlayer(sp, new RagePayload(entry.getKey(), remainingSeconds));
                    PacketDistributor.sendToPlayer(sp, new RageVisualStatePayload(entry.getKey(), getRagePercent(entry.getKey())));
                }
            }
        }
    }

    public static void startRage(UUID uuid, int durationSeconds) {
        long expiry = durationSeconds <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + (durationSeconds * 1000L);
        RAGING_PLAYERS.put(uuid, expiry);
        RAGE_PERCENTAGES.put(uuid, 1.0f);
    }

    public static void endRage(UUID uuid) {
        RAGING_PLAYERS.remove(uuid);
        RAGE_PERCENTAGES.remove(uuid);
    }

    public static boolean isRaging(UUID uuid) {
        Long expiry = RAGING_PLAYERS.get(uuid);
        if (expiry == null) return false;
        if (expiry == Long.MAX_VALUE) return true;
        if (System.currentTimeMillis() > expiry) {
            if (RAGING_PLAYERS.remove(uuid, expiry)) {
                RAGE_PERCENTAGES.remove(uuid);
            }
            return false;
        }
        return true;
    }

    public static boolean hasRagingPlayers() {
        return !RAGING_PLAYERS.isEmpty();
    }

    public static long getRemainingMillis(UUID uuid) {
        Long expiry = RAGING_PLAYERS.get(uuid);
        if (expiry == null) return 0;
        if (expiry == Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.max(0, expiry - System.currentTimeMillis());
    }

    public static void setRagePercent(UUID uuid, float percent) {
        RAGE_PERCENTAGES.put(uuid, Math.max(0.0f, Math.min(1.0f, percent)));
    }

    public static float getRagePercent(UUID uuid) {
        return RAGE_PERCENTAGES.getOrDefault(uuid, isRaging(uuid) ? 1.0f : 0.0f);
    }
}
