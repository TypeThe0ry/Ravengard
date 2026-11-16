package net.swofty.velocity.gamemanager;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import com.velocitypowered.api.proxy.Player;
import net.swofty.commons.ServerType;
import net.swofty.commons.text.Text;
import net.swofty.velocity.gamemanager.impl.LowestPlayerCount;
import net.swofty.velocity.testflow.TestFlowManager;
import org.jetbrains.annotations.Nullable;
import org.tinylog.Logger;

import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BalanceConfigurations {

    private static final BalanceConfiguration LOWEST_PLAYER_COUNT = new LowestPlayerCount();
    private static final List<BalanceConfiguration> DEFAULT_CHAIN = List.of(LOWEST_PLAYER_COUNT);

    public static final Map<ServerType, List<BalanceConfiguration>> CONFIGURATIONS = Map.ofEntries(
            Map.entry(ServerType.RAVENGARD_LOBBY, DEFAULT_CHAIN)
    );

    public static @Nullable GameManager.GameServer getServerFor(Player player, ServerType type) {
        final boolean inTestFlow = TestFlowManager.isPlayerInTestFlow(player.getUsername());
        if (inTestFlow) {
            player.sendMessage(Text.of("<e>你当前处于网络隔离的测试流程中，负载均衡将仅限于测试流程服务器！"));
            player.sendMessage(Text.of("<8>正在执行测试流程 {}……",
                    TestFlowManager.getTestFlowForPlayer(player.getUsername()).getName()));
        }

        try {
            for (BalanceConfiguration configuration : CONFIGURATIONS.getOrDefault(type, DEFAULT_CHAIN)) {
                List<GameManager.GameServer> serversToConsider = GameManager.getFromType(type);
                if (inTestFlow) {
                    serversToConsider.removeIf(server -> !isEligibleForTestFlowPlayer(server, player));
                } else {
                    serversToConsider.removeIf(server -> !isEligibleForRegularPlayer(server));
                }

                GameManager.GameServer server = configuration.getServer(player, serversToConsider);
                if (server != null) {
                    if (inTestFlow) {
                        player.sendMessage(Text.of("<8>已为你的测试流程完成服务器管理器覆盖。"));
                    }
                    return server;
                }
            }
            return null;
        } catch (Exception e) {
            Logger.error(e, "Error in trying to balance type {} for player {}",
                    type.name(), player.getUsername());
            throw e;
        }
    }

    private static boolean hasCapacity(GameManager.GameServer server) {
        return server.maxPlayers() > server.registeredServer().getPlayersConnected().size();
    }

    private static boolean isEligibleForRegularPlayer(GameManager.GameServer server) {
        return hasCapacity(server) && !TestFlowManager.isServerInTestFlow(server.internalID());
    }

    private static boolean isEligibleForTestFlowPlayer(GameManager.GameServer server, Player player) {
        if (!hasCapacity(server)) return false;
        if (!TestFlowManager.isServerInTestFlow(server.internalID())) return false;

        TestFlowManager.ProxyTestFlowInstance instance =
                TestFlowManager.getFromServerUUID(server.internalID());
        return instance != null && instance.hasPlayer(player.getUsername());
    }
}
