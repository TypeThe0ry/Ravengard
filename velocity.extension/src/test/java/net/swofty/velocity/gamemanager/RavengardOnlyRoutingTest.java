package net.swofty.velocity.gamemanager;

import net.swofty.commons.ServerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RavengardOnlyRoutingTest {
    @Test
    void nullAndPrototypeLobbyDestinationsAreMappedToRavengard() {
        assertEquals(ServerType.RAVENGARD_LOBBY, RavengardOnlyRouting.lobbyDestination(null, null));
        assertEquals(ServerType.RAVENGARD_LOBBY,
            RavengardOnlyRouting.lobbyDestination(ServerType.BEDWARS_GAME, ServerType.PROTOTYPE_LOBBY));
    }

    @Test
    void explicitRavengardDestinationIsPreserved() {
        assertEquals(ServerType.RAVENGARD_LOBBY,
            RavengardOnlyRouting.lobbyDestination(ServerType.RAVENGARD_DUNGEON, ServerType.RAVENGARD_LOBBY));
    }

    @Test
    void afkRoutingNeverFallsBackToPrototypeLobby() {
        assertEquals(ServerType.RAVENGARD_LOBBY,
            RavengardOnlyRouting.afkOrigin(ServerType.PROTOTYPE_LOBBY, null));
        assertEquals(ServerType.RAVENGARD_LOBBY,
            RavengardOnlyRouting.afkOrigin(null, ServerType.RAVENGARD_DUNGEON));
    }
}
