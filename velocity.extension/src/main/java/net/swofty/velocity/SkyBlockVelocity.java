package net.swofty.velocity;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.EventManager;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.AwaitingEventExecutor;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.network.Connections;
import com.viaversion.viabackwards.ViaBackwardsPlatformImpl;
import com.viaversion.viarewind.ViaRewindPlatformImpl;
import com.viaversion.viaversion.ViaManagerImpl;
import com.viaversion.viaversion.commands.ViaCommandHandler;
import io.github.retrooper.packetevents.velocity.factory.VelocityPacketEventsBuilder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.swofty.commons.ServerType;
import net.swofty.commons.ServiceType;
import net.swofty.commons.config.ConfigProvider;
import net.swofty.commons.data.SwoftyData;
import net.swofty.commons.protocol.RedisProtocol;
import net.swofty.commons.protocol.objects.proxy.from.*;
import net.swofty.commons.protocol.objects.punishment.GetActivePunishmentProtocol;
import net.swofty.commons.punishment.ActivePunishment;
import net.swofty.commons.punishment.PunishmentMessages;
import net.swofty.commons.punishment.PunishmentReason;
import net.swofty.commons.punishment.PunishmentTag;
import net.swofty.commons.punishment.PunishmentType;
import net.swofty.commons.redis.ProxyHeartbeat;
import net.swofty.commons.redis.RedisClient;
import net.swofty.commons.redis.RedisEndpoint;
import net.swofty.commons.redis.RedisMessageHandler;
import net.swofty.commons.text.Text;
import net.swofty.proxyapi.ProxyService;
import net.swofty.commons.redis.RedisWatchdog;
import net.swofty.redisapi.api.RedisAPI;
import net.swofty.velocity.command.*;
import net.swofty.velocity.data.AuthenticationDatabase;
import net.swofty.velocity.data.CoopDatabase;
import net.swofty.velocity.data.ProfilesDatabase;
import net.swofty.velocity.data.StoreEntitlementsDatabase;
import net.swofty.velocity.data.UserDatabase;
import net.swofty.velocity.gamemanager.BalanceConfiguration;
import net.swofty.velocity.gamemanager.BalanceConfigurations;
import net.swofty.velocity.gamemanager.GameManager;
import net.swofty.velocity.gamemanager.CrashRecoveryRouting;
import net.swofty.velocity.gamemanager.TransferHandler;
import net.swofty.velocity.packet.PlayerChannelHandler;
import net.swofty.velocity.packet.listener.PlayerMovementListener;
import net.swofty.velocity.presence.PresencePublisher;
import net.swofty.velocity.redis.RedisHandlerRegistry;
import net.swofty.velocity.redis.listeners.ListenerStaffChat;
import net.swofty.velocity.redis.listeners.ListenerStorePurchaseFulfillment;
import net.swofty.velocity.testflow.TestFlowManager;
import net.swofty.velocity.text.ProxyText;
import net.swofty.velocity.viaversion.SkyBlockPlatform;
import net.swofty.velocity.viaversion.SkyBlockPlatformLoader;
import net.swofty.velocity.viaversion.injector.SkyBlockViaInjector;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Plugin(
    id = "skyblock",
    name = "SkyBlock",
    version = "1.0",
    description = "SkyBlock plugin for Velocity",
    authors = {"Swofty"}
)
public class SkyBlockVelocity {
    @Getter
    private static ProxyServer server = null;
    @Getter
    private static SkyBlockVelocity plugin;
    @Getter
    private final PluginContainer pluginContainer;
    @Getter
    private final org.slf4j.Logger logger;
    @Getter
    private final Path dataDirectory;
    @Getter
    private static boolean supportCrossVersion = false;
    @Inject
    private ProxyServer proxy;

    @Getter
    private static final Set<UUID> unauthenticated = ConcurrentHashMap.newKeySet();

