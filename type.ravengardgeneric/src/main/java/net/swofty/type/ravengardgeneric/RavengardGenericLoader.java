package net.swofty.type.ravengardgeneric;

import lombok.Getter;
import lombok.SneakyThrows;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.client.play.ClientAttackPacket;
import net.minestom.server.network.packet.client.play.ClientInteractEntityPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerActionPacket;
import net.minestom.server.network.packet.client.play.ClientUseItemPacket;
import net.minestom.server.listener.PlayerActionListener;
import net.minestom.server.listener.UseItemListener;
import net.minestom.server.world.DimensionType;
import net.kyori.adventure.key.Key;
import net.swofty.commons.CustomWorlds;
import net.swofty.commons.ServerType;
import net.swofty.type.generic.HypixelConst;
import net.swofty.type.generic.HypixelTypeLoader;
import net.swofty.type.generic.command.HypixelCommand;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.generic.event.HypixelEventHandler;
import net.swofty.type.generic.packet.HypixelPacketClientListener;
import net.swofty.type.generic.packet.HypixelPacketServerListener;
import net.swofty.type.generic.resourcepack.ResourcePackManager;
import net.swofty.type.generic.world.HypixelWorldLoader;
import net.swofty.type.ravengardgeneric.resourcepack.RavengardPack;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;
import org.jetbrains.annotations.Nullable;
import org.reflections.Reflections;
import org.tinylog.Logger;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public record RavengardGenericLoader(HypixelTypeLoader typeLoader) {

    private static final Path DUNGEON_POLAR = Path.of("./configuration/ravengard/dungeon_1.polar");

    @Getter
    private static MinecraftServer server;

    @Nullable
    public static Instance tutorialInstance;

    @SneakyThrows
    public void initialize(MinecraftServer server) {
        RavengardGenericLoader.server = server;
        net.swofty.type.generic.data.domain.PlayerDataService.register(new net.swofty.type.ravengardgeneric.data.RavengardDomain());
        registerRegistrySuperset(
                CustomWorlds.RAVENGARD_LOBBY.getPath(),
                CustomWorlds.RAVENGARD_TUTORIAL.getPath(),
                DUNGEON_POLAR);

        if (typeLoader.getType() == ServerType.RAVENGARD_LOBBY) {
            tutorialInstance = HypixelWorldLoader.load(CustomWorlds.RAVENGARD_TUTORIAL, MinecraftServer.getInstanceManager());
        }

        net.swofty.type.ravengardgeneric.i18n.RavengardLanguagePack.installFromEnvironment();
        net.swofty.type.ravengardgeneric.chat.CarbonChatBridge.startFromEnvironment(typeLoader.getType().name());
        net.swofty.type.ravengardgeneric.item.RavengardItemRegistry.load();
        net.swofty.type.ravengardgeneric.shop.RavengardShopRegistry.load();
        net.swofty.type.ravengardgeneric.item.attribute.RavengardItemAttribute.registerItemAttributes();
        net.swofty.type.ravengardgeneric.entity.mob.RavengardMob.startTicking();
        connectRegions();
        net.swofty.type.ravengardgeneric.region.RavengardRegion.cacheRegions();
        net.swofty.type.ravengardgeneric.region.RavengardRegionTracker.start();

        loopThroughPackage("net.swofty.type.ravengardgeneric.event.actions", HypixelEventClass.class)
                .forEach(HypixelEventHandler::registerEventMethods);

        loopThroughPackage("net.swofty.type.ravengardgeneric.commands", HypixelCommand.class)
                .forEach(command -> {
                    try {
                        MinecraftServer.getCommandManager().register(command.getCommand());
                    } catch (Exception e) {
                        Logger.error(e, "Failed to register command {} in class {}",
                                command.getCommand().getName(), command.getClass().getSimpleName());
                    }
                });

        loopThroughPackage("net.swofty.type.ravengardgeneric.packets.client", HypixelPacketClientListener.class)
                .forEach(HypixelPacketClientListener::cacheListener);
        loopThroughPackage("net.swofty.type.ravengardgeneric.packets.server", HypixelPacketServerListener.class)
                .forEach(HypixelPacketServerListener::cacheListener);

        HypixelPacketClientListener.register(HypixelConst.getEventHandler());
        HypixelPacketServerListener.register(HypixelConst.getEventHandler());
        HypixelEventHandler.register(HypixelConst.getEventHandler());
        MinecraftServer.getPacketListenerManager().setPlayListener(ClientAttackPacket.class, (packet, player) -> {
            Logger.debug("Ravengard raw attack packet received: player={} packet={}",
                    player.getUsername(), packet.getClass().getSimpleName());
            if (packet instanceof ClientAttackPacket attack && player instanceof RavengardPlayer ravengardPlayer) {
                net.swofty.type.ravengardgeneric.packets.client.PacketListenerRavengardCombat
                        .bridge(ravengardPlayer, attack.targetId());
            }
        });
        MinecraftServer.getPacketListenerManager().setPlayListener(ClientPlayerActionPacket.class, (packet, player) -> {
            if (packet.status() == ClientPlayerActionPacket.Status.UPDATE_ITEM_STATE) {
                Logger.debug("Ravengard item-use release packet received: player={} status={}",
                        player.getUsername(), packet.status());
            }
            PlayerActionListener.playerActionListener(packet, player);
        });
        MinecraftServer.getPacketListenerManager().setPlayListener(ClientUseItemPacket.class, (packet, player) -> {
            Logger.debug("Ravengard item-use start packet received: player={} hand={}",
                    player.getUsername(), packet.hand());
            if (packet.hand() == net.minestom.server.entity.PlayerHand.MAIN
                    && player.getItemInMainHand().material() == net.minestom.server.item.Material.BOW) {
                player.refreshItemUse(packet.hand(), 72000L);
                player.refreshActiveHand(true, false, false);
            }
            UseItemListener.useItemListener(packet, player);
            if (packet.hand() == net.minestom.server.entity.PlayerHand.MAIN
                    && player.getItemInMainHand().material() == net.minestom.server.item.Material.BOW) {
                net.swofty.type.ravengardgeneric.event.actions.player.ActionPlayerBow.markDrawStart(player);
            }
        });

        RavengardPack testingPack = RavengardPack.fromConfig();
        ResourcePackManager packManager = new ResourcePackManager(testingPack);
        HypixelConst.setResourcePackManager(packManager);
        packManager.initialize();

        MinecraftServer.getConnectionManager().setPlayerProvider((playerConnection, gameProfile) -> {
            RavengardPlayer player = new RavengardPlayer(playerConnection, gameProfile);

            UUID uuid = playerConnection.getPlayer().getUuid();
            String username = playerConnection.getPlayer().getUsername();

            Logger.info("Received new Ravengard player: {} ({})", username, uuid);
            return player;
        });
    }

    static void registerRegistrySuperset(Path... polarFiles) throws java.io.IOException {
        for (Path polarFile : polarFiles) {
            if (!Files.isRegularFile(polarFile)) {
                throw new IllegalStateException("Ravengard map not found: " + polarFile);
            }
            HypixelWorldLoader.registerCustomBiomes(polarFile);
        }
        Key dimensionKey = Key.key("ravengard:dungeon");
        if (MinecraftServer.getDimensionTypeRegistry().getKey(dimensionKey) == null) {
            MinecraftServer.getDimensionTypeRegistry().register(
                    dimensionKey,
                    DimensionType.builder().ambientLight(1).build());
        }
    }

    private static void connectRegions() {
        try {
            com.mongodb.ConnectionString connection = new com.mongodb.ConnectionString(
                    net.swofty.commons.config.ConfigProvider.settings().getMongodb());
            com.mongodb.MongoClientSettings settings = com.mongodb.MongoClientSettings.builder()
                    .applyConnectionString(connection).build();
            com.mongodb.client.MongoClient client = com.mongodb.client.MongoClients.create(settings);
            net.swofty.type.ravengardgeneric.data.monogdb.RavengardRegionDatabase.connect(client);
            net.swofty.type.ravengardgeneric.data.monogdb.RavengardProfileDatabase.connect(client);
            net.swofty.type.ravengardgeneric.data.monogdb.RavengardTrackedItemsDatabase.connect(client);
        } catch (Exception exception) {
            Logger.warn(exception, "Could not connect the Ravengard region collection; "
                    + "only built-in regions will be available");
        }
    }

    public static List<RavengardPlayer> getLoadedPlayers() {
        List<RavengardPlayer> players = new ArrayList<>();
        MinecraftServer.getConnectionManager().getOnlinePlayers()
                .stream()
                .filter(player -> player instanceof RavengardPlayer)
                .filter(player -> player.getInstance() != null)
                .filter(player -> net.swofty.type.generic.data.DataHandler
                        .findUser(player.getUuid()).isPresent())
                .forEach(player -> players.add((RavengardPlayer) player));
        return players;
    }

    public static @Nullable RavengardPlayer getFromUUID(UUID uuid) {
        return getLoadedPlayers().stream()
                .filter(player -> player.getUuid().equals(uuid))
                .findFirst()
                .orElse(null);
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
                .filter(java.util.Objects::nonNull);
    }
}
