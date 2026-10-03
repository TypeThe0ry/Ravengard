package net.swofty.type.ravengardgeneric.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.timer.TaskSchedule;
import net.swofty.type.generic.HypixelGenericLoader;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.ravengardgeneric.i18n.RavengardLocalization;
import org.jetbrains.annotations.Nullable;
import org.tinylog.Logger;
import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Joins the network's CarbonChat cross-server messenger: global chat flows both ways, the
 * Ravengard players are announced to the network, and private messages cross servers.
 */
public final class CarbonChatBridge {
    public static final String ENV_REDIS = "CARBON_REDIS_URI";
    public static final String GLOBAL_CHANNEL = "carbon:global";

    private static final byte[] CHANNEL = CarbonChatProtocol.CHANNEL.getBytes(StandardCharsets.UTF_8);
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final long SERVER_TIMEOUT_MILLIS = 20_000;
    private static volatile @Nullable CarbonChatBridge instance;

    public record NetworkPlayer(UUID id, String name) {
    }

    private final UUID serverId;
    private final JedisPool pool;
    private final Map<UUID, Map<UUID, String>> remotePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> remoteAlive = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> replyTargets = new ConcurrentHashMap<>();

    private CarbonChatBridge(String redisUri, UUID serverId) {
        this.pool = new JedisPool(URI.create(redisUri));
        this.serverId = serverId;
    }

    public static boolean isEnabled() {
        return instance != null;
    }

    public static @Nullable CarbonChatBridge get() {
        return instance;
    }

    public static void startFromEnvironment(String serverName) {
        String uri = System.getenv(ENV_REDIS);
        if (uri == null || uri.isBlank()) return;
        UUID serverId = UUID.nameUUIDFromBytes(("ravengard-carbonchat:" + serverName).getBytes(StandardCharsets.UTF_8));
        CarbonChatBridge bridge = new CarbonChatBridge(uri.strip(), serverId);
        instance = bridge;
        Thread.ofVirtual().name("carbonchat-subscriber").start(bridge::subscribeForever);
        MinecraftServer.getSchedulerManager().buildTask(bridge::heartbeat)
                .delay(TaskSchedule.seconds(5)).repeat(TaskSchedule.seconds(5)).schedule();
        MinecraftServer.getSchedulerManager().buildTask(bridge::announcePlayers)
                .delay(TaskSchedule.seconds(60)).repeat(TaskSchedule.seconds(60)).schedule();
        MinecraftServer.getSchedulerManager().buildTask(bridge::hideNetworkFromTabList)
                .delay(TaskSchedule.seconds(3)).repeat(TaskSchedule.seconds(3)).schedule();
        MinecraftServer.getSchedulerManager().buildShutdownTask(() -> bridge.publish(
                CarbonChatProtocol.LOCAL_PLAYERS, CarbonChatProtocol.localPlayers(Map.of())));
        MinecraftServer.getGlobalEventHandler()
                .addListener(PlayerSpawnEvent.class, event -> {
                    if (event.isFirstSpawn()) bridge.playerChanged(event.getPlayer(), true);
                })
                .addListener(PlayerDisconnectEvent.class, event -> bridge.playerChanged(event.getPlayer(), false));
        CarbonWhisperCommands.register();
        Logger.info("CarbonChat cross-server chat bridged as server {}", serverId);
    }

    public static void sendGlobal(HypixelPlayer player, String message) {
        CarbonChatBridge bridge = instance;
        if (bridge == null) return;
        String component = GSON.serialize(CarbonChatFormat.line(player.getUsername(), message));
        Thread.ofVirtual().start(() -> bridge.publish(CarbonChatProtocol.CHAT_MESSAGE, CarbonChatProtocol.chat(
                new CarbonChatProtocol.ChatMessage(player.getUuid(), GLOBAL_CHANNEL, player.getUsername(), component))));
    }

