package net.swofty.velocity.redis.listeners;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.swofty.commons.ServerType;
import net.swofty.commons.UnderstandableProxyServer;
import net.swofty.commons.protocol.RedisProtocol;
import net.swofty.commons.protocol.objects.proxy.from.RunEventProtocol;
import net.swofty.commons.protocol.objects.proxy.from.TeleportProtocol;
import net.swofty.commons.protocol.objects.proxy.to.PlayerHandlerProtocol;
import net.swofty.commons.redis.RedisClient;
import net.swofty.commons.redis.RedisMessageContext;
import net.swofty.commons.redis.RedisMessageHandler;
import net.swofty.commons.text.Text;
import net.swofty.redisapi.api.requests.DataRequest;
import net.swofty.velocity.SkyBlockVelocity;
import net.swofty.velocity.gamemanager.GameManager;
import net.swofty.velocity.gamemanager.RavengardOnlyRouting;
import net.swofty.velocity.gamemanager.TransferHandler;
import net.swofty.velocity.presence.PresencePublisher;
import org.json.JSONObject;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ListenerPlayerHandler implements RedisMessageHandler<
        PlayerHandlerProtocol.Request,
        PlayerHandlerProtocol.Response> {

    private static final PlayerHandlerProtocol.Response EMPTY = new PlayerHandlerProtocol.Response(Map.of(), true, null);
    private static final long TRANSFER_TIMEOUT_SECONDS = 15;

    @Override
    public RedisProtocol<PlayerHandlerProtocol.Request, PlayerHandlerProtocol.Response> protocol() {
        return new PlayerHandlerProtocol();
    }

    @Override
    public PlayerHandlerProtocol.Response handle(PlayerHandlerProtocol.Request message, RedisMessageContext context) {
        UUID uuid = UUID.fromString(message.uuid());
        PlayerHandlerProtocol.Action action = message.action();
        Map<String, Object> data = message.data() != null ? message.data() : Map.of();

        Optional<Player> potentialPlayer = SkyBlockVelocity.getServer().getPlayer(uuid);
        if (potentialPlayer.isEmpty()) {
            if (action == PlayerHandlerProtocol.Action.IS_ONLINE) {
                return new PlayerHandlerProtocol.Response(Map.of("isOnline", false), true, null);
            }
            return EMPTY;
        }
        if (action == PlayerHandlerProtocol.Action.IS_ONLINE) {
            Player player = potentialPlayer.get();
            publishPresence(player, true);
            return new PlayerHandlerProtocol.Response(Map.of("isOnline", true), true, null);
        }
        Player player = potentialPlayer.get();
        Optional<ServerConnection> potentialServer = player.getCurrentServer();

        switch (action) {
            case RESOLVE_TRANSFER -> {
                ServerType type = ServerType.valueOf((String) data.get("type"));
                if (!GameManager.hasType(type) || !GameManager.isAnyEmpty(type)) {
                    return new PlayerHandlerProtocol.Response(Map.of(), false,
                            "当前没有可用的 " + type.formatName() + " 服务器");
                }
                GameManager.GameServer server = net.swofty.velocity.gamemanager.BalanceConfigurations.getServerFor(player, type);
                if (server == null || !server.hasEmptySlots()) {
                    return new PlayerHandlerProtocol.Response(Map.of(), false, "没有可用的目标服务器");
                }
                return new PlayerHandlerProtocol.Response(
                        Map.of("server_uuid", server.internalID().toString()), true, null);
            }
            case GET_SERVER -> {
                UUID playersServer = UUID.fromString(potentialServer.get().getServer().getServerInfo().getName());
                GameManager.GameServer serverInfo = GameManager.getFromUUID(playersServer);
                if (serverInfo == null) {
                    return EMPTY;
                }

                publishPresence(player, true);
                UnderstandableProxyServer ups = new UnderstandableProxyServer(
                    serverInfo.displayName(),
                    serverInfo.internalID(),
                    GameManager.getTypeFromRegisteredServer(serverInfo.registeredServer()),
                    serverInfo.registeredServer().getServerInfo().getAddress().getPort(),
                    serverInfo.registeredServer().getPlayersConnected().stream().map(Player::getUniqueId).toList(),
                    serverInfo.maxPlayers(),
                    serverInfo.shortDisplayName()
                );
                return new PlayerHandlerProtocol.Response(Map.of("server", ups.toJSON().toMap()), true, null);
            }
            case TRANSFER_WITH_UUID -> {
                UUID server = UUID.fromString((String) data.get("server_uuid"));
                GameManager.GameServer serverInfo = GameManager.getFromUUID(server);
                TransferHandler transferHandler = new TransferHandler(player);

                if (serverInfo == null) {
                    player.sendMessage(Text.of(
                        "<c>在网络中查找服务器时出现问题，请稍后再试。"
                    ));
                    return new PlayerHandlerProtocol.Response(Map.of(), false,
                            "无法在网络中找到目标服务器");
                }

                player.sendMessage(Text.of("<7>正在传送到服务器 {}……", serverInfo.displayName()));

                if (!serverInfo.hasEmptySlots()) {
                    player.sendMessage(Text.of(
                        "<c>尝试连接到 {}，但没有空余位置。请稍后再试。",
                        serverInfo.displayName()
                    ));
                    return new PlayerHandlerProtocol.Response(Map.of(), false, "目标服务器已满");
                }

                if (data.get("document") instanceof String document) {
                    JSONObject snapshot = new JSONObject(document);
                    JSONObject payload = new JSONObject()
                            .put("uuid", uuid.toString())
                            .put("account_document", snapshot.getString("account_document"));
                    if (snapshot.has("profile_document")) {
                        payload.put("profile_document", snapshot.getString("profile_document"));
                    }
                    var cacheResponse = new DataRequest(server.toString(), "player-transfer-data", payload).await().join();
                    if (cacheResponse.data() == null || !cacheResponse.data().optBoolean("cached")) {
                        return new PlayerHandlerProtocol.Response(Map.of(), false,
                                "目标服务器未确认你的数据");
                    }
                }

                transferHandler.addToDisregard();
                try {
                    transferHandler.transferTo(serverInfo.registeredServer())
                        .get(TRANSFER_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
                    return new PlayerHandlerProtocol.Response(Map.of(), false,
                        "传送失败：" + cause.getMessage());
                } finally {
                    transferHandler.removeFromDisregard();
                }
                return new PlayerHandlerProtocol.Response(Map.of(), true, null);
            }
            case TRANSFER -> {
                ServerType type = ServerType.valueOf((String) data.get("type"));
                if (!GameManager.hasType(type)
                    || new TransferHandler(player).isInLimbo()
                    || !GameManager.isAnyEmpty(type)) {
                    player.sendMessage(Text.of(
                        "<c>尝试传送到 {} 服务器，但没有空余位置，请稍后再试。",
                        type.formatName()
                    ));
                    return EMPTY;
                }
                new TransferHandler(player).transferTo(type);
            }
            case LIMBO -> {
                TransferHandler transferHandler = new TransferHandler(player);
                String reason = data.containsKey("reason") ? (String) data.get("reason") : "";

                if ("AFK".equalsIgnoreCase(reason)) {
                    ServerType originType = resolveAfkOriginType(data, potentialServer);
                    transferHandler.sendToLimboFromAfk(originType).join();
                } else {
                    transferHandler.sendToLimbo().join();
                }
            }
            case TELEPORT -> {
                if (potentialServer.isEmpty()) {
                    return EMPTY;
                }
                UUID server = UUID.fromString(potentialServer.get().getServer().getServerInfo().getName());
                Number x = (Number) data.get("x");
                Number y = (Number) data.get("y");
                Number z = (Number) data.get("z");
                Number yaw = (Number) data.get("yaw");
                Number pitch = (Number) data.get("pitch");
                RedisClient.requestServer(server,
                    new TeleportProtocol(),
                    new TeleportProtocol.Request(uuid.toString(),
                        x.doubleValue(), y.doubleValue(), z.doubleValue(),
                        yaw.floatValue(), pitch.floatValue())).join();
            }
            case EVENT -> {
                if (potentialServer.isEmpty()) {
                    return EMPTY;
                }
                UUID server = UUID.fromString(potentialServer.get().getServer().getServerInfo().getName());
                RedisClient.requestServer(server,
                    new RunEventProtocol(),
                    new RunEventProtocol.Request(uuid.toString(),
                        (String) data.get("event"),
                        (String) data.get("data"))).join();
            }
            case MESSAGE -> {
                String messageToSend = (String) data.get("message");
                player.sendMessage(JSONComponentSerializer.json().deserialize(messageToSend));
            }
        }
        return EMPTY;
    }

    private ServerType resolveAfkOriginType(Map<String, Object> data, Optional<ServerConnection> potentialServer) {
        ServerType requestedOriginType = null;
        if (data.containsKey("origin-type")) {
            String rawOriginType = (String) data.get("origin-type");
            if (ServerType.isServerType(rawOriginType)) {
                requestedOriginType = ServerType.valueOf(rawOriginType.toUpperCase());
            }
        }

        ServerType currentType = potentialServer
            .map(connection -> GameManager.getTypeFromRegisteredServer(connection.getServer()))
            .orElse(null);
        return RavengardOnlyRouting.afkOrigin(requestedOriginType, currentType);
    }

    private void publishPresence(Player player, boolean online) {
        try {
            Optional<ServerConnection> serverConn = player.getCurrentServer();
            var type = serverConn.map(conn -> GameManager.getTypeFromRegisteredServer(conn.getServer())).orElse(null);
            PresencePublisher.publish(player, online, serverConn.map(ServerConnection::getServer).orElse(null),
                type != null ? type.name() : null);
        } catch (Exception ignored) {
        }
    }
}
