package net.swofty.type.prototypelobby.commands;

import net.minestom.server.command.builder.arguments.ArgumentType;
import net.swofty.commons.ServerType;
import net.swofty.type.generic.command.CommandParameters;
import net.swofty.type.generic.command.HypixelCommand;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.generic.user.categories.Rank;
import net.swofty.type.lobby.LobbyOrchestratorConnector;

@CommandParameters(
        labels = "play",
        description = "Join a game queue",
        usage = "/play ravengard",
        permission = Rank.DEFAULT,
        allowsConsole = false
)
public final class PlayCommand extends HypixelCommand {
    @Override
    public void registerUsage(MinestomCommand command) {
        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            if (!(sender instanceof HypixelPlayer player)) return;
            new LobbyOrchestratorConnector(player)
                    .sendToGame(ServerType.RAVENGARD_DUNGEON, "STANDARD");
        }, ArgumentType.Literal("ravengard"));
    }
}
