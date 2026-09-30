package net.swofty.type.ravengardgeneric.profile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RavengardRunState {
    private static final Map<UUID, String> ACTIVE_RUNS = new ConcurrentHashMap<>();

    private RavengardRunState() {
    }

    public static void begin(UUID player, String gameId) {
        if (player == null || gameId == null || gameId.isBlank()) return;
        ACTIVE_RUNS.put(player, gameId);
    }

    public static boolean isActive(UUID player) {
        return player != null && ACTIVE_RUNS.containsKey(player);
    }

    public static boolean isActive(UUID player, String gameId) {
        return player != null && gameId != null && gameId.equals(ACTIVE_RUNS.get(player));
    }

    public static boolean finish(UUID player, String gameId) {
        return player != null && gameId != null && ACTIVE_RUNS.remove(player, gameId);
    }

    public static void abort(UUID player) {
        if (player != null) ACTIVE_RUNS.remove(player);
    }
}
