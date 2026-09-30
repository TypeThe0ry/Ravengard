package net.swofty.velocity.gamemanager;

import net.swofty.commons.ServerType;

import java.util.LinkedHashSet;

public final class CrashRecoveryRouting {
    private CrashRecoveryRouting() {
    }

    public static LinkedHashSet<ServerType> candidates(ServerType serverType) {
        LinkedHashSet<ServerType> candidates = new LinkedHashSet<>();
        candidates.add(ServerType.RAVENGARD_LOBBY);
        return candidates;
    }
}
