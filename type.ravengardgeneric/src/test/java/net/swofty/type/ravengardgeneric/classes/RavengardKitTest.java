package net.swofty.type.ravengardgeneric.classes;

import net.minestom.server.MinecraftServer;
import net.swofty.commons.YamlFileUtils;
import net.swofty.type.ravengardgeneric.item.RavengardItemConfigParser;
import net.swofty.type.ravengardgeneric.item.RavengardItemType;
import net.swofty.type.ravengardgeneric.item.components.StandardItemComponent;
import net.swofty.type.ravengardgeneric.item.statistics.RavengardItemStatistic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavengardKitTest {
    private static final Map<String, RavengardItemType> ITEMS = new HashMap<>();

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadItems() throws Exception {
        MinecraftServer.init();
        for (File file : YamlFileUtils.getYamlFiles(new File("../configuration/ravengard/items"))) {
            List<Map<String, Object>> items = (List<Map<String, Object>>) YamlFileUtils.loadYaml(file).get("items");
            if (items == null) continue;
            for (Map<String, Object> config : items) {
                RavengardItemType type = RavengardItemConfigParser.parse(config);
                ITEMS.put(type.getId(), type);
            }
        }
    }

    @Test
    void everyClassStartsArmedWithItemsItCanUse() {
        for (RavengardClass value : RavengardClass.values()) {
            RavengardKit kit = RavengardKit.forClass(value);
            List<String> pieces = new ArrayList<>(List.of(kit.chestId(), kit.legsId(), kit.bootsId()));
            pieces.addAll(kit.hotbarIds());
            if (kit.offhandId() != null) pieces.add(kit.offhandId());
            boolean armed = false;
            for (String id : pieces) {
                RavengardItemType type = ITEMS.get(id);
                assertNotNull(type, value + " kit item " + id);
                assertTrue(type.usableBy(value), value + " cannot use " + id);
                armed |= type.statistic(RavengardItemStatistic.DAMAGE) > 0
                        || type.statistic(RavengardItemStatistic.RANGED_DAMAGE) > 0;
            }
            assertTrue(armed, value + " starts without a weapon");
        }
    }

    @Test
    void theKnightCarriesItsBucklerInTheOffhand() {
        RavengardKit kit = RavengardKit.forClass(RavengardClass.KNIGHT);
        assertEquals("BUCKLER", kit.offhandId());
        assertEquals("SHIELD", ITEMS.get("BUCKLER").component(StandardItemComponent.class).getStandardItemType());
    }
}
