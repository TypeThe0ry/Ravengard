package net.swofty.type.ravengarddungeon.events;

import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.swofty.type.generic.event.EventNodes;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.generic.event.phase.EventPhase;
import net.swofty.type.generic.event.phase.PhasedEvent;
import net.swofty.type.ravengarddungeon.TypeRavengardDungeonLoader;
import net.swofty.type.ravengarddungeon.game.DungeonInstanceRegistry;
import net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer;

public class ActionPlayerDisconnect implements HypixelEventClass {
    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = false, phase = EventPhase.DISCONNECT)
    public void run(PlayerDisconnectEvent event) {
        RavengardDungeonPlayer player = (RavengardDungeonPlayer) event.getPlayer();
        if (DungeonInstanceRegistry.disconnectPlayer(player)) {
            return;
        }
        var game = TypeRavengardDungeonLoader.getGame();
        if (game != null && game.getGameId().equals(player.getGameId())) {
            game.disconnect(player);
        }
    }
}