    public Optional<NetworkPlayer> findRemote(String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        return remotePlayers.values().stream()
                .flatMap(players -> players.entrySet().stream())
                .filter(entry -> entry.getValue().toLowerCase(Locale.ROOT).equals(wanted))
                .map(entry -> new NetworkPlayer(entry.getKey(), entry.getValue()))
                .findFirst();
    }

    public java.util.List<String> remoteNames() {
        return remotePlayers.values().stream().flatMap(players -> players.values().stream()).distinct().toList();
    }

    public Optional<NetworkPlayer> findRemote(UUID id) {
        return remotePlayers.values().stream()
                .filter(players -> players.containsKey(id))
                .map(players -> new NetworkPlayer(id, players.get(id)))
                .findFirst();
    }

    public @Nullable UUID replyTarget(UUID player) {
        return replyTargets.get(player);
    }

    public void rememberConversation(UUID sender, UUID recipient) {
        replyTargets.put(sender, recipient);
    }

    public void whisperRemote(Player sender, NetworkPlayer recipient, String message) {
        String component = GSON.serialize(Component.text(message));
        rememberConversation(sender.getUuid(), recipient.id());
        Thread.ofVirtual().start(() -> publish(CarbonChatProtocol.WHISPER, CarbonChatProtocol.whisper(
                new CarbonChatProtocol.Whisper(sender.getUuid(), recipient.id(), component))));
    }

