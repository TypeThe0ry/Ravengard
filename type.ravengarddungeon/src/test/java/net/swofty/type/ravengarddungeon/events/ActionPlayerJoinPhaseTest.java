package net.swofty.type.ravengarddungeon.events;

import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.swofty.type.generic.event.phase.EventPhase;
import net.swofty.type.generic.event.phase.PhasedEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ActionPlayerJoinPhaseTest {
    @Test
    void dynamicDungeonIsReservedBeforeResourcePackLoading() throws Exception {
        var method = ActionPlayerJoin.class.getDeclaredMethod("run", AsyncPlayerConfigurationEvent.class);
        var annotation = method.getAnnotation(PhasedEvent.class);

        assertEquals(EventPhase.CONNECT, annotation.phase());
        assertFalse(annotation.requireDataLoaded());
    }
}
