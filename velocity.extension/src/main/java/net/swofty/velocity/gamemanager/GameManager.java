package net.swofty.velocity.gamemanager;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import lombok.Getter;
import net.swofty.commons.ServerType;
import net.swofty.commons.config.ConfigProvider;
import net.swofty.commons.protocol.objects.proxy.from.PingServerProtocol;
import net.swofty.velocity.SkyBlockVelocity;
import net.swofty.commons.redis.RedisClient;
import net.swofty.velocity.testflow.TestFlowManager;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameManager {
    public static final int SLEEP_TIME = 1000;
    private static final int PING_DEADLINE_MILLIS = 3000;
    public static final int MISSED_PINGS_BEFORE_REMOVAL = 5;
    @Getter
    private static final Map<ServerType, List<GameServer>> servers = new ConcurrentHashMap<>();
    private static final ServerPingTracker pingTracker = new ServerPingTracker(MISSED_PINGS_BEFORE_REMOVAL);

    public static synchronized GameServer addServer(ServerType type, UUID serverID, String host, int port, int maxPlayers) {
        pingTracker.forget(serverID);
        port = port == -1 ? getNextAvailablePort() : port;    // if port is -1 then get next available port
        host = (host == null || host.isEmpty()) ? ConfigProvider.settings().getHostName() : host; // if host is null then get from config
        RegisteredServer registeredServer = SkyBlockVelocity.getServer().registerServer(
                new ServerInfo(serverID.toString(), new InetSocketAddress(host, port))
        );

        boolean isLobby = type.name().endsWith("_LOBBY");
        String rootName = isLobby ? "L" : (maxPlayers <= 20 ? "mini" : "mega");
        String shortenedRootName = isLobby ? "L" : (maxPlayers <= 20 ? "m" : "M");

        char letter = (char) (java.util.concurrent.ThreadLocalRandom.current().nextInt(26) + (isLobby ? 'a' : 'A'));
        String displayName = getNextAvailableDisplayName() + "" + letter;

        GameServer server = new GameServer(
                rootName + displayName, shortenedRootName + displayName,
                serverID, registeredServer, maxPlayers
        );
        servers.computeIfAbsent(type, _ -> new CopyOnWriteArrayList<>()).add(server);

        return server;
    }

    public static boolean isAnyEmpty(ServerType type) {
        return getFromType(type).stream().anyMatch(gameServer -> gameServer.maxPlayers() > gameServer.registeredServer().getPlayersConnected().size());
    }

    public static @Nullable GameServer getFromRegisteredServer(RegisteredServer registeredServer) {
        for (List<GameServer> gameServers : servers.values()) {
            for (GameServer gameServer : gameServers) {
                if (gameServer.registeredServer().equals(registeredServer)) {
                    return gameServer;
                }
            }
        }
        return null;
    }

    public static @Nullable ServerType getTypeFromRegisteredServer(RegisteredServer registeredServer) {
        for (ServerType type : servers.keySet()) {
            for (GameServer gameServer : servers.get(type)) {
                if (gameServer.registeredServer().equals(registeredServer)) return type;
            }
        }
        return null;
    }

    public static @Nullable ServerType getTypeFromUUID(UUID uuid) {
        for (ServerType type : servers.keySet()) {
            for (GameServer gameServer : servers.get(type)) {
                if (gameServer.internalID().equals(uuid)) return type;
            }
        }
        return null;
    }

    public static boolean hasType(ServerType type) {
        return servers.containsKey(type) && !servers.get(type).isEmpty();
    }

    /**
     * A snapshot of the registered servers of a type. Callers narrow it down while balancing, and
     * that must never reach the registry: a full server filtered out here used to be unregistered
     * for good, since backends only register once at startup.
     */
    public static List<GameServer> getFromType(ServerType type) {
        List<GameServer> registered = servers.get(type);
        return registered == null ? new ArrayList<>() : new ArrayList<>(registered);
    }

    public static GameServer getFromUUID(UUID uuid) {
        for (List<GameServer> gameServers : servers.values()) {
            for (GameServer gameServer : gameServers) {
                if (gameServer.internalID().equals(uuid)) return gameServer;
            }
        }
        return null;
    }

    public static void loopServers(ProxyServer server) {
        server.getScheduler().buildTask(SkyBlockVelocity.getPlugin(), () -> {
            new HashMap<>(servers).forEach((serverType, registeredServers) -> {
                new ArrayList<>(registeredServers).forEach(registeredServer -> {
                    RegisteredServer givenServer = registeredServer.registeredServer();
                    AtomicBoolean pingSuccess = new AtomicBoolean(false);
                    long startTime = System.currentTimeMillis();

                    RedisClient.requestServer(registeredServer.internalID(),
                            new PingServerProtocol(), new PingServerProtocol.Request()
                    ).thenRun(() -> {
                        pingSuccess.set(true);
                        pingTracker.recordSuccess(registeredServer.internalID());
                    });

                    server.getScheduler().buildTask(SkyBlockVelocity.getPlugin(), () -> {
                        if (pingSuccess.get()) return;

                        if (!pingTracker.recordFailure(registeredServer.internalID())) {
                            org.tinylog.Logger.warn(
                                "Server {} missed a ping ({}/{}, sent at {}, deadline {}ms)",
                                givenServer.getServerInfo().getName(),
                                pingTracker.missed(registeredServer.internalID()),
                                MISSED_PINGS_BEFORE_REMOVAL,
                                startTime,
                                System.currentTimeMillis() - startTime
                            );
                            return;
                        }

                        org.tinylog.Logger.warn(
                            "Server {} is offline! Removing from list after {} missed pings (last ping sent at {}, deadline {}ms)",
                            givenServer.getServerInfo().getName(),
                            MISSED_PINGS_BEFORE_REMOVAL,
                            startTime,
                            System.currentTimeMillis() - startTime
                        );
                        List<GameServer> currentServers = servers.get(serverType);
                        if (currentServers == null || !currentServers.remove(registeredServer)) return;

                        TestFlowManager.handleServerDisconnect(registeredServer.internalID);
                    }).delay(Duration.ofMillis(PING_DEADLINE_MILLIS)).schedule();
                });
            });
        }).repeat(Duration.ofMillis(SLEEP_TIME)).schedule();
    }

    private static int getNextAvailableDisplayName() {
        Set<Integer> used = new HashSet<>();
        for (List<GameServer> list : servers.values()) {
            for (GameServer gs : list) {
                String digits = gs.displayName().replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    try {
                        int n = Integer.parseInt(digits);
                        if (n > 0) used.add(n);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        int candidate = 1;
        while (used.contains(candidate)) candidate++;
        return candidate;
    }


    private static int getNextAvailablePort() {
        if (servers.isEmpty()) return 20000;
        if (servers.values().stream().allMatch(List::isEmpty)) return 20000;

        ArrayList<GameServer> gameServers = new ArrayList<>();
        servers.values().forEach(gameServers::addAll);

        int highestPort = gameServers.stream().mapToInt(gameServer -> gameServer.registeredServer().getServerInfo().getAddress().getPort()).max().getAsInt();
        return highestPort + 1;
    }

    public record GameServer(String displayName, String shortDisplayName,
                             UUID internalID, RegisteredServer registeredServer,
                             int maxPlayers) {
        public boolean hasEmptySlots() {
            return maxPlayers > registeredServer().getPlayersConnected().size();
        }
    }
}