    /**
     * A network proxy that rebuilds the tab list on a server switch (TAB's global playerlist) can
     * still list the players of the server the viewer just left; the Ravengard tab list only
     * carries the map, so the network's players are taken off it.
     */
    private void hideNetworkFromTabList() {
        java.util.Set<UUID> local = new java.util.HashSet<>();
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            local.add(player.getUuid());
        }
        java.util.List<UUID> remote = remotePlayers.values().stream()
                .flatMap(players -> players.keySet().stream())
                .filter(id -> !local.contains(id))
                .distinct()
                .toList();
        if (remote.isEmpty()) return;
        net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket packet =
                new net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket(remote);
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.sendPacket(packet);
        }
    }

    private void heartbeat() {
        publish(CarbonChatProtocol.KEEP_ALIVE, CarbonChatProtocol.keepAlive(serverId));
        long now = System.currentTimeMillis();
        remoteAlive.entrySet().removeIf(entry -> {
            boolean expired = now - entry.getValue() > SERVER_TIMEOUT_MILLIS;
            if (expired) remotePlayers.remove(entry.getKey());
            return expired;
        });
    }

    private void announcePlayers() {
        Map<UUID, String> players = new HashMap<>();
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            players.put(player.getUuid(), player.getUsername());
        }
        publish(CarbonChatProtocol.LOCAL_PLAYERS, CarbonChatProtocol.localPlayers(players));
    }

    private void playerChanged(Player player, boolean added) {
        if (!added) replyTargets.remove(player.getUuid());
        Thread.ofVirtual().start(() -> publish(CarbonChatProtocol.LOCAL_PLAYER_CHANGE, CarbonChatProtocol.playerChange(
                new CarbonChatProtocol.PlayerChange(player.getUuid(), added, player.getUsername()))));
    }

    private void publish(int packetId, byte[] payload) {
        byte[] frame = CarbonChatProtocol.frame(serverId, UUID.randomUUID(), packetId, payload);
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(CHANNEL, frame);
        } catch (Exception exception) {
            Logger.warn("CarbonChat publish failed: {}", exception.getMessage());
        }
    }

    private void subscribeForever() {
        long backoff = 1000;
        while (true) {
            try (Jedis jedis = pool.getResource()) {
                backoff = 1000;
                jedis.subscribe(new BinaryJedisPubSub() {
                    @Override
                    public void onSubscribe(byte[] channel, int subscribedChannels) {
                        publish(CarbonChatProtocol.INITIALIZATION, CarbonChatProtocol.initialization(serverId));
                        announcePlayers();
                    }

                    @Override
                    public void onMessage(byte[] channel, byte[] message) {
                        receive(message);
                    }
                }, CHANNEL);
            } catch (Exception exception) {
                Logger.warn("CarbonChat subscription lost, retrying in {} ms: {}", backoff, exception.getMessage());
            }
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException interrupted) {
                return;
            }
            backoff = Math.min(backoff * 2, 30_000);
        }
    }

    private void receive(byte[] message) {
        CarbonChatProtocol.Frame frame;
        try {
            frame = CarbonChatProtocol.decode(message);
        } catch (Exception exception) {
            Logger.warn("Unreadable CarbonChat frame: {}", exception.getMessage());
            return;
        }
        if (frame.server().equals(serverId)) return;
        remoteAlive.put(frame.server(), System.currentTimeMillis());
        for (CarbonChatProtocol.Packet packet : frame.packets()) {
            try {
                handle(frame.server(), packet);
            } catch (Exception exception) {
                Logger.warn("Unreadable CarbonChat packet {}: {}", packet.id(), exception.getMessage());
            }
        }
    }

    private void handle(UUID from, CarbonChatProtocol.Packet packet) {
        switch (packet.id()) {
            case CarbonChatProtocol.INITIALIZATION -> {
                publish(CarbonChatProtocol.PACKET_VERSION_REPLY, CarbonChatProtocol.packetVersion(from, serverId));
                announcePlayers();
            }
            case CarbonChatProtocol.PACKET_VERSION_REQUEST -> {
                UUID requester = CarbonChatProtocol.versionRequester(packet.payload(), serverId);
                if (requester != null) {
                    publish(CarbonChatProtocol.PACKET_VERSION_REPLY, CarbonChatProtocol.packetVersion(requester, serverId));
                }
            }
            case CarbonChatProtocol.CHAT_MESSAGE -> {
                CarbonChatProtocol.ChatMessage chat = CarbonChatProtocol.readChat(packet.payload());
                if (GLOBAL_CHANNEL.equals(chat.channelKey())) {
                    show(GSON.deserialize(chat.componentJson()));
                }
            }
            case CarbonChatProtocol.LOCAL_PLAYERS -> {
                Map<UUID, String> players = CarbonChatProtocol.readLocalPlayers(packet.payload());
                if (players.isEmpty()) {
                    remotePlayers.remove(from);
                } else {
                    remotePlayers.put(from, new ConcurrentHashMap<>(players));
                }
            }
            case CarbonChatProtocol.LOCAL_PLAYER_CHANGE -> {
                CarbonChatProtocol.PlayerChange change = CarbonChatProtocol.readPlayerChange(packet.payload());
                Map<UUID, String> players = remotePlayers.computeIfAbsent(from, ignored -> new ConcurrentHashMap<>());
                if (change.added()) {
                    players.put(change.playerId(), change.playerName());
                } else {
                    players.remove(change.playerId());
                }
            }
            case CarbonChatProtocol.WHISPER -> deliverWhisper(CarbonChatProtocol.readWhisper(packet.payload()));
            default -> {
            }
        }
    }

    private void deliverWhisper(CarbonChatProtocol.Whisper whisper) {
        Player recipient = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(whisper.to());
        if (recipient == null) return;
        String senderName = findRemote(whisper.from()).map(NetworkPlayer::name).orElse("?");
        Component line = CarbonChatFormat.whisperFrom(senderName, GSON.deserialize(whisper.componentJson()));
        RavengardLocalization.verbatim(line);
        recipient.sendMessage(line);
        rememberConversation(recipient.getUuid(), whisper.from());
    }

    private static void show(Component line) {
        RavengardLocalization.verbatim(line);
        for (HypixelPlayer player : HypixelGenericLoader.getLoadedPlayers()) {
            player.sendMessage(line);
        }
    }
}
