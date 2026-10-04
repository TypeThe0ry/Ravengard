package net.swofty.velocity.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.swofty.velocity.gamemanager.GameManager;
import net.swofty.velocity.gamemanager.GameManager.GameServer;
import net.swofty.commons.ServerType;
import net.swofty.commons.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ServerStatusCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
        invocation.source().sendMessage(Text.of("***** 服务器状态 *****"));

        Map<ServerType, List<GameServer>> serverMap = GameManager.getServers();
        if (serverMap.isEmpty()) {
            invocation.source().sendMessage(Text.of("当前没有已注册的服务器。"));
            return;
        }

        for (Map.Entry<ServerType, List<GameServer>> entry : serverMap.entrySet()) {
            ServerType type = entry.getKey();
            List<GameServer> gameServers = entry.getValue();

            invocation.source().sendMessage(Text.of("{}:", type.name()));

            if (gameServers.isEmpty()) {
                invocation.source().sendMessage(Text.of(" - 当前没有在线的此类服务器。"));
                continue;
            }

            for (GameServer server : gameServers) {
                invocation.source().sendMessage(Text.of(" - {}（ID：{}，端口：{}，玩家数：{}）",
                        server.displayName(),
                        server.internalID(),
                        server.registeredServer().getServerInfo().getAddress().getPort(),
                        server.registeredServer().getPlayersConnected().size()
                ));
            }
        }
    }
}
