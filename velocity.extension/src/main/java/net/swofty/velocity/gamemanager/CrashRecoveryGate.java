package net.swofty.velocity.gamemanager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class CrashRecoveryGate {
    private static final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();

    private CrashRecoveryGate() {
    }

    static boolean begin(UUID playerUuid) {
        return activePlayers.add(playerUuid);
    }

    static void clear(UUID playerUuid) {
        activePlayers.remove(playerUuid);
    }

}
