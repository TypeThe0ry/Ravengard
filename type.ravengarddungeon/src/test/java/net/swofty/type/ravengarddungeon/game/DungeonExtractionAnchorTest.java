package net.swofty.type.ravengarddungeon.game;

import net.minestom.server.instance.block.Block;
import net.swofty.dungeons.ravengard.RavengardDungeon.RoomPlacement;
import net.swofty.type.ravengarddungeon.generator.DungeonTemplate;
import net.swofty.type.ravengarddungeon.generator.RavengardDungeonGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonExtractionAnchorTest {
    @Test
    void everyOfficialCyanPlacementProducesOneStandableDynamicAnchor() {
        for (long seed : new long[]{1L, 919191L, -7L, Long.MAX_VALUE}) {
            var generated = RavengardDungeonGenerator.generate(seed, 120);
            var anchors = DungeonInstanceRegistry.extractionAnchors(generated);
            var cyan = generated.dungeon().getPlacements().stream()
                    .filter(placement -> "cyan".equals(placement.room().getColor()))
                    .toList();

            assertEquals(10, cyan.size());
            assertEquals(cyan.size(), anchors.size());
            assertEquals(anchors.size(), new HashSet<>(anchors).size());

            for (int index = 0; index < cyan.size(); index++) {
                RoomPlacement placement = cyan.get(index);
                var anchor = anchors.get(index);
                float expectedYaw = switch (placement.rotation()) {
                    case CLOCKWISE_90 -> 90f;
                    case CLOCKWISE_180 -> 180f;
                    case CLOCKWISE_270 -> 270f;
                    case NONE -> 0f;
                };
                assertEquals(expectedYaw, anchor.yaw());
                int localX = (int) Math.floor(anchor.x()) - placement.originX();
                int localZ = (int) Math.floor(anchor.z()) - placement.originZ();
                int[] source = placement.toSourceFrame(localX, localZ);
                assertTrue(inRoom(placement, source[0], source[1]));

                int worldX = placement.originX() + localX;
                int worldZ = placement.originZ() + localZ;
                int feetY = (int) anchor.y();
                DungeonTemplate template = DungeonTemplate.get();
                assertTrue(template.blockAt(worldX, feetY - 1, worldZ).solid());
                assertNotEquals(net.minestom.server.instance.block.Block.BARRIER,
                        template.blockAt(worldX, feetY - 1, worldZ));
                assertEquals(Block.AIR, template.blockAt(worldX, feetY, worldZ));
                assertEquals(Block.AIR, template.blockAt(worldX, feetY + 1, worldZ));
                assertEquals(expectedFeetY(template, worldX, worldZ), feetY);
            }
        }
    }

    @Test
    void staticOfficialMapProducesOneAnchorForEveryCyanRoom() {
        var anchors = DungeonInstanceRegistry.officialExtractionAnchors();
        var cyanRooms = RavengardDungeonGenerator.getCatalog().getRooms().stream()
                .filter(room -> "cyan".equals(room.getColor()))
                .toList();

        assertEquals(10, cyanRooms.size());
        assertEquals(cyanRooms.size(), anchors.size());
        assertEquals(anchors.size(), new HashSet<>(anchors).size());
        DungeonTemplate template = DungeonTemplate.get();
        for (int index = 0; index < cyanRooms.size(); index++) {
            var room = cyanRooms.get(index);
            var source = DungeonInstanceRegistry.cyanAnchorSource(room);
            var anchor = anchors.get(index);
            int worldX = (int) Math.floor(anchor.x());
            int worldZ = (int) Math.floor(anchor.z());
            assertEquals(room.getMinX() + source.x(), worldX);
            assertEquals(room.getMinZ() + source.z(), worldZ);
            int feetY = (int) anchor.y();
            assertTrue(template.blockAt(worldX, feetY - 1, worldZ).solid());
            assertNotEquals(Block.BARRIER, template.blockAt(worldX, feetY - 1, worldZ));
            assertEquals(Block.AIR, template.blockAt(worldX, feetY, worldZ));
            assertEquals(Block.AIR, template.blockAt(worldX, feetY + 1, worldZ));
        }
    }

    @Test
    void anchorsCoverAllRoomRotationsWithoutLeavingTheMask() {
        var room = RavengardDungeonGenerator.getCatalog().getRooms().stream()
                .filter(candidate -> "cyan".equals(candidate.getColor()))
                .findFirst().orElseThrow();
        var source = DungeonInstanceRegistry.cyanAnchorSource(room);
        Set<String> rotations = new HashSet<>();
        for (var rotation : net.swofty.dungeons.ravengard.Rotation.values()) {
            RoomPlacement placement = new RoomPlacement(room, rotation, 101, -203);
                double[] local = placement.toPlacementFrame(source.x(), source.z());
                local[0] += 0.5;
                local[1] += 0.5;
            int[] recovered = placement.toSourceFrame(
                    (int) Math.floor(local[0]), (int) Math.floor(local[1]));
            rotations.add(rotation.name());
            assertTrue(inRoom(placement, recovered[0], recovered[1]));
        }
        assertEquals(Set.of("NONE", "CLOCKWISE_90", "CLOCKWISE_180", "CLOCKWISE_270"), rotations);
    }

    @Test
    void everyOfficialCyanRoomKeepsItsAnchorInMaskAcrossAllRotations() {
        var cyanRooms = RavengardDungeonGenerator.getCatalog().getRooms().stream()
                .filter(candidate -> "cyan".equals(candidate.getColor()))
                .toList();
        assertEquals(10, cyanRooms.size());
        for (var room : cyanRooms) {
            var source = DungeonInstanceRegistry.cyanAnchorSource(room);
            Set<String> mask = new HashSet<>();
            room.getMask().forEach(run -> {
                for (int x = run[1]; x <= run[2]; x++) mask.add(x + ":" + run[0]);
            });
            for (var rotation : net.swofty.dungeons.ravengard.Rotation.values()) {
                RoomPlacement placement = new RoomPlacement(room, rotation, 101, -203);
                double[] local = placement.toPlacementFrame(source.x(), source.z());
                local[0] += 0.5;
                local[1] += 0.5;
                int[] recovered = placement.toSourceFrame(
                        (int) Math.floor(local[0]), (int) Math.floor(local[1]));
                assertTrue(inRoom(placement, recovered[0], recovered[1]),
                        room.getId() + " anchor escaped mask at " + rotation);
                boolean hasTwoByTwo = mask.stream().map(key -> key.split(":"))
                        .anyMatch(parts -> {
                            int x = Integer.parseInt(parts[0]);
                            int z = Integer.parseInt(parts[1]);
                            return mask.contains((x + 1) + ":" + z)
                                    && mask.contains(x + ":" + (z + 1))
                                    && mask.contains((x + 1) + ":" + (z + 1));
                        });
                if (hasTwoByTwo && !room.getId().equals("room_22") && !room.getId().equals("room_46")) {
                    assertTrue(inMask(placement, recovered[0] + 1, recovered[1]));
                    assertTrue(inMask(placement, recovered[0], recovered[1] + 1));
                    assertTrue(inMask(placement, recovered[0] + 1, recovered[1] + 1));
                }
            }
        }
    }

    @Test
    void cyanAnchorsPreferAContinuousTwoByTwoFootprintWhenAvailable() {
        var cyanRooms = RavengardDungeonGenerator.getCatalog().getRooms().stream()
                .filter(candidate -> "cyan".equals(candidate.getColor()))
                .toList();
        assertEquals(10, cyanRooms.size());
        for (var room : cyanRooms) {
            var source = DungeonInstanceRegistry.cyanAnchorSource(room);
            Set<String> mask = new HashSet<>();
            room.getMask().forEach(run -> {
                for (int x = run[1]; x <= run[2]; x++) mask.add(x + ":" + run[0]);
            });
            if (outlineOnly(room)) continue;
            assertTrue(mask.contains(source.x() + ":" + source.z()));
            boolean hasTwoByTwo = mask.stream().map(key -> key.split(":")).anyMatch(parts -> {
                int x = Integer.parseInt(parts[0]);
                int z = Integer.parseInt(parts[1]);
                return mask.contains((x + 1) + ":" + z)
                        && mask.contains(x + ":" + (z + 1))
                        && mask.contains((x + 1) + ":" + (z + 1));
            });
            if (hasTwoByTwo && !room.getId().equals("room_22") && !room.getId().equals("room_46")) {
                assertTrue(mask.contains((source.x() + 1) + ":" + source.z())
                        && mask.contains(source.x() + ":" + (source.z() + 1))
                        && mask.contains((source.x() + 1) + ":" + (source.z() + 1)));
            }
        }
    }

    @Test
    void outlineOnlyMasksPutThePortalInsideTheRoomTheyEnclose() {
        for (var room : RavengardDungeonGenerator.getCatalog().getRooms()) {
            if (!outlineOnly(room)) continue;
            var anchor = DungeonInstanceRegistry.cyanAnchorSource(room);
            boolean onOutline = room.getMask().stream()
                    .anyMatch(run -> run[0] == anchor.z() && anchor.x() >= run[1] && anchor.x() <= run[2]);

            assertTrue(!onOutline, room.getId() + " portal sits in the wall line at " + anchor);
            assertTrue(anchor.x() > 0 && anchor.x() < room.getWidth() - 1, anchor.toString());
            assertTrue(anchor.z() > 0 && anchor.z() < room.getDepth() - 1, anchor.toString());
        }
    }

    @Test
    void everyExtractionPortalStandsClearOfWallsOnALevelFloor() {
        DungeonTemplate template = DungeonTemplate.get();
        List<String> crowded = new ArrayList<>();
        for (var anchor : DungeonInstanceRegistry.officialExtractionAnchors()) {
            int x = anchor.blockX(), feetY = anchor.blockY(), z = anchor.blockZ();
            for (int dx = -1; dx <= 1; dx++) {
                if (!template.blockAt(x + dx, feetY - 1, z).registry().collisionShape()
                        .isFaceFull(net.minestom.server.instance.block.BlockFace.TOP)) {
                    crowded.add(anchor + " uneven floor at " + dx);
                }
                for (int dy = 0; dy < 3; dy++) {
                    if (template.blockAt(x + dx, feetY + dy, z).registry().collisionShape().relativeEnd().y() > 0) {
                        crowded.add(anchor + " model inside " + template.blockAt(x + dx, feetY + dy, z).name()
                                + " at " + dx + "," + dy);
                    }
                }
            }
        }

        assertEquals(List.of(), crowded);
    }

    private static boolean inMask(RoomPlacement placement, int x, int z) {
        return placement.room().getMask().stream()
                .anyMatch(run -> run[0] == z && x >= run[1] && x <= run[2]);
    }

    private static boolean outlineOnly(net.swofty.dungeons.ravengard.RavengardRoomCatalog.DungeonRoom room) {
        return room.getId().equals("room_22") || room.getId().equals("room_46");
    }

    private static boolean inRoom(RoomPlacement placement, int x, int z) {
        var room = placement.room();
        if (!outlineOnly(room)) return inMask(placement, x, z);
        return x >= 0 && z >= 0 && x < room.getWidth() && z < room.getDepth();
    }

    private static int expectedFeetY(DungeonTemplate template, int worldX, int worldZ) {
        for (int y = 72; y >= 60; y--) {
            if (template.blockAt(worldX, y, worldZ).solid()
                    && template.blockAt(worldX, y + 1, worldZ) == Block.AIR
                    && template.blockAt(worldX, y + 2, worldZ) == Block.AIR) {
                return y + 1;
            }
        }
        throw new AssertionError("No standable ground at " + worldX + "," + worldZ);
    }

}
