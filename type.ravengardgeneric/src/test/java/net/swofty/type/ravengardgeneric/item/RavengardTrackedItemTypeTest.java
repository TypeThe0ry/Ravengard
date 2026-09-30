package net.swofty.type.ravengardgeneric.item;

import net.minestom.server.MinecraftServer;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.swofty.type.ravengardgeneric.item.attribute.RavengardItemAttributeHandler;
import net.swofty.type.ravengardgeneric.item.attribute.attributes.ItemAttributeItemId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RavengardTrackedItemTypeTest {
    @BeforeAll
    @SuppressWarnings("unchecked")
    static void registerItems() throws Exception {
        MinecraftServer.init();
        Field items = RavengardItemRegistry.class.getDeclaredField("ITEMS");
        items.setAccessible(true);
        Map<String, RavengardItemType> registry = (Map<String, RavengardItemType>) items.get(null);
        for (Map<String, Object> config : java.util.List.<Map<String, Object>>of(
                Map.of("id", "IRON_SWORD", "rarity", "COMMON", "material", "stick", "value", 3),
                Map.of("id", "UNCOMMON_IRON_SWORD", "tracked_id", "UNCOMMON_IRON_SWORD",
                        "rarity", "UNCOMMON", "material", "stick", "value", 5))) {
            RavengardItemType type = RavengardItemConfigParser.parse(config);
            registry.put(type.getId(), type);
        }
    }

    @Test
    void anItemWithItsOwnTrackedIdResolvesToItselfNotItsCommonNamesake() {
        assertEquals("UNCOMMON_IRON_SWORD", typeOf("UNCOMMON_IRON_SWORD").getId());
    }

    @Test
    void rarityPrefixedIdsStillResolveToTheBaseItem() {
        assertEquals("IRON_SWORD", typeOf("COMMON_IRON_SWORD").getId());
    }

    private static RavengardItemType typeOf(String trackedId) {
        ItemStack.Builder builder = ItemStack.builder(Material.STICK);
        new ItemAttributeItemId().apply(builder, trackedId);
        return new RavengardItemAttributeHandler(builder.build()).getType();
    }
}
