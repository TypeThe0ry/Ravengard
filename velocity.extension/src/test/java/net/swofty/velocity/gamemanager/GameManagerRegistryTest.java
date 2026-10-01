package net.swofty.velocity.gamemanager;

import net.swofty.commons.ServerType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameManagerRegistryTest {
    private static final ServerType TYPE = ServerType.RAVENGARD_LOBBY;

    @AfterEach
    void clear() {
        GameManager.getServers().remove(TYPE);
    }

    @Test
    void filteringTheServersOfATypeLeavesTheRegistryIntact() {
        GameManager.GameServer lobby = new GameManager.GameServer("L1a", "L1a", UUID.randomUUID(), null, 20);
        GameManager.getServers().put(TYPE, new CopyOnWriteArrayList<>(List.of(lobby)));

        List<GameManager.GameServer> candidates = GameManager.getFromType(TYPE);
        candidates.removeIf(server -> true);

        assertTrue(candidates.isEmpty());
        assertTrue(GameManager.hasType(TYPE));
        assertEquals(lobby, GameManager.getFromUUID(lobby.internalID()));
    }

    @Test
    void aTypeWithNoServersIsAnEmptyListRatherThanNull() {
        assertTrue(GameManager.getFromType(TYPE).isEmpty());
        assertFalse(GameManager.isAnyEmpty(TYPE));
    }
}
