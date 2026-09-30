package net.swofty.type.ravengarddungeon.generator;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavengardSpawnPointsTest {
    @Test
    void generatedDungeonUsesThreeDistinctStartRoomSpawnPoints() {
        var generated = RavengardDungeonGenerator.generate(0x51A7E5L, 24);
        var points = RavengardDungeonGenerator.spawnPoints(generated);

        assertEquals(3, points.size());
        assertEquals(3, new HashSet<>(points).size());
        for (var point : points) {
            assertEquals(67.0, point.y());
            assertTrue(point.x() >= generated.dungeon().getStartRoom().originX());
            assertTrue(point.z() >= generated.dungeon().getStartRoom().originZ());
            assertTrue(point.x() <= generated.dungeon().getStartRoom().originX()
                    + generated.dungeon().getStartRoom().getFootprintWidth());
            assertTrue(point.z() <= generated.dungeon().getStartRoom().originZ()
                    + generated.dungeon().getStartRoom().getFootprintDepth());
        }
        var doorObjects = generated.objects().stream()
                .filter(object -> "door".equals(object.category()) || "doors".equals(object.category())
                        || (object.type() != null && (object.type().contains("door") || object.type().contains("gate"))))
                .toList();
        for (var point : points) {
            assertTrue(doorObjects.stream().noneMatch(door -> {
                double dx = door.x() - point.x();
                double dz = door.z() - point.z();
                return dx * dx + dz * dz < 9.0;
            }), () -> "spawn overlaps a closed door at " + point);
        }
    }

    @Test
    void officialIronGateMarkersAreAlsoExcludedFromFirstSpawn() {
        var generated = RavengardDungeonGenerator.generate(0x51A7E5L, 24);
        var gate = new RavengardDungeonGenerator.PlacedObject("door", "iron_gate",
                generated.spawn().x(), generated.spawn().y() + 1.75, generated.spawn().z() - 1.0, 0.0);
        var points = RavengardDungeonGenerator.spawnPoints(generated, java.util.List.of(gate));
        assertTrue(points.stream().noneMatch(point ->
                Math.hypot(point.x() - gate.x(), point.z() - gate.z()) < 3.0));
    }
}
