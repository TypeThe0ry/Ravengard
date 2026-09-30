package net.swofty.type.ravengardgeneric.profile;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavengardRunStateTest {
    @Test
    void activeDungeonRunDisablesDefaultInventoryPersistence() {
        UUID player = UUID.randomUUID();
        RavengardProfiles.markInventoryLoaded(player);

        assertTrue(RavengardProfiles.shouldPersistInventory(player));
        RavengardRunState.begin(player, "game-1");
        assertFalse(RavengardProfiles.shouldPersistInventory(player));
        assertFalse(RavengardRunState.finish(player, "game-2"));
        assertFalse(RavengardProfiles.shouldPersistInventory(player));
        assertTrue(RavengardRunState.finish(player, "game-1"));
        assertTrue(RavengardProfiles.shouldPersistInventory(player));
    }

    @Test
    void abortedDungeonRunRestoresDefaultInventoryPersistence() {
        UUID player = UUID.randomUUID();
        RavengardProfiles.markInventoryLoaded(player);

        RavengardRunState.begin(player, "game-1");
        RavengardRunState.abort(player);

        assertTrue(RavengardProfiles.shouldPersistInventory(player));
    }

    @Test
    void inventoryIsNeverPersistedBeforeItWasRestoredThisSession() {
        UUID player = UUID.randomUUID();

        assertFalse(RavengardProfiles.shouldPersistInventory(player));
        RavengardProfiles.markInventoryLoaded(player);
        assertTrue(RavengardProfiles.shouldPersistInventory(player));
        RavengardProfiles.forgetInventoryLoaded(player);
        assertFalse(RavengardProfiles.shouldPersistInventory(player));
    }
}
