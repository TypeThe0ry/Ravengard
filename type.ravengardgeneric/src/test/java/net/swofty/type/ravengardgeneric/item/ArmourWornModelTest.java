package net.swofty.type.ravengardgeneric.item;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmourWornModelTest {
    private static final Path ITEMS = Path.of("../configuration/ravengard/items");
    private static final Path EQUIPMENT = Path.of("../configuration/resourcepacks/ravengard/assets/hypixel_ravengard/equipment");
    private static final Set<String> ARMOUR = Set.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");

    @Test
    @SuppressWarnings("unchecked")
    void everyArmourPieceHasAWornModelThePackCarries() throws Exception {
        List<String> missing = new ArrayList<>();
        int checked = 0;
        try (var files = Files.list(ITEMS)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".yml")).toList()) {
                Map<String, Object> data = net.swofty.commons.YamlFileUtils.loadYaml(file.toFile());
                for (Map<String, Object> item : (List<Map<String, Object>>) data.get("items")) {
                    List<Map<String, Object>> components = (List<Map<String, Object>>) item.getOrDefault("components", List.of());
                    boolean armour = components.stream().anyMatch(component -> "STANDARD_ITEM".equals(component.get("id"))
                            && ARMOUR.contains(String.valueOf(component.get("standard_item_type"))));
                    if (!armour) continue;
                    checked++;
                    String asset = components.stream().filter(component -> "EQUIPPABLE".equals(component.get("id")))
                            .map(component -> String.valueOf(component.get("asset_id"))).findFirst().orElse(null);
                    if (asset == null || !Files.isRegularFile(EQUIPMENT.resolve(asset.substring(asset.indexOf(':') + 1) + ".json"))) {
                        missing.add(String.valueOf(item.get("id")));
                    }
                }
            }
        }
        assertTrue(checked > 40, "only " + checked + " armour pieces found");
        assertEquals(List.of(), missing);
    }
}
