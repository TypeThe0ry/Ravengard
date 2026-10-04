package net.swofty.type.ravengarddungeon.events;

import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;
import net.swofty.type.generic.event.EventNodes;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.generic.event.phase.EventPhase;
import net.swofty.type.generic.event.phase.PhasedEvent;
import net.swofty.type.generic.redis.service.GameInformationHandler;
import net.swofty.type.ravengarddungeon.game.DungeonInstanceRegistry;
import net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer;

import java.util.UUID;


public class ActionPlayerDungeonAssign implements HypixelEventClass {

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = false, phase = EventPhase.GAMEPLAY)
    public void onSpawn(PlayerSpawnEvent event) {
        if (!event.isFirstSpawn() || !(event.getPlayer() instanceof RavengardDungeonPlayer player)) {
            return;
        }
        String assignedGameId = GameInformationHandler.game.remove(player.getUuid());
        if (assignedGameId == null) {
            DungeonInstanceRegistry.DungeonInstance configured = findConfiguredInstance(player.getInstance());
            if (configured != null) {
                DungeonInstanceRegistry.sendPlayerIn(player, configured);
                return;
            }
            player.sendMessage("<c>未找到地牢分配，正在返回天穹余烬大厅。");
            player.sendTo(net.swofty.commons.ServerType.RAVENGARD_LOBBY);
            return;
        }
        DungeonInstanceRegistry.DungeonInstance instance;
        try {
            instance = DungeonInstanceRegistry.get(UUID.fromString(assignedGameId));
        } catch (IllegalArgumentException exception) {
            player.sendMessage("<c>地牢分配无效，正在返回天穹余烬大厅。");
            player.sendTo(net.swofty.commons.ServerType.RAVENGARD_LOBBY);
            return;
        }
        if (instance == null) {
            player.sendMessage("<c>你的地牢实例已不存在。");
            player.sendTo(net.swofty.commons.ServerType.RAVENGARD_LOBBY);
            return;
        }
        DungeonInstanceRegistry.sendPlayerIn(player, instance);
    }

    private static DungeonInstanceRegistry.DungeonInstance findConfiguredInstance(Instance instance) {
        if (instance == null) return null;
        for (DungeonInstanceRegistry.DungeonInstance candidate : DungeonInstanceRegistry.all()) {
            if (candidate.getInstance() == instance) return candidate;
        }
        return null;
    }

}
