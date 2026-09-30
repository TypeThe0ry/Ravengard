package net.swofty.velocity.gamemanager;

import net.swofty.commons.ServerType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyBlockVelocityCrashRecoveryTest {

    @Test
    void dungeonCrashPrioritizesRavengardLobby() {
        LinkedHashSet<ServerType> candidates = CrashRecoveryRouting.candidates(ServerType.RAVENGARD_DUNGEON);

        assertEquals(ServerType.RAVENGARD_LOBBY, candidates.iterator().next());
        assertFalse(candidates.contains(ServerType.RAVENGARD_DUNGEON));
        assertFalse(candidates.contains(ServerType.PROTOTYPE_LOBBY));
    }

    @Test
    void nonRavengardCrashFallsBackOnlyToRavengardLobby() {
        LinkedHashSet<ServerType> candidates = CrashRecoveryRouting.candidates(ServerType.BEDWARS_GAME);

        assertEquals(1, candidates.size());
        assertEquals(ServerType.RAVENGARD_LOBBY, candidates.iterator().next());
        assertFalse(candidates.contains(ServerType.PROTOTYPE_LOBBY));
    }
}
