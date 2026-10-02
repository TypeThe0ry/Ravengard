package net.swofty.type.ravengarddungeon.interactables;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonSatchelGroundTest {
    private InstanceContainer instance;

    @BeforeAll
    static void start() {
        MinecraftServer.init();
    }

    @AfterAll
    static void stop() {
        MinecraftServer.stopCleanly();
    }

    @BeforeEach
    void createInstance() {
        instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.loadChunk(0, 0).join();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                instance.setBlock(x, 60, z, Block.STONE);
            }
        }
    }

    @AfterEach
    void removeInstance() {
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void aBagDroppedInTheAirFallsToTheFloorBelow() {
        Pos grounded = DungeonSatchel.groundedAt(instance, new Pos(4.5, 66, 4.5));
        assertEquals(new Pos(4.5, 61, 4.5), grounded);
    }

    @Test
    void aBagDroppedInsideAWallMovesToTheNearestOpenColumn() {
        for (int y = 61; y <= 64; y++) instance.setBlock(4, y, 4, Block.STONE);
        Pos grounded = DungeonSatchel.groundedAt(instance, new Pos(4.5, 62, 4.5));
        assertEquals(61, grounded.y());
        assertEquals(1, Math.max(Math.abs(grounded.blockX() - 4), Math.abs(grounded.blockZ() - 4)));
    }

    @Test
    void satchelsCapturedWithTheMapAreClearedButDroppedOnesStay() {
        Entity capturedClosed = capturedDisplay("hypixel_ravengard:item/gameplay/lootbags/lootbag_2", 3.5);
        Entity capturedOpen = capturedDisplay("hypixel_ravengard:item/gameplay/lootbags/lootbag_2_open", 5.5);
        Entity barrel = capturedDisplay("hypixel_ravengard:item/decoration/barrels/barrel", 7.5);
        DungeonSatchel dropped = DungeonSatchel.spawn(instance, new Pos(10.5, 61, 10.5));

        assertEquals(2, DungeonSatchel.clearCapturedSatchels(instance));

        assertTrue(capturedClosed.isRemoved());
        assertTrue(capturedOpen.isRemoved());
        assertFalse(barrel.isRemoved());
        assertEquals(1, instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY)
                .filter(entity -> !entity.isRemoved() && entity != barrel)
                .count());
        dropped.remove();
    }

    private Entity capturedDisplay(String model, double x) {
        Entity display = new Entity(EntityType.ITEM_DISPLAY);
        display.editEntityMeta(ItemDisplayMeta.class,
                meta -> meta.setItemStack(DungeonInteractable.modelStack(model)));
        display.setInstance(instance, new Pos(x, 61.5, 4.5)).join();
        return display;
    }
}
