package net.swofty.type.ravengardgeneric.event.actions.player;

import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.inventory.PlayerInventory;
import net.swofty.type.generic.event.EventNodes;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.generic.event.phase.EventPhase;
import net.swofty.type.generic.event.phase.PhasedEvent;
import net.swofty.type.ravengardgeneric.profile.RavengardProfiles;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

/** Closing the own inventory with an item on the cursor puts it back, as the vanilla server does. */
public class ActionPlayerInventoryClose implements HypixelEventClass {

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = false, phase = EventPhase.GAMEPLAY)
    public void run(InventoryCloseEvent event) {
        if (event.getInventory() instanceof PlayerInventory
                && event.getPlayer() instanceof RavengardPlayer player) {
            RavengardProfiles.returnCursorItem(player);
        }
    }
}
