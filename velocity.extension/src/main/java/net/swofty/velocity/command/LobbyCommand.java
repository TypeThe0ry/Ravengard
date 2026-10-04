package net.swofty.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.swofty.commons.text.Text;
import net.swofty.commons.LobbyDestination;
import net.swofty.commons.ServerType;
import net.swofty.velocity.gamemanager.GameManager;
import net.swofty.velocity.gamemanager.RavengardOnlyRouting;
import net.swofty.velocity.gamemanager.TransferHandler;

public class LobbyCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!(source instanceof Player player)) {
            source.sendMessage(Text.of("<c>该命令只能由玩家使用。"));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length > 1) {
            player.sendMessage(Text.of("<c>用法：/lobby [游戏]"));
            return;
        }

        ServerType currentType = player.getCurrentServer()
            .map(connection -> GameManager.getTypeFromRegisteredServer(connection.getServer()))
            .orElse(null);

        ServerType destination;
        if (args.length == 0) {
            destination = RavengardOnlyRouting.lobbyDestination(
                currentType, LobbyDestination.resolveDefaultDestination(currentType));
        } else {
            destination = RavengardOnlyRouting.lobbyDestination(
                currentType, LobbyDestination.resolveFromAlias(args[0]));
            if (destination == null) {
                player.sendMessage(Text.of("<c>未知的大厅目标。可尝试 /lobby bw、/lobby sw、/lobby bedwars、/lobby skywars。"));
                return;
            }
        }

		if ((!GameManager.hasType(destination) || !GameManager.isAnyEmpty(destination))
			&& GameManager.hasType(ServerType.RAVENGARD_LOBBY)
			&& GameManager.isAnyEmpty(ServerType.RAVENGARD_LOBBY)) {
			destination = ServerType.RAVENGARD_LOBBY;
		}

        new TransferHandler(player).transferTo(destination);
    }
}
