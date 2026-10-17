package net.swofty.type.ravengardgeneric.event.actions.player;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.EquipmentSlot;
import net.swofty.type.ravengardgeneric.classes.RavengardClass;
import net.swofty.type.ravengardgeneric.item.RavengardItemConfigParser;
import net.swofty.type.ravengardgeneric.item.RavengardItemType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionPlayerArmorTest {
    private static RavengardItemType tunic;
    private static RavengardItemType sword;

    @BeforeAll
    static void load() {
        MinecraftServer.init();
        tunic = RavengardItemConfigParser.parse(Map.of(
                "id", "TEST_TUNIC", "rarity", "COMMON", "material", "leather_chestplate",
                "components", List.of(
                        Map.of("id", "STANDARD_ITEM", "standard_item_type", "CHESTPLATE"),
                        Map.of("id", "CLASS_RESTRICTED", "classes", List.of("KNIGHT", "SORCERER")))));
        sword = RavengardItemConfigParser.parse(Map.of(
                "id", "TEST_SWORD", "rarity", "COMMON", "material", "blaze_rod",
                "components", List.of(Map.of("id", "STANDARD_ITEM", "standard_item_type", "SWORD"))));
    }

    @Test
    void aChestpieceOnlyGoesOnTheChest() {
        assertTrue(ActionPlayerArmor.fits(tunic, EquipmentSlot.CHESTPLATE, RavengardClass.KNIGHT));
        assertFalse(ActionPlayerArmor.fits(tunic, EquipmentSlot.BOOTS, RavengardClass.KNIGHT));
        assertFalse(ActionPlayerArmor.fits(tunic, EquipmentSlot.HELMET, RavengardClass.KNIGHT));
    }

    @Test
    void armourOfAnotherClassIsRefused() {
        assertFalse(ActionPlayerArmor.fits(tunic, EquipmentSlot.CHESTPLATE, RavengardClass.HUNTER));
    }

    @Test
    void aWeaponOrAPlainItemIsNotArmour() {
        assertFalse(ActionPlayerArmor.fits(sword, EquipmentSlot.CHESTPLATE, RavengardClass.KNIGHT));
        assertFalse(ActionPlayerArmor.fits(null, EquipmentSlot.BOOTS, RavengardClass.KNIGHT));
    }
}
