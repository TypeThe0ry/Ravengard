package net.swofty.type.ravengarddungeon.interactables;

import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.instance.Instance;
import net.swofty.type.generic.world.HypixelWorldLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonChestCaptureTest {
    private static final net.minestom.server.tag.Tag<Boolean> CONVERTED =
            net.minestom.server.tag.Tag.Boolean("ravengard_chest_converted").defaultValue(false);

    @Test
    void everyCapturedChestBottomBecomesAChestWithItsLid() throws Exception {
        MinecraftServer.init();
        var manager = MinecraftServer.getInstanceManager();
        var container = manager.createInstanceContainer();
        Instance instance = HypixelWorldLoader.loadFrom(
                Path.of("..", "configuration", "ravengard", "dungeon_1.polar"), container, manager);
        try {
            assertEveryBottomHasItsLid(instance);
        } finally {
            manager.unregisterInstance(instance);
            manager.unregisterInstance(container);
        }
    }

    private static void assertEveryBottomHasItsLid(Instance instance) {
        int converted = DungeonChest.convertDecorChests(instance, 1);

        List<Entity> bottoms = chestDisplays(instance, "_bottom");
        List<Entity> tops = chestDisplays(instance, "_top");
        assertEquals(454, bottoms.size());
        assertEquals(454, converted);
        assertTrue(bottoms.stream().allMatch(bottom -> bottom.getTag(CONVERTED)));
        for (Entity bottom : bottoms) {
            String lid = model(bottom).replace("_bottom", "_top");
            assertTrue(tops.stream().anyMatch(top -> top.getTag(CONVERTED) && lid.equals(model(top))
                    && top.getPosition().distance(bottom.getPosition()) < 1.1), bottom.getPosition().toString());
        }
        Pos throneRoom = new Pos(-91.5, 63.5, 1.5);
        assertTrue(tops.stream().anyMatch(top -> top.getPosition().samePoint(throneRoom)
                && model(top).endsWith("silver/silver_top")));
    }

    private static List<Entity> chestDisplays(Instance instance, String suffix) {
        return instance.getEntities().stream()
                .filter(entity -> !entity.isRemoved())
                .filter(entity -> {
                    String model = model(entity);
                    return model != null && model.contains(":item/gameplay/chests/") && model.endsWith(suffix);
                })
                .toList();
    }

    private static String model(Entity entity) {
        if (!(entity.getEntityMeta() instanceof ItemDisplayMeta meta)) return null;
        var stack = meta.getItemStack();
        if (stack == null || stack.isAir()) return null;
        var model = stack.get(DataComponents.ITEM_MODEL);
        return model == null ? null : model.toString();
    }
}
