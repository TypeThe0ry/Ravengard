package net.swofty.type.ravengardgeneric.event.actions.player;

import net.swofty.commons.ServerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavengardConfigurationTransferTest {
    @Test
    void ravengardInternalTransfersReuseTheExistingRegistrySet() {
        assertTrue(ActionPlayerJoin.isRavengardInternalTransfer(
                ServerType.RAVENGARD_LOBBY, ServerType.RAVENGARD_DUNGEON));
        assertTrue(ActionPlayerJoin.isRavengardInternalTransfer(
                ServerType.RAVENGARD_DUNGEON, ServerType.RAVENGARD_LOBBY));
        assertTrue(ActionPlayerJoin.isRavengardInternalTransfer(
                ServerType.RAVENGARD_DUNGEON, ServerType.RAVENGARD_DUNGEON));
    }

    @Test
    void firstEntryFromOutsideRavengardSendsTheFullRegistrySet() {
        assertFalse(ActionPlayerJoin.isRavengardInternalTransfer(
                null, ServerType.RAVENGARD_LOBBY));
        assertFalse(ActionPlayerJoin.isRavengardInternalTransfer(
                ServerType.PROTOTYPE_LOBBY, ServerType.RAVENGARD_LOBBY));
        assertFalse(ActionPlayerJoin.isRavengardInternalTransfer(
                ServerType.MAIN_LOBBY, ServerType.RAVENGARD_DUNGEON));
        assertFalse(ActionPlayerJoin.isRavengardInternalTransfer(
                ServerType.RAVENGARD_LOBBY, ServerType.PROTOTYPE_LOBBY));
    }
}
