package net.swofty.type.ravengardgeneric.classes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.CustomData;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import net.swofty.commons.text.Text;
import net.swofty.type.ravengardgeneric.gui.RavengardItems;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

/**
 * Which registry items a class starts with, and where they go.
 *
 * <p>Slot 1 of the crafting grid is bound to the drop key and slot 4 to swap-offhand, matching the
 * captured Knight inventory. The pieces themselves are defined in configuration/ravengard/items.
 */
public record RavengardKit(String chestId, String legsId, String bootsId,
                           java.util.List<String> hotbarIds) {

    public RavengardKit(String chestId, String legsId, String bootsId) {
        this(chestId, legsId, bootsId, java.util.List.of());
    }

    public static final int SLOT_ABILITY_ONE = PlayerInventoryUtils.CRAFT_SLOT_1;
    public static final int SLOT_ABILITY_TWO = PlayerInventoryUtils.CRAFT_SLOT_4;
    public static final int SLOT_CHEST = PlayerInventoryUtils.CHESTPLATE_SLOT;
    public static final int SLOT_LEGS = PlayerInventoryUtils.LEGGINGS_SLOT;
    public static final int SLOT_BOOTS = PlayerInventoryUtils.BOOTS_SLOT;
    /**
     * The four accessory panes fill the top-left of the storage grid. Only the neck slot's
     * position is captured (the recorder truncates the player inventory at ten slots); the other
     * three follow it along the top row.
     */
    public static final int SLOT_NECK = 9;
    public static final int SLOT_EARRING = 10;
    public static final int SLOT_BELT = 11;
    public static final int SLOT_RING = 12;

    /**
     * Sprite offsets for an ability sitting in a crafting slot. Captured, because these are
     * inventory-screen positions rather than menu-grid ones, and they depend only on the slot.
     */
    private static final int OFFSET_ABILITY_ONE = 0x813500;
    private static final int OFFSET_ABILITY_TWO = 0x934700;

    public static RavengardKit forClass(RavengardClass value) {
        // Only the Knight and Sorcerer kits have been captured; the others reuse the Knight
        // shape until they are.
        return switch (value) {
            case KNIGHT -> new RavengardKit("TUNIC", "REINFORCED_PANTS", "OLD_BOOTS");
            case WARRIOR -> new RavengardKit("WEATHERED_HARNESS", "WEATHERED_PANTS", "OLD_BOOTS");
            case HUNTER -> new RavengardKit("TUNIC", "REINFORCED_PANTS", "OLD_BOOTS",
                    java.util.List.of("ASH_BOW"));
            case ASSASSIN -> new RavengardKit("TUNIC", "REINFORCED_PANTS", "OLD_BOOTS");
            case SORCERER -> new RavengardKit("WEATHERED_ROBE", "WEATHERED_PANTS", "OLD_BOOTS",
                    java.util.List.of("APPRENTICE_STAFF", "STARTER_BANDAGE"));
        };
    }

    /** Slot decides which key the ability is bound to, and with it the sprite offset and hint. */
    public static ItemStack ability(RavengardAbility value, int slot) {
        boolean first = slot == SLOT_ABILITY_ONE;
        return RavengardItems.button(value)
                .hoverColor(first ? OFFSET_ABILITY_ONE : OFFSET_ABILITY_TWO)
                .label(Text.of("Ability: {}", value.getDisplayName()))
                .lore(value.getHighlightedDescription())
                .blankLine()
                .lore(Text.of("<7>Cooldown: <e>{}", value.getCooldownText()))
                .blankLine()
                .lore(Text.of("<#f0f05c>Press {} to use!",
                        Component.keybind(first ? "key.drop" : "key.swapOffhand")))
                .build()
                .with(DataComponents.CUSTOM_DATA, new CustomData(CompoundBinaryTag.builder()
                        .putString("ravengard_ability", value.name())
                        .build()));
    }

    public static RavengardAbility abilityFrom(ItemStack stack) {
        if (stack == null || stack.isAir()) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        return RavengardAbility.fromKey(data.nbt().getString("ravengard_ability"));
    }

    public static RavengardAbility equippedAbility(RavengardPlayer player, int slot) {
        if (player == null) {
            return null;
        }
        int inventorySlot = slot == 0 ? SLOT_ABILITY_ONE : SLOT_ABILITY_TWO;
        return abilityFrom(player.getInventory().getItemStack(inventorySlot));
    }

    public static java.util.List<RavengardAbility> equippedAbilities(RavengardPlayer player) {
        java.util.ArrayList<RavengardAbility> result = new java.util.ArrayList<>(2);
        RavengardAbility first = equippedAbility(player, 0);
        RavengardAbility second = equippedAbility(player, 1);
        if (first != null) result.add(first);
        if (second != null) result.add(second);
        return java.util.List.copyOf(result);
    }
}
