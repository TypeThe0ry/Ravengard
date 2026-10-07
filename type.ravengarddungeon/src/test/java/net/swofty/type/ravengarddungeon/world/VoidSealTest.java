package net.swofty.type.ravengarddungeon.world;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.swofty.type.generic.world.HypixelWorldLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoidSealTest {
    @BeforeAll
    static void initializeMinestom() {
        MinecraftServer.init();
    }

    @AfterAll
    static void stopMinestom() {
        MinecraftServer.stopCleanly();
    }

    @Test
    void theMissingWestChunksAreWalledOffFromTheFloorBesideThem() {
        assertTrue(VoidSeal.sealed(-225, 0));
        assertTrue(VoidSeal.sealed(-209, -26));
        assertTrue(VoidSeal.sealed(-240, -32));
        assertFalse(VoidSeal.sealed(-224, 0));
        assertFalse(VoidSeal.sealed(-235, 0));
    }

    @Test
    void theOpenWestEdgeIsWalledOff() {
        assertTrue(java.util.stream.IntStream.rangeClosed(-256, -253).anyMatch(x -> VoidSeal.sealed(x, -36)));
    }

    @Test
    void sealsAreBuiltAsTheirChunksLoad() throws Exception {
        InstanceContainer container = MinecraftServer.getInstanceManager().createInstanceContainer();
        HypixelWorldLoader.loadFrom(Path.of("../configuration/ravengard/dungeon_1.polar"), container,
                MinecraftServer.getInstanceManager());
        VoidSeal.apply(container);
        container.loadChunk(-15, 0).join();
        container.loadChunk(-14, 0).join();
        long deadline = System.currentTimeMillis() + 5000;
        while (container.getBlock(-225, VoidSeal.WALL_MAX_Y, 0) != Block.BARRIER
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        for (int y = VoidSeal.WALL_MIN_Y; y <= VoidSeal.WALL_MAX_Y; y++) {
            assertEquals(Block.BARRIER, container.getBlock(-225, y, 0));
        }
        assertNotEquals(Block.BARRIER, container.getBlock(-224, 65, 0));
        assertNotEquals(Block.AIR, container.getBlock(-224, 64, 0));
        MinecraftServer.getInstanceManager().unregisterInstance(container);
    }
}
