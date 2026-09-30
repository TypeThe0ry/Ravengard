package net.swofty.type.ravengarddungeon.events;

import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerEntityInteractEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.swofty.type.generic.event.EventNodes;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.generic.event.phase.EventPhase;
import net.swofty.type.generic.event.phase.PhasedEvent;
import net.swofty.type.ravengarddungeon.interactables.InteractableRegistry;

public class ActionPlayerInteractables implements HypixelEventClass {

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = true, phase = EventPhase.GAMEPLAY)
    public void onInteract(PlayerEntityInteractEvent event) {
        if (event.getHand() != PlayerHand.MAIN) return;
        var resolved = InteractableRegistry.byInteraction(event.getTarget());
        if (resolved != null) {
            org.tinylog.Logger.debug("Interact click: player={} entity={} type={} class={} position={} distance={}",
                    event.getPlayer().getUsername(), event.getTarget().getEntityId(),
                    event.getTarget().getEntityType(), resolved.getClass().getSimpleName(),
                    event.getTarget().getPosition(),
                    Math.round(event.getPlayer().getPosition().distance(event.getTarget().getPosition()) * 100.0) / 100.0);
        }
        InteractableRegistry.onClick(event.getPlayer(), event.getTarget());
        if (resolved == null) {
            // The visible extraction is an ITEM_DISPLAY; the hitbox is a separate
            // invisible INTERACTION entity, so clients may target the display.
            InteractableRegistry.onUse(event.getPlayer(), event.getTarget());
        }
    }

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = true, phase = EventPhase.GAMEPLAY)
    public void onUse(PlayerUseItemEvent event) {
        if (event.getHand() != PlayerHand.MAIN) return;
        InteractableRegistry.onUse(event.getPlayer());
    }

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = true, phase = EventPhase.GAMEPLAY)
    public void onBlockInteract(PlayerBlockInteractEvent event) {
        if (event.getHand() != PlayerHand.MAIN) return;
        InteractableRegistry.onUse(event.getPlayer());
    }

    @PhasedEvent(node = EventNodes.INVENTORY, requireDataLoaded = false, phase = EventPhase.GAMEPLAY)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory() instanceof net.minestom.server.inventory.Inventory inventory) {
            InteractableRegistry.onInventoryClose(event.getPlayer(), inventory);
        }
    }

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = false, phase = EventPhase.GAMEPLAY)
    public void onRelease(PlayerCancelItemUseEvent event) {
        InteractableRegistry.onRightClickReleased(event.getPlayer());
    }

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = false, phase = EventPhase.DISCONNECT)
    public void onDisconnect(PlayerDisconnectEvent event) {
        InteractableRegistry.clearPlayer(event.getPlayer());
    }
}
