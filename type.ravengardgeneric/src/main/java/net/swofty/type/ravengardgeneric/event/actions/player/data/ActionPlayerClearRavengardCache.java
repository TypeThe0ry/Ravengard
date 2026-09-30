package net.swofty.type.ravengardgeneric.event.actions.player.data;

import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.swofty.type.generic.event.EventNodes;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.generic.event.phase.EventPhase;
import net.swofty.type.generic.event.phase.PhasedEvent;

public class ActionPlayerClearRavengardCache implements HypixelEventClass {

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = false, phase = EventPhase.GAMEPLAY)
    public void run(PlayerDisconnectEvent event) {
        // closing the open view hands anything it was holding back before the profile is saved
        if (event.getPlayer() instanceof net.swofty.type.generic.user.HypixelPlayer player) {
            net.swofty.type.generic.gui.v2.ViewNavigator.remove(player);
        }
        if (event.getPlayer() instanceof net.swofty.type.ravengardgeneric.user.RavengardPlayer player) {
            net.swofty.type.ravengardgeneric.profile.RavengardProfiles.returnCursorItem(player);
        }
        net.swofty.type.ravengardgeneric.queue.RavengardQueue.forget(event.getPlayer().getUuid());
        net.swofty.type.ravengardgeneric.entity.animation.AnimatedRavengardNPC.forget(event.getPlayer().getUuid());
        net.swofty.type.ravengardgeneric.region.RavengardRegionTracker.forget(event.getPlayer().getUuid());
        if (net.swofty.type.generic.HypixelConst.getTypeLoader().getType()
                != net.swofty.commons.ServerType.RAVENGARD_DUNGEON) {
            net.swofty.type.ravengardgeneric.profile.RavengardRunState.abort(event.getPlayer().getUuid());
        }
    }
}
