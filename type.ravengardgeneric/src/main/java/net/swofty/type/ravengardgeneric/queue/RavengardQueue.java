package net.swofty.type.ravengardgeneric.queue;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import net.swofty.commons.ServerType;
import net.swofty.commons.ServiceType;
import net.swofty.commons.UnderstandableProxyServer;
import net.swofty.commons.protocol.objects.orchestrator.ChooseGameProtocol;
import net.swofty.commons.protocol.objects.orchestrator.GetServerForMapProtocol;
import net.swofty.proxyapi.ProxyService;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The fight queue as the 0.2 playtest presents it: joining answers with the gray then green
 * queue messages and the ominous drums, the menu button flips to leaving, and a found server
 * transfers the player to a dungeon. Matchmaking wait times are not observable from captures,
 * so a short fixed delay stands in for them.
 */
public final class RavengardQueue {
    public static final String FIGHT_QUEUE = "Ravengard Dungeon Trios";
    private static final TaskSchedule MATCH_DELAY = TaskSchedule.seconds(3);
    private static final Sound DRUMS = Sound.sound()
            .type(Key.key("hypixel_ravengard", "action.drums"))
            .volume(1.0f)
            .pitch(1.0f)
            .build();

    private static final Map<UUID, Task> QUEUED = new ConcurrentHashMap<>();
    private static final ProxyService ORCHESTRATOR = new ProxyService(ServiceType.ORCHESTRATOR);

    private RavengardQueue() {
    }

    private static final java.util.Set<UUID> SEARCHING = ConcurrentHashMap.newKeySet();

    public static boolean isQueued(HypixelPlayer player) {
        return QUEUED.containsKey(player.getUuid()) || SEARCHING.contains(player.getUuid());
    }

    public static void join(RavengardPlayer player) {
        if (isQueued(player)) {
            player.sendMessage("<c>{} is already queued for a game!", player.getUsername());
            return;
        }
        if (net.swofty.type.generic.HypixelConst.getTypeLoader().getType() == ServerType.RAVENGARD_DUNGEON) {
            player.sendMessage("<c>You are already in a dungeon!");
            return;
        }
        if (player.isTutorial() || player.getRavengardClass() == null) {
            player.sendMessage("<c>Choose a class before joining a fight!");
            return;
        }
        player.sendMessage("<7>Joining fight...");
        player.sendMessage("<a>Successfully queued for {}!", FIGHT_QUEUE);
        player.playSound(DRUMS);

        Task task = MinecraftServer.getSchedulerManager().buildTask(() -> {
            QUEUED.remove(player.getUuid());
            if (!player.isOnline()) {
                return;
            }
            player.sendMessage("<7>Found server, initiating transfer to {}...",
                    ServerType.RAVENGARD_DUNGEON.name().toLowerCase());
            GetServerForMapProtocol.GetServerForMapMessage request =
                    new GetServerForMapProtocol.GetServerForMapMessage(
                            ServerType.RAVENGARD_DUNGEON, null, "STANDARD", 1);
            UUID searching = player.getUuid();
            SEARCHING.add(searching);
            ORCHESTRATOR.handleRequest(request, 90, TimeUnit.SECONDS).thenCompose(response -> {
                if (!(response instanceof GetServerForMapProtocol.GetServerForMapResponse(
                        UnderstandableProxyServer server, String gameId, boolean success, String error))
                        || !success || server == null || gameId == null) {
                    String reason = response instanceof GetServerForMapProtocol.GetServerForMapResponse r
                            && r.error() != null ? r.error() : "no dungeon servers available";
                    player.sendMessage("<c>Could not start a dungeon: {}", reason);
                    return java.util.concurrent.CompletableFuture.<Void>completedFuture(null);
                }
                if (!player.isOnline()) {
                    return java.util.concurrent.CompletableFuture.<Void>completedFuture(null);
                }
                return ORCHESTRATOR.handleRequest(new ChooseGameProtocol.ChooseGameMessage(
                        player.getUuid(), server, gameId), 90, TimeUnit.SECONDS).thenAccept(choice -> {
                    if (!(choice instanceof ChooseGameProtocol.ChooseGameResponse result)) {
                        player.sendMessage("<c>Failed to register for the dungeon: no response");
                        return;
                    }
                    if (!result.success()) {
                        String reason = result.error() != null
                                ? result.error() : "dungeon registration failed";
                        player.sendMessage("<c>Failed to register for the dungeon: {}", reason);
                        return;
                    }
                    if (!player.isOnline()) {
                        return;
                    }
                    player.sendMessage("<a>Sending you to <f>{}<a>!", server.shortName());
                    player.asProxyPlayer().transferToWithIndication(server.uuid());
                }).exceptionally(throwable -> {
                    player.sendMessage("<c>Failed to register for the dungeon: {}", throwable.getMessage());
                    return null;
                });
            }).exceptionally(throwable -> {
                player.sendMessage("<c>Dungeon search failed: {}", throwable.getMessage());
                return null;
            }).whenComplete((ignored, throwable) -> SEARCHING.remove(searching));
        }).delay(MATCH_DELAY).schedule();
        QUEUED.put(player.getUuid(), task);
    }

    public static void leave(RavengardPlayer player) {
        Task task = QUEUED.remove(player.getUuid());
        if (task == null) {
            if (SEARCHING.contains(player.getUuid())) {
                player.sendMessage("<c>A dungeon is already being prepared for you!");
            }
            return;
        }
        task.cancel();
        player.sendMessage("<c>You have left the queue.");
    }

    public static void forget(UUID uuid) {
        SEARCHING.remove(uuid);
        Task task = QUEUED.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }
}
