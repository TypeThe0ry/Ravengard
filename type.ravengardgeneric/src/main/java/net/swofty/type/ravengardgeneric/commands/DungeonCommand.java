package net.swofty.type.ravengardgeneric.commands;

import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.number.ArgumentNumber;
import net.swofty.commons.ServerType;
import net.swofty.commons.ServiceType;
import net.swofty.commons.UnderstandableProxyServer;
import net.swofty.commons.text.Text;
import net.swofty.commons.protocol.objects.orchestrator.ChooseGameProtocol;
import net.swofty.commons.protocol.objects.orchestrator.GetServerForMapProtocol;
import net.swofty.commons.protocol.objects.orchestrator.ListGamesProtocol;
import net.swofty.proxyapi.ProxyService;
import net.swofty.type.generic.command.CommandParameters;
import net.swofty.type.generic.command.HypixelCommand;
import net.swofty.type.generic.user.categories.Rank;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

import java.util.concurrent.ThreadLocalRandom;

@CommandParameters(labels = "dungeon",
        description = "天穹余烬地牢管理",
        usage = "/dungeon <join|list|generate [种子] [房间数]>",
        permission = Rank.STAFF, allowsConsole = false)
public class DungeonCommand extends HypixelCommand {
    /** Set by the dungeon server so joining an instance hosted locally skips the proxy hop. */
    public static volatile java.util.function.BiFunction<RavengardPlayer, String, Boolean> localJoin;

    private static final ProxyService ORCHESTRATOR = new ProxyService(ServiceType.ORCHESTRATOR);
    private static final int DEFAULT_ADMIN_ROOMS = 150;

