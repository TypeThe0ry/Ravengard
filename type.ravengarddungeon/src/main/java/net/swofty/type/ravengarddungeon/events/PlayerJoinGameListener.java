package net.swofty.type.ravengarddungeon.events;

import net.swofty.type.game.game.event.PlayerPostJoinGameEvent;
import net.swofty.type.generic.event.EventNodes;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.generic.event.phase.PhasedEvent;
import net.swofty.type.ravengarddungeon.game.RavengardDungeonGame;
import net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer;

public class PlayerJoinGameListener implements HypixelEventClass {

    @PhasedEvent(node = EventNodes.CUSTOM, requireDataLoaded = false)
    public void onPlayerJoinGame(PlayerPostJoinGameEvent event) {
        RavengardDungeonPlayer player = (RavengardDungeonPlayer) event.player();
        int playerIndex = event.currentPlayerCount() - 1;
        RavengardDungeonGame game = (RavengardDungeonGame) event.getGame();
        if (player.getInstance() == game.getInstance()) {
            player.teleport(game.getSpawnPoint(playerIndex));
        }
    }
}
