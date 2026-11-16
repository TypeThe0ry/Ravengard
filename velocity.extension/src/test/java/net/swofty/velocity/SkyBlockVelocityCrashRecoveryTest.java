package net.swofty.velocity.gamemanager;

import net.swofty.commons.ServerType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SkyBlockVelocityCrashRecoveryTest {

    @Test
    void dungeonCrashPrioritizesRavengardLobby() {
        LinkedHashSet<ServerType> candidates = CrashRecoveryRouting.candidates(ServerType.RAVENGARD_DUNGEON);

        assertEquals(ServerType.RAVENGARD_LOBBY, candidates.iterator().next());
        assertFalse(candidates.contains(ServerType.RAVENGARD_DUNGEON));
    }

    @Test
    void lobbyCrashFallsBackOnlyToRavengardLobby() {
        LinkedHashSet<ServerType> candidates = CrashRecoveryRouting.candidates(ServerType.RAVENGARD_LOBBY);

        assertEquals(1, candidates.size());
        assertEquals(ServerType.RAVENGARD_LOBBY, candidates.iterator().next());
    }
}
