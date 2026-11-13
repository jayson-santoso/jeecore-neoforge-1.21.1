package net.sonflowers.jeecore.gene.nimbus.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores shared per-player cloud visibility state used by Nimbus gameplay and client rendering.
 */
public class CloudManager {
    private static final Map<UUID, Boolean> CLOUDED_PLAYERS = new ConcurrentHashMap<>();

    public static void setClouded(UUID uuid, boolean clouded) {
        if (clouded) {
            CLOUDED_PLAYERS.put(uuid, true);
        } else {
            CLOUDED_PLAYERS.remove(uuid);
        }
    }

    public static boolean isClouded(UUID uuid) {
        return CLOUDED_PLAYERS.getOrDefault(uuid, false);
    }

    public static boolean hasCloudedPlayers() {
        return !CLOUDED_PLAYERS.isEmpty();
    }

    public static void clearAll() {
        CLOUDED_PLAYERS.clear();
    }
}
