package net.swofty.type.ravengarddungeon.game;

import net.minestom.server.instance.block.Block;
import net.swofty.type.ravengarddungeon.generator.DungeonTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavengardSpawnPointTest {
    @Test
    void officialSpawnPadsAreStandableAndClear() {
        DungeonTemplate template = DungeonTemplate.get();
        assertEquals(3, RavengardDungeonGame.SPAWN_POINTS.size());
        assertEquals(List.of(
                        new net.minestom.server.coordinate.Pos(-188.5, 66, -134.5),
                        new net.minestom.server.coordinate.Pos(-188.5, 66, -81.5),
                        new net.minestom.server.coordinate.Pos(-187.5, 66, -26.5)),
                RavengardDungeonGame.SPAWN_POINTS);
        for (var spawn : RavengardDungeonGame.SPAWN_POINTS) {
            int x = spawn.blockX();
            int y = spawn.blockY();
            int z = spawn.blockZ();
            assertTrue(template.blockAt(x, y - 1, z).solid(),
                    () -> "spawn has no solid floor at " + x + "," + y + "," + z
                            + " floor=" + template.blockAt(x, y - 1, z).key()
                            + " feet=" + template.blockAt(x, y, z).key()
                            + " head=" + template.blockAt(x, y + 1, z).key());
            assertEquals(Block.AIR, template.blockAt(x, y, z));
            assertEquals(Block.AIR, template.blockAt(x, y + 1, z));
        }
    }
}
