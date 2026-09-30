package net.swofty.type.ravengarddungeon.events;

import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.swofty.type.generic.event.EventNodes;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.generic.event.phase.EventPhase;
import net.swofty.type.generic.event.phase.PhasedEvent;
import net.swofty.type.ravengarddungeon.game.DungeonInstanceRegistry;
import net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer;

public class ActionPlayerForfeitRun implements HypixelEventClass {
    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = false, phase = EventPhase.GAMEPLAY)
    public void run(PlayerDisconnectEvent event) {
        DungeonInstanceRegistry.forfeitRun((RavengardDungeonPlayer) event.getPlayer());
    }
}
