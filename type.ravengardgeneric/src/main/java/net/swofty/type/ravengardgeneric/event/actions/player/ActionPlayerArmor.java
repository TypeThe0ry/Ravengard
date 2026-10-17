package net.swofty.type.ravengardgeneric.event.actions.player;

import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import net.swofty.type.generic.event.EventNodes;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.generic.event.phase.EventPhase;
import net.swofty.type.generic.event.phase.PhasedEvent;
import net.swofty.type.generic.utility.ScheduleUtility;
import net.swofty.type.ravengardgeneric.item.RavengardItemType;
import net.swofty.type.ravengardgeneric.item.attribute.RavengardItemAttributeHandler;
import net.swofty.type.ravengardgeneric.item.components.StandardItemComponent;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

import java.util.Map;

/**
 * Armour slots only take a piece made for them that the class can wear. Clicks reach the armour
 * slots in many ways (placing, shift, hotbar keys, dragging), so after any click in the player's own
 * inventory every armour slot is checked and anything that does not belong goes back in the bag.
 */
public class ActionPlayerArmor implements HypixelEventClass {
    private static final Map<Integer, EquipmentSlot> ARMOR_SLOTS = Map.of(
            PlayerInventoryUtils.HELMET_SLOT, EquipmentSlot.HELMET,
            PlayerInventoryUtils.CHESTPLATE_SLOT, EquipmentSlot.CHESTPLATE,
            PlayerInventoryUtils.LEGGINGS_SLOT, EquipmentSlot.LEGGINGS,
            PlayerInventoryUtils.BOOTS_SLOT, EquipmentSlot.BOOTS);

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = true, phase = EventPhase.GAMEPLAY)
    public void onInventoryClick(InventoryPreClickEvent event) {
        if (!(event.getPlayer() instanceof RavengardPlayer player)
                || event.getInventory() != player.getInventory()) {
            return;
        }
        ScheduleUtility.nextTick(() -> returnMisplacedArmor(player));
    }

    static void returnMisplacedArmor(RavengardPlayer player) {
        if (!player.isOnline()) return;
        ARMOR_SLOTS.forEach((slot, equipment) -> {
            ItemStack worn = player.getInventory().getItemStack(slot);
            if (worn.isAir() || fits(new RavengardItemAttributeHandler(worn).getType(), equipment, player.getRavengardClass())) return;
            player.getInventory().setItemStack(slot, ItemStack.AIR);
            if (!player.getInventory().addItemStack(worn)) {
                player.dropItem(worn);
            }
            player.sendMessage("<c>这件物品不能穿在这个位置！");
        });
    }

    static boolean fits(RavengardItemType type, EquipmentSlot equipment,
                        net.swofty.type.ravengardgeneric.classes.RavengardClass playerClass) {
        StandardItemComponent standard = type == null ? null : type.component(StandardItemComponent.class);
        return standard != null && standard.equipmentSlot() == equipment
                && type.usableBy(playerClass);
    }
}
