package net.swofty.type.ravengarddungeon.interactables;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
