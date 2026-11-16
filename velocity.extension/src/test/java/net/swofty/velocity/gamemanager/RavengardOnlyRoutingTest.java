package net.swofty.velocity.gamemanager;

import net.swofty.commons.ServerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RavengardOnlyRoutingTest {
    @Test
    void missingLobbyDestinationIsMappedToRavengard() {
        assertEquals(ServerType.RAVENGARD_LOBBY, RavengardOnlyRouting.lobbyDestination(null, null));
        assertEquals(ServerType.RAVENGARD_LOBBY,
            RavengardOnlyRouting.lobbyDestination(ServerType.RAVENGARD_DUNGEON, null));
    }

    @Test
    void explicitRavengardDestinationIsPreserved() {
        assertEquals(ServerType.RAVENGARD_LOBBY,
            RavengardOnlyRouting.lobbyDestination(ServerType.RAVENGARD_DUNGEON, ServerType.RAVENGARD_LOBBY));
        assertEquals(ServerType.RAVENGARD_DUNGEON,
            RavengardOnlyRouting.lobbyDestination(ServerType.RAVENGARD_LOBBY, ServerType.RAVENGARD_DUNGEON));
    }

    @Test
    void afkRoutingAlwaysEndsInTheRavengardLobby() {
        assertEquals(ServerType.RAVENGARD_LOBBY,
            RavengardOnlyRouting.afkOrigin(ServerType.RAVENGARD_LOBBY, null));
        assertEquals(ServerType.RAVENGARD_LOBBY,
            RavengardOnlyRouting.afkOrigin(null, ServerType.RAVENGARD_DUNGEON));
    }
}
