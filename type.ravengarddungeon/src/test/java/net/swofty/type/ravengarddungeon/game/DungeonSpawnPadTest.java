package net.swofty.type.ravengarddungeon.game;

import net.swofty.type.ravengarddungeon.generator.RavengardDungeonGenerator;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonSpawnPadTest {
    @Test
    void officialSpawnPadsStayOnTheUntouchedCityBaseInsideTheMap() {
        for (long seed : new long[]{1L, 42L, 919191L, -7L, Long.MAX_VALUE, 123456789L}) {
            var generated = RavengardDungeonGenerator.generate(seed, 120);
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (var placement : generated.dungeon().getPlacements()) {
                minX = Math.min(minX, placement.originX());
                maxX = Math.max(maxX, placement.originX() + placement.getFootprintWidth());
                minZ = Math.min(minZ, placement.originZ());
                maxZ = Math.max(maxZ, placement.originZ() + placement.getFootprintDepth());
            }
            for (var pad : RavengardDungeonGame.SPAWN_POINTS) {
                int padX = pad.blockX();
                int padZ = pad.blockZ();
                long currentSeed = seed;
                assertTrue(padX >= minX && padX < maxX && padZ >= minZ && padZ < maxZ,
                        () -> "pad " + pad + " outside map bounds for seed " + currentSeed);
                for (var swap : generated.dungeon().getSwaps()) {
                    AtomicBoolean covered = new AtomicBoolean();
                    swap.forEachMaskCell((localX, localZ) -> {
                        if (swap.originX() + localX == padX && swap.originZ() + localZ == padZ) {
                            covered.set(true);
                        }
                    });
                    assertFalse(covered.get(),
                            () -> "pad " + pad + " lies in a swapped room for seed " + currentSeed);
                }
            }
        }
    }
}