    @Inject
    public SkyBlockVelocity(ProxyServer tempServer, org.slf4j.Logger logger, PluginContainer pluginContainer, @DataDirectory Path dataDirectory) {
        plugin = this;
        server = tempServer;
        this.logger = logger;
        this.pluginContainer = pluginContainer;
        this.dataDirectory = dataDirectory;

    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        net.swofty.commons.text.MarkupLocalization.installFromEnvironment();
        server = proxy;
        supportCrossVersion = ConfigProvider.settings().getIntegrations().isViaVersion();

        if (supportCrossVersion) {
            PacketEvents.setAPI(VelocityPacketEventsBuilder.build(server, this.pluginContainer, this.logger, this.dataDirectory));
            PacketEvents.getAPI().getSettings().checkForUpdates(false);
            PacketEvents.getAPI().load();

            EventManager events = PacketEvents.getAPI().getEventManager();
            events.registerListener(new PlayerMovementListener(), PacketListenerPriority.NORMAL);

            PacketEvents.getAPI().init();
        }

        if (supportCrossVersion) {
            ViaManagerImpl.initAndLoad(
                new SkyBlockPlatform(dataDirectory.toFile()),
                new SkyBlockViaInjector(),
                new ViaCommandHandler(false),
                new SkyBlockPlatformLoader(),
                ViaBackwardsPlatformImpl::new,
                ViaRewindPlatformImpl::new
            );
        }

        // Register packets
        server.getEventManager().register(this, PostLoginEvent.class,
            (AwaitingEventExecutor<PostLoginEvent>) postLoginEvent -> EventTask.withContinuation(continuation -> {
                injectPlayer(postLoginEvent.getPlayer());
                TestFlowManager.handlePlayerJoin(postLoginEvent.getPlayer().getUsername());
                PresencePublisher.publish(postLoginEvent.getPlayer(), true, (String) null, null);

                // Broadcast staff join notification (servers will check if player is staff)
                ListenerStaffChat.broadcastStaffJoin(postLoginEvent.getPlayer().getUniqueId());

                continuation.resume();
            }));
        server.getEventManager().register(this, PermissionsSetupEvent.class,
            (AwaitingEventExecutor<PermissionsSetupEvent>) permissionsEvent -> EventTask.withContinuation(continuation -> {
                permissionsEvent.setProvider(_ -> PermissionFunction.ALWAYS_FALSE);
                continuation.resume();
            }));
        server.getEventManager().register(this, DisconnectEvent.class, PostOrder.LAST,
            (AwaitingEventExecutor<DisconnectEvent>) disconnectEvent ->
                disconnectEvent.getLoginStatus() == DisconnectEvent.LoginStatus.CONFLICTING_LOGIN
                    ? null
                    : EventTask.async(() -> {
                    // Broadcast staff leave notification (servers will check if player is staff)
                    ListenerStaffChat.broadcastStaffLeave(disconnectEvent.getPlayer().getUniqueId());

                    // Handle test flow player leave
                    TestFlowManager.handlePlayerLeave(disconnectEvent.getPlayer().getUsername());
                    PresencePublisher.publish(disconnectEvent.getPlayer(), false, (String) null, null);
                    removePlayer(disconnectEvent.getPlayer());
                })
        );

        server.getEventManager().register(this, ServerConnectedEvent.class,
            (AwaitingEventExecutor<ServerConnectedEvent>) serverConnectedEvent ->
                EventTask.async(() -> {
                    RegisteredServer newServer = serverConnectedEvent.getServer();
                    TransferHandler.clearCrashRecoveryOnServerConnected(
                        serverConnectedEvent.getPlayer().getUniqueId(), newServer);
                    var type = GameManager.getTypeFromRegisteredServer(newServer);
                    PresencePublisher.publish(serverConnectedEvent.getPlayer(), true, newServer, type != null ? type.name() : null);
                }));

        server.getScheduler().buildTask(SkyBlockVelocity.getPlugin(), () -> {
            server.getAllPlayers().forEach(player -> {
                var current = player.getCurrentServer();
                var type = current.map(conn -> GameManager.getTypeFromRegisteredServer(conn.getServer())).orElse(null);
                PresencePublisher.publish(player, true, current.map(ServerConnection::getServer).orElse(null),
                    type != null ? type.name() : null);
            });
        }).repeat(Duration.ofSeconds(10)).schedule();

        // Register commands
        CommandManager commandManager = proxy.getCommandManager();
        CommandMeta statusCommandMeta = commandManager.metaBuilder("serverstatus")
            .aliases("status")
            .plugin(this)
            .build();

        CommandMeta protocolVersionMeta = commandManager.metaBuilder("protocolversion")
            .aliases("protocol")
            .plugin(this)
            .build();

        CommandMeta loginCommandMeta = commandManager.metaBuilder("login")
            .plugin(this)
            .build();

        CommandMeta registerCommandMeta = commandManager.metaBuilder("register")
            .plugin(this)
            .build();

        CommandMeta lobbyCommandMeta = commandManager.metaBuilder("lobby")
            .plugin(this)
            .build();

        commandManager.register(statusCommandMeta, new ServerStatusCommand());
        commandManager.register(protocolVersionMeta, new ProtocolVersionCommand());
        commandManager.register(loginCommandMeta, new LoginCommand());
        commandManager.register(registerCommandMeta, new RegisterCommand());
        commandManager.register(lobbyCommandMeta, new LobbyCommand());

        // Handle database
        new ProfilesDatabase("_placeHolder").connect(ConfigProvider.settings().getMongodb());
        new AuthenticationDatabase(new UUID(0, 0)).connect(ConfigProvider.settings().getMongodb());
        UserDatabase.connect(ConfigProvider.settings().getMongodb());
        StoreEntitlementsDatabase.connect(ConfigProvider.settings().getMongodb());
        CoopDatabase.connect(ConfigProvider.settings().getMongodb());
        ListenerStorePurchaseFulfillment.startRankExpirationReconciler();

        // Setup Redis
        RedisAPI.generateInstance(ConfigProvider.settings().getRedisUri());
        RedisAPI.getInstance().setFilterId("proxy");
        RedisClient.identify(RedisEndpoint.proxy());

        ProxyHeartbeat.init(ConfigProvider.settings().getRedisUri());
        server.getScheduler().buildTask(SkyBlockVelocity.getPlugin(), ProxyHeartbeat::beat)
            .repeat(Duration.ofSeconds(2)).schedule();
        loopThroughPackage("net.swofty.velocity.redis.listeners", RedisMessageHandler.class)
            .forEach(RedisHandlerRegistry::register);
        RedisProtocol<?, ?>[] fromProxyProtocols = {
            new TeleportProtocol(), new PlayerSwitchedProtocol(),
            new DoesServerHaveIslandProtocol(),
            new RunEventProtocol(), new PingServerProtocol(),
            new GivePlayersOriginTypeProtocol(), new BroadcastStaffChatProtocol()
        };
        for (RedisProtocol<?, ?> protocol : fromProxyProtocols) {
            RedisClient.registerResponseProtocol(protocol);
        }
        loopThroughPackage("net.swofty.commons.protocol.objects", RedisProtocol.class)
            .filter(obj -> !obj.getClass().getPackageName().startsWith("net.swofty.commons.protocol.objects.proxy"))
            .forEach(RedisClient::registerResponseProtocol);
        RedisAPI.getInstance().startListeners();
        RedisWatchdog.start("velocity-proxy");

        // Setup GameManager
        GameManager.loopServers(server);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        RedisWatchdog.stop();
        try {
            SwoftyData.shutdown();
        } catch (Exception e) {
            logger.error("Failed to shut down player data storage", e);
        }

        try {
            RedisAPI.getInstance().shutdown();
        } catch (Exception e) {
            logger.error("Failed to shut down Redis", e);
        }
    }