    @Override
    public void registerUsage(MinestomCommand command) {
        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            RavengardPlayer player = (RavengardPlayer) sender;
            player.sendMessage("<e>/dungeon join <7>- 排队进入地牢");
            player.sendMessage("<e>/dungeon join \\<实例> <7>- 俯瞰某个实例");
            player.sendMessage("<e>/dungeon list <7>- 列出所有地牢服务器及其实例");
            player.sendMessage("<e>/dungeon generate [种子] [房间数] <7>- 创建专用实例");
        });

        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            queue((RavengardPlayer) sender, "STANDARD");
        }, ArgumentType.Literal("join"));

        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            list((RavengardPlayer) sender);
        }, ArgumentType.Literal("list"));

        ArgumentNumber<Long> seedArg = ArgumentType.Long("seed");
        ArgumentNumber<Integer> roomsArg = ArgumentType.Integer("rooms").min(3).max(200);

        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            adminGenerate((RavengardPlayer) sender,
                    "ADMIN:" + ThreadLocalRandom.current().nextLong() + ":" + DEFAULT_ADMIN_ROOMS);
        }, ArgumentType.Literal("generate"));

        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            adminGenerate((RavengardPlayer) sender,
                    "ADMIN:" + context.get(seedArg) + ":" + DEFAULT_ADMIN_ROOMS);
        }, ArgumentType.Literal("generate"), seedArg);

        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            adminGenerate((RavengardPlayer) sender,
                    "ADMIN:" + context.get(seedArg) + ":" + context.get(roomsArg));
        }, ArgumentType.Literal("generate"), seedArg, roomsArg);

        var instanceArg = ArgumentType.Word("instance");
        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            adminJoin((RavengardPlayer) sender, context.get(instanceArg));
        }, ArgumentType.Literal("join"), instanceArg);
    }

    private static void adminGenerate(RavengardPlayer player, String mode) {
        player.sendMessage("<7>正在分配地牢实例……");
        GetServerForMapProtocol.GetServerForMapMessage request =
                new GetServerForMapProtocol.GetServerForMapMessage(
                        ServerType.RAVENGARD_DUNGEON, null, mode, 1);
        ORCHESTRATOR.handleRequest(request).thenAccept(response -> {
            if (!(response instanceof GetServerForMapProtocol.GetServerForMapResponse(
                    UnderstandableProxyServer server, String gameId, boolean success, String error))
                    || server == null || gameId == null) {
                String reason = response instanceof GetServerForMapProtocol.GetServerForMapResponse r
                        && r.error() != null ? r.error() : "没有可用的服务器";
                player.sendMessage("<c>无法分配实例：{}", reason);
                return;
            }
            player.sendMessage("<a>实例 <f>{}<a> 已在 <f>{}<a> 创建。无人时 30 秒后自动销毁。",
                    gameId, server.shortName());
            player.sendMessage("<click:run:'/dungeon join {0}'><e><l>[点击加入] <7>或执行 /dungeon join {1}</click>",
                    gameId, gameId.substring(0, 8));
        }).exceptionally(throwable -> {
            player.sendMessage("<c>分配失败：{}", throwable.getMessage());
            return null;
        });
    }

    private static void adminJoin(RavengardPlayer player, String instanceId) {
        if (localJoin != null && localJoin.apply(player, instanceId)) {
            return;
        }
        ORCHESTRATOR.handleRequest(new ListGamesProtocol.ListGamesMessage(ServerType.RAVENGARD_DUNGEON))
                .thenAccept(response -> {
                    if (!(response instanceof ListGamesProtocol.ListGamesResponse listing)
                            || !listing.success()) {
                        player.sendMessage("<c>查找实例失败。");
                        return;
                    }
                    for (ListGamesProtocol.ServerGames server : listing.servers()) {
                        for (ListGamesProtocol.GameSummary game : server.games()) {
                            if (!game.gameId().equals(instanceId)
                                    && !game.gameId().startsWith(instanceId)) continue;
                            UnderstandableProxyServer proxy = new UnderstandableProxyServer(
                                    server.shortName(),
                                    java.util.UUID.fromString(server.serverUuid()),
                                    ServerType.RAVENGARD_DUNGEON, -1,
                                    new java.util.ArrayList<>(), server.maxPlayers(),
                                    server.shortName());
                            ORCHESTRATOR.handleRequest(new ChooseGameProtocol.ChooseGameMessage(
                                    player.getUuid(), proxy, game.gameId())).thenRun(() -> {
                                player.sendMessage("<a>正在把你送往 <f>{}<a>！", server.shortName());
                                player.asProxyPlayer().transferToWithIndication(proxy.uuid());
                            });
                            return;
                        }
                    }
                    player.sendMessage("<c>没有匹配 <f>{}<c> 的实例。", instanceId);
                }).exceptionally(throwable -> {
                    player.sendMessage("<c>查找失败：{}", throwable.getMessage());
                    return null;
                });
    }

    private static void queue(RavengardPlayer player, String mode) {
        player.sendMessage("<7>正在寻找地牢服务器……");
        GetServerForMapProtocol.GetServerForMapMessage request =
                new GetServerForMapProtocol.GetServerForMapMessage(
                        ServerType.RAVENGARD_DUNGEON, null, mode, 1);

        ORCHESTRATOR.handleRequest(request).thenAccept(response -> {
            if (!(response instanceof GetServerForMapProtocol.GetServerForMapResponse(
                    UnderstandableProxyServer server, String gameId, boolean success, String error))
                    || server == null || gameId == null) {
                String reason = response instanceof GetServerForMapProtocol.GetServerForMapResponse r
                        && r.error() != null ? r.error() : "没有可用的服务器";
                player.sendMessage("<c>找不到地牢：{}", reason);
                return;
            }

            ORCHESTRATOR.handleRequest(new ChooseGameProtocol.ChooseGameMessage(
                    player.getUuid(), server, gameId)).thenRun(() -> {
                player.sendMessage("<a>正在把你送往 <f>{}<a>！", server.shortName());
                player.asProxyPlayer().transferToWithIndication(server.uuid());
            }).exceptionally(throwable -> {
                player.sendMessage("<c>地牢登记失败：{}", throwable.getMessage());
                return null;
            });
        }).exceptionally(throwable -> {
            player.sendMessage("<c>地牢搜索失败：{}", throwable.getMessage());
            return null;
        });
    }

    private static void list(RavengardPlayer player) {
        ORCHESTRATOR.handleRequest(new ListGamesProtocol.ListGamesMessage(ServerType.RAVENGARD_DUNGEON))
                .thenAccept(response -> {
                    if (!(response instanceof ListGamesProtocol.ListGamesResponse listing)
                            || !listing.success()) {
                        player.sendMessage("<c>无法列出地牢服务器。");
                        return;
                    }
                    if (listing.servers().isEmpty()) {
                        player.sendMessage("<c>没有在线的地牢服务器。");
                        return;
                    }
                    for (ListGamesProtocol.ServerGames server : listing.servers()) {
                        player.sendMessage("<e>{} <7>- <f>{}<7>/<f>{} 名玩家<7>，<f>{}<7> 个空闲实例位",
                                server.shortName(), server.onlinePlayers(), server.maxPlayers(),
                                server.remainingGameSlots() == null ? "?" : server.remainingGameSlots());
                        for (ListGamesProtocol.GameSummary game : server.games()) {
                            boolean dying = game.map() != null && game.map().startsWith("empty:");
                            Text line = Text.of("  <8>- <f>{} <7>{} <f>{} 名玩家 ",
                                            game.gameId().substring(0, 8), game.gameTypeName(), game.playerCount())
                                    .append(game.acceptingJoins() ? "<a>开放" : "<c>关闭")
                                    .appendIf(dying, " <c>（{} 秒后销毁）", dying ? game.map().substring(6) : "");
                            player.sendMessage(line);
                        }
                        if (server.games().isEmpty()) {
                            player.sendMessage("  <8>- <7>没有实例");
                        }
                    }
                }).exceptionally(throwable -> {
                    player.sendMessage("<c>列表获取失败：{}", throwable.getMessage());
                    return null;
                });
    }
}
