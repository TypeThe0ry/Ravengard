package net.swofty.type.ravengardgeneric.event.actions.player;

import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.swofty.commons.ServerType;
import net.swofty.type.generic.HypixelConst;
import net.swofty.type.generic.event.EventNodes;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.generic.event.phase.EventPhase;
import net.swofty.type.generic.event.phase.PhasedEvent;
import net.swofty.type.generic.redis.RedisOriginServer;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;
import org.tinylog.Logger;

public class ActionPlayerJoin implements HypixelEventClass {

    @PhasedEvent(node = EventNodes.PLAYER, phase = EventPhase.GAMEPLAY)
    public void run(AsyncPlayerConfigurationEvent event) {
        RavengardPlayer player = (RavengardPlayer) event.getPlayer();
        ServerType destination = HypixelConst.getTypeLoader().getType();
        ServerType origin = RedisOriginServer.consume(player.getUuid());

        if (origin != null) {
            player.setOriginServer(origin);
        }
        if (isRavengardInternalTransfer(origin, destination)) {
            event.setSendRegistryData(false);
        }
        event.setSpawningInstance(HypixelConst.getEmptyInstance());
        player.setRespawnPoint(HypixelConst.getTypeLoader()
                .getLoaderValues()
                .spawnPosition()
                .apply(player.getOriginServer())
        );

        Logger.info("Player {} joined Ravengard from {}", player.getUsername(), player.getOriginServer());
    }

    static boolean isRavengardInternalTransfer(ServerType origin, ServerType destination) {
        return isRavengard(origin) && isRavengard(destination);
    }

    private static boolean isRavengard(ServerType type) {
        return type == ServerType.RAVENGARD_LOBBY || type == ServerType.RAVENGARD_DUNGEON;
    }
}