    private boolean checkPunished(Player player) {
        try {
            ProxyService service = new ProxyService(ServiceType.PUNISHMENT);

            CompletableFuture<?> banFuture = service.handleRequest(
                new GetActivePunishmentProtocol.GetActivePunishmentMessage(player.getUniqueId(), PunishmentType.BAN.name()));
            CompletableFuture<?> muteFuture = service.handleRequest(
                new GetActivePunishmentProtocol.GetActivePunishmentMessage(player.getUniqueId(), PunishmentType.MUTE.name()));

            CompletableFuture.allOf(banFuture, muteFuture).orTimeout(3, TimeUnit.SECONDS).join();

            Object banResult = banFuture.join();
            if (banResult instanceof GetActivePunishmentProtocol.GetActivePunishmentResponse(
                boolean found1, String type1, String id, PunishmentReason reason1,
                long at, List<PunishmentTag> tags1, boolean success1, String error1
            ) && found1) {
                ActivePunishment punishment = new ActivePunishment(
                    type1, id, reason1, at, tags1);
                ProxyText.disconnect(player, PunishmentMessages.banMessage(punishment));
                return true;
            }

            Object muteResult = muteFuture.join();
            if (muteResult instanceof GetActivePunishmentProtocol.GetActivePunishmentResponse(
                boolean found, String type, String banId, PunishmentReason reason,
                long expiresAt, List<PunishmentTag> tags, boolean success, String error
            ) && found) {
                ActivePunishment punishment = new ActivePunishment(
                    type, banId, reason, expiresAt, tags);
                player.sendMessage(PunishmentMessages.muteMessage(punishment));
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Subscribe
    public EventTask onPlayerJoin(PlayerChooseInitialServerEvent event) {
        return EventTask.async(() -> {
            final Player player = event.getPlayer();

            if (checkPunished(player)) {
                return;
            }

            if (ConfigProvider.settings().isRequireAuth()) {
                ProxyText.disconnect(player, "<c>Authentication requires a configured holding server; this Ravengard-only proxy has none.");
                return;
            }

            if (!GameManager.hasType(ServerType.RAVENGARD_LOBBY)
                    || !GameManager.isAnyEmpty(ServerType.RAVENGARD_LOBBY)) {
                ProxyText.disconnect(player, "<c>There are no Ravengard Lobby servers available at the moment.");
                return;
            }

            List<GameManager.GameServer> gameServers = GameManager.getFromType(ServerType.RAVENGARD_LOBBY);
            if (TestFlowManager.isPlayerInTestFlow(player.getUsername())) {
                TestFlowManager.ProxyTestFlowInstance instance = TestFlowManager.getTestFlowForPlayer(player.getUsername());
                player.sendMessage(Text.of("<7>You are currently in test flow {}.", instance.getName()));
                player.sendMessage(Text.of("<7>Servers involved include {}", instance.getGameServers().stream().map(GameManager.GameServer::displayName).collect(Collectors.joining(", "))));
                player.sendMessage(Text.of("<7>We are expecting {} servers to instantiate.", instance.getTotalExpectedServers()));
                player.sendMessage(Text.of("<7>Test flow has been running for {} seconds.", instance.getUptime() / 1000));

                gameServers.removeIf(server -> {
                    TestFlowManager.ProxyTestFlowInstance testFlowInstance = TestFlowManager.getFromServerUUID(
                        server.internalID()
                    );

                    return testFlowInstance == null || !instance.hasServer(server.internalID());
                });
            } else {
                gameServers.removeIf(server -> {
                    TestFlowManager.ProxyTestFlowInstance testFlowInstance = TestFlowManager.getFromServerUUID(
                        server.internalID()
                    );

                    return testFlowInstance != null;
                });
            }

            if (gameServers.isEmpty()) {
                ProxyText.disconnect(player, "<c>There are no servers (type=RAVENGARD_LOBBY) available at the moment.");
                return;
            }

            List<BalanceConfiguration> configurations = BalanceConfigurations.CONFIGURATIONS.get(ServerType.RAVENGARD_LOBBY);
            GameManager.GameServer toSendTo = gameServers.getFirst();

            for (BalanceConfiguration configuration : configurations) {
                GameManager.GameServer server = configuration.getServer(player, gameServers);
                if (server != null) {
                    toSendTo = server;
                    break;
                }
            }

            // TODO: Force Resource Pack
            event.setInitialServer(toSendTo.registeredServer());
        });
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        unauthenticated.remove(event.getPlayer().getUniqueId());
        TransferHandler.clearPlayerState(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onServerCrash(KickedFromServerEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        if (!TransferHandler.beginCrashRecovery(playerUuid)) {
            event.setResult(KickedFromServerEvent.Notify.create(
                ProxyText.render("<c>Your Ravengard connection is already recovering. Please reconnect.")));
            return;
        }

        if (checkPunished(event.getPlayer())) {
            TransferHandler.clearCrashRecovery(playerUuid);
            return;
        }

        RegisteredServer originalServer = event.getServer();
        Component reason = event.getServerKickReason().orElseGet(() ->
            ProxyText.render("<c>Your connection to the server was lost. Please try again later."));
        ServerType serverType = GameManager.getTypeFromRegisteredServer(originalServer);

        for (ServerType candidate : CrashRecoveryRouting.candidates(serverType)) {
            if (!GameManager.hasType(candidate) || !GameManager.isAnyEmpty(candidate)) {
                continue;
            }
            GameManager.GameServer destination = BalanceConfigurations.getServerFor(event.getPlayer(), candidate);
            if (destination != null && !destination.registeredServer().equals(originalServer)) {
                event.setResult(KickedFromServerEvent.RedirectPlayer.create(destination.registeredServer(), reason));
                return;
            }
        }

        TransferHandler.clearCrashRecovery(playerUuid);
        event.setResult(KickedFromServerEvent.Notify.create(reason));
    }


    @Subscribe
    public void onPing(ProxyPingEvent event) {
        event.setPing(new ServerPing(
            event.getPing().getVersion(),
            new ServerPing.Players(server.getPlayerCount(), 200000, List.of()),
            Text.legacy("§f                 §aHypixel Network §c[1.8/26.2]\n§f   §6§lSB 0.27 TORRHUS & SAFARI §7| §e§lSUMMER EVENT").asComponent(),
            event.getPing().getFavicon().orElse(null)
        ));
    }

    @Subscribe
    public void onPlayerConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        if (!(player.getProtocolVersion().getProtocol() >= ProtocolVersion.MAXIMUM_VERSION.getProtocol()) && ConfigProvider.settings().getIntegrations().isViaVersion()) {
            String supportedFrom = ProtocolVersion.MAXIMUM_VERSION.getVersionIntroducedIn();

            player.sendMessage(Text.of("\n" +
                "<6><l>----------- <r><c>Server Notice <6><l>-----------\n" +
                "<r><c>Although we do support versions prior to <6>{}<c>, the experience may be degraded.\n" +
                "<c>If you experience any issues, please test if it also occurs on <6>{}<c> before reporting it.\n" +
                "<6><l>---------------------------------\n" +
                "\n", supportedFrom, supportedFrom));
        }

    }

    public static <T> Stream<T> loopThroughPackage(String packageName, Class<T> clazz) {
        Reflections reflections = new Reflections(packageName);
        Set<Class<? extends T>> subTypes = reflections.getSubTypesOf(clazz);

        return subTypes.stream()
            .map(subClass -> {
                try {
                    return clazz.cast(subClass.getDeclaredConstructor().newInstance());
                } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
                         InvocationTargetException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull);
    }

    private void injectPlayer(final Player player) {
        final ConnectedPlayer connectedPlayer = (ConnectedPlayer) player;
        Channel channel = connectedPlayer.getConnection().getChannel();
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addBefore(Connections.HANDLER, "PACKET", new PlayerChannelHandler(player));
    }

    private void removePlayer(final Player player) {
        final ConnectedPlayer connectedPlayer = (ConnectedPlayer) player;
        final Channel channel = connectedPlayer.getConnection().getChannel();

        channel.eventLoop().submit(() -> {
            channel.pipeline().remove("PACKET");
        });
    }
}
