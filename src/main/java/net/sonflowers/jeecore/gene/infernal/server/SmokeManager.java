package net.sonflowers.jeecore.gene.infernal.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores per-player smoke visibility state used by gameplay and client effects.
 */
public class SmokeManager {
    private static final Map<UUID, Boolean> SMOKING_PLAYERS = new ConcurrentHashMap<>();

    public static void setSmoking(UUID uuid, boolean smoking) {
        if (smoking) {
            SMOKING_PLAYERS.put(uuid, true);
        } else {
            SMOKING_PLAYERS.remove(uuid);
        }
    }

    public static boolean isSmoking(UUID uuid) {
        return SMOKING_PLAYERS.getOrDefault(uuid, false);
    }

    public static boolean hasSmokingPlayers() {
        return !SMOKING_PLAYERS.isEmpty();
    }

    public static void clearAll() {
        SMOKING_PLAYERS.clear();
    }
}
