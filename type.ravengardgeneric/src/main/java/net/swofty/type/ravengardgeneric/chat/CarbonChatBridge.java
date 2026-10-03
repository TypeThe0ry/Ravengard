package net.swofty.type.ravengardgeneric.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minestom.server.MinecraftServer;
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
import java.util.UUID;

/**
 * Joins the network's CarbonChat cross-server chat: what Ravengard players say in the global
 * channel reaches the CarbonChat servers, and their global chat is shown here.
 */
public final class CarbonChatBridge {
    public static final String ENV_REDIS = "CARBON_REDIS_URI";
    public static final String GLOBAL_CHANNEL = "carbon:global";

    private static final byte[] CHANNEL = CarbonChatProtocol.CHANNEL.getBytes(StandardCharsets.UTF_8);
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static volatile @Nullable CarbonChatBridge instance;

    private final UUID serverId = UUID.randomUUID();
    private final JedisPool pool;

    private CarbonChatBridge(String redisUri) {
        this.pool = new JedisPool(URI.create(redisUri));
    }

    public static boolean isEnabled() {
        return instance != null;
    }

    public static void startFromEnvironment() {
        String uri = System.getenv(ENV_REDIS);
        if (uri == null || uri.isBlank()) return;
        CarbonChatBridge bridge = new CarbonChatBridge(uri.strip());
        instance = bridge;
        Thread.ofVirtual().name("carbonchat-subscriber").start(bridge::subscribeForever);
        MinecraftServer.getSchedulerManager().buildTask(() -> bridge.publish(CarbonChatProtocol.KEEP_ALIVE,
                        CarbonChatProtocol.keepAlive(bridge.serverId)))
                .delay(TaskSchedule.seconds(5)).repeat(TaskSchedule.seconds(5)).schedule();
        Logger.info("CarbonChat cross-server chat bridged as server {}", bridge.serverId);
    }

    public static void sendGlobal(HypixelPlayer player, String message) {
        CarbonChatBridge bridge = instance;
        if (bridge == null) return;
        String component = GSON.serialize(CarbonChatFormat.line(player.getUsername(), message));
        Thread.ofVirtual().start(() -> bridge.publish(CarbonChatProtocol.CHAT_MESSAGE, CarbonChatProtocol.chat(
                new CarbonChatProtocol.ChatMessage(player.getUuid(), GLOBAL_CHANNEL, player.getUsername(), component))));
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
            case CarbonChatProtocol.INITIALIZATION -> publish(CarbonChatProtocol.PACKET_VERSION_REPLY,
                    CarbonChatProtocol.packetVersion(from, serverId));
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
            default -> {
            }
        }
    }

    private static void show(Component line) {
        RavengardLocalization.verbatim(line);
        for (HypixelPlayer player : HypixelGenericLoader.getLoadedPlayers()) {
            player.sendMessage(line);
        }
    }
}
