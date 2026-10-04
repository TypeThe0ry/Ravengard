package net.swofty.type.ravengarddungeon.events;

import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.swofty.commons.ServerType;
import net.swofty.type.generic.event.EventNodes;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.generic.event.phase.EventPhase;
import net.swofty.type.generic.event.phase.PhasedEvent;
import net.swofty.type.generic.redis.service.GameInformationHandler;
import net.swofty.type.generic.utility.ScheduleUtility;
import net.swofty.type.ravengarddungeon.game.DungeonInstanceRegistry;
import net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer;
import org.tinylog.Logger;

public class ActionPlayerJoin implements HypixelEventClass {
    private static final long ASSIGNMENT_MAX_AGE_MILLIS = 120_000;

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = false, phase = EventPhase.CONNECT)
    public void run(AsyncPlayerConfigurationEvent event) {
        RavengardDungeonPlayer player = (RavengardDungeonPlayer) event.getPlayer();
        if (!GameInformationHandler.isFresh(player.getUuid(), ASSIGNMENT_MAX_AGE_MILLIS)) {
            GameInformationHandler.game.remove(player.getUuid());
        }
        String assignedGameId = GameInformationHandler.game.get(player.getUuid());
        if (assignedGameId != null) {
            DungeonInstanceRegistry.DungeonInstance assigned = resolve(assignedGameId);
            if (assigned != null) {
                if (!DungeonInstanceRegistry.tryReserve(assigned, player.getUuid())) {
                    Logger.warn("Assigned Ravengard dungeon {} expired before {} completed configuration",
                            assignedGameId, player.getUsername());
                    ScheduleUtility.delay(() -> joinFallbackWhenPlayReady(player, 0), 20);
                    return;
                }
                try {
                    assigned.whenReady().join();
                    event.setSpawningInstance(assigned.getInstance());
                    player.setRespawnPoint(assigned.getGame().getSpawnPoint(0));
                    Logger.info("Selected dynamic Ravengard dungeon {} as spawning instance for {}",
                            assigned.getGameId(), player.getUsername());
                    return;
                } catch (RuntimeException exception) {
                    DungeonInstanceRegistry.releaseReservation(assigned, player.getUuid());
                    Logger.error(exception, "Failed preparing assigned Ravengard dungeon {} for {}",
                            assignedGameId, player.getUsername());
                }
            }
        }
        ScheduleUtility.delay(() -> joinFallbackWhenPlayReady(player, 0), 20);
    }

    private static DungeonInstanceRegistry.DungeonInstance resolve(String assignedGameId) {
        try {
            return DungeonInstanceRegistry.get(java.util.UUID.fromString(assignedGameId));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static void joinFallbackWhenPlayReady(RavengardDungeonPlayer player, int waitedTicks) {
        if (!player.isOnline()) return;
        if (GameInformationHandler.game.containsKey(player.getUuid())) {
            if (waitedTicks < 400) {
                player.scheduler().scheduleNextTick(() -> joinFallbackWhenPlayReady(player, waitedTicks + 1));
                return;
            }
            Logger.warn("Dungeon assignment for {} was never consumed, dropping it", player.getUsername());
            GameInformationHandler.game.remove(player.getUuid());
        }
        for (var instance : net.swofty.type.ravengarddungeon.game.DungeonInstanceRegistry.all()) {
            if (instance.getPlayers().contains(player.getUuid())) return;
        }
        var connection = player.getPlayerConnection();
        if (connection.getClientState() != net.minestom.server.network.ConnectionState.PLAY
                || connection.getServerState() != net.minestom.server.network.ConnectionState.PLAY) {
            if (waitedTicks < 400) {
                player.scheduler().scheduleNextTick(() -> joinFallbackWhenPlayReady(player, waitedTicks + 1));
            }
            return;
        }
        player.sendMessage("<c>未找到有效的地牢分配，正在返回天穹余烬大厅。");
        player.sendTo(ServerType.RAVENGARD_LOBBY);
    }
}
