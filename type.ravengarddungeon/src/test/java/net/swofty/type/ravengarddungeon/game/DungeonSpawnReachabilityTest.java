package net.swofty.type.ravengarddungeon.game;

import com.google.gson.Gson;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig;
import net.swofty.type.ravengarddungeon.generator.DungeonTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonSpawnReachabilityTest {
    private static final int MAP_RADIUS = 320;
    private static final int DOOR_RADIUS = 2;
    private static final int MAXIMUM_DROP = 4;

    private static DungeonTemplate map;
    private static RavengardDungeonConfig config;
    private static Set<Long> doorColumns;
    private static Set<Long> walkable;

    @BeforeAll
    static void walkTheOfficialMapFromTheSpawnPads() throws Exception {
        MinecraftServer.init();
        map = DungeonTemplate.get();
        config = new Gson().fromJson(Files.readString(Path.of("..", "configuration", "ravengard",
                "dungeon_1.json")), RavengardDungeonConfig.class);
        doorColumns = new HashSet<>();
        for (var object : config.objects()) {
            if (!object.category().equals("door")) continue;
            int doorX = (int) Math.floor(object.x());
            int doorZ = (int) Math.floor(object.z());
            for (int dx = -DOOR_RADIUS; dx <= DOOR_RADIUS; dx++) {
                for (int dz = -DOOR_RADIUS; dz <= DOOR_RADIUS; dz++) {
                    doorColumns.add(column(doorX + dx, doorZ + dz));
                }
            }
        }
        walkable = reachableFrom(RavengardDungeonGame.SPAWN_POINTS);
    }

    @Test
    void everySpawnPadIsOpenStandingRoom() {
        for (Pos pad : RavengardDungeonGame.SPAWN_POINTS) {
            assertTrue(standable(pad.blockX(), pad.blockY(), pad.blockZ()), pad.toString());
            assertTrue(!blocked(pad.blockX(), pad.blockY() + 2, pad.blockZ()), pad.toString());
        }
    }

    @Test
    void everyOfficialEncounterMarkerLandsWherePlayersCanWalk() {
        var markers = config.objects().stream()
                .filter(object -> object.category().equals("entity_spawn")).toList();
        var stranded = new ArrayList<String>();
        for (var marker : markers) {
            var resolved = DungeonInstanceRegistry.resolveSpawnMarker(marker, map::blockAt);
            if (resolved == null || !reached(resolved.x(), resolved.y(), resolved.z())) {
                stranded.add(marker.type() + "@" + marker.x() + "," + marker.y() + "," + marker.z()
                        + " -> " + (resolved == null ? "none" : resolved.x() + "," + resolved.y() + "," + resolved.z()));
            }
        }

        assertEquals(300, markers.size());
        assertEquals(List.of(), stranded);
    }

    @Test
    void everyExtractionPointCanBeWalkedTo() {
        var anchors = DungeonInstanceRegistry.officialExtractionAnchors();
        var stranded = anchors.stream()
                .filter(anchor -> !reached(anchor.x(), anchor.y(), anchor.z()))
                .map(Pos::toString).toList();

        assertEquals(10, anchors.size());
        assertEquals(List.of(), stranded);
    }

    @Test
    void roomTopUpsLandWherePlayersCanWalkAndLeaveFewRoomsEmpty() {
        var markers = config.objects().stream()
                .filter(object -> object.category().equals("entity_spawn")).toList();
        Set<String> available = Set.of("skeleton_knight", "zombie_knight");
        var official = DungeonInstanceRegistry.planEncounterMobs(
                DungeonInstanceRegistry.clusterMarkers(markers, 6.0), available, 1L);
        var units = RoomEncounters.units(
                net.swofty.type.ravengarddungeon.generator.RavengardDungeonGenerator.getCatalog());
        var topUps = RoomEncounters.plan(units, official, available, 1L, 10_000,
                RavengardDungeonGame.SPAWN_POINTS, map::blockAt,
                WalkableFloor.from(RavengardDungeonGame.SPAWN_POINTS, config.objects(), map::blockAt));

        var stranded = new ArrayList<String>();
        for (var plan : topUps) {
            var resolved = DungeonInstanceRegistry.resolveSpawnMarker(plan.marker(), map::blockAt);
            if (resolved == null || !reached(resolved.x(), resolved.y(), resolved.z())) {
                stranded.add(plan.marker().x() + "," + plan.marker().y() + "," + plan.marker().z());
            }
        }
        assertEquals(List.of(), stranded);

        var all = new ArrayList<>(official);
        all.addAll(topUps);
        int rooms = 0, emptyRooms = 0, mobs = 0;
        var empty = new ArrayList<String>();
        for (var unit : units) {
            if (unit.quota() == 0) continue;
            Set<Long> footprint = new HashSet<>();
            unit.cells().forEach(cell -> footprint.add(column((int) cell[0], (int) cell[1])));
            if (RavengardDungeonGame.SPAWN_POINTS.stream()
                    .anyMatch(pad -> footprint.contains(column(pad.blockX(), pad.blockZ())))) continue;
            long count = all.stream().filter(plan -> footprint.contains(column(
                    (int) Math.floor(plan.marker().x()), (int) Math.floor(plan.marker().z())))).count();
            rooms++;
            mobs += (int) count;
            if (count == 0) {
                emptyRooms++;
                empty.add(unit.id());
            }
        }
        System.out.println("room top-ups=" + topUps.size() + " rooms=" + rooms + " empty=" + emptyRooms
                + " mobsInRooms=" + mobs + " empty rooms " + empty);
        assertTrue(emptyRooms * 10 <= rooms, "too many rooms without a mob: " + empty);
        assertTrue(mobs * 2 >= rooms * 2, "fewer than two mobs per two rooms");
    }

    private static boolean reached(double x, double y, double z) {
        int blockX = (int) Math.floor(x), blockY = (int) Math.floor(y), blockZ = (int) Math.floor(z);
        for (int dy = -1; dy <= 1; dy++) {
            if (walkable.contains(cell(blockX, blockY + dy, blockZ))) return true;
        }
        return false;
    }

    private static Set<Long> reachableFrom(List<Pos> starts) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (Pos start : starts) {
            int[] origin = {start.blockX(), start.blockY(), start.blockZ()};
            if (seen.add(cell(origin[0], origin[1], origin[2]))) queue.add(origin);
        }
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] from = queue.poll();
            for (int[] step : steps) {
                int x = from[0] + step[0], z = from[2] + step[1];
                if (Math.abs(x) > MAP_RADIUS || Math.abs(z) > MAP_RADIUS) continue;
                for (int dy = 1; dy >= -MAXIMUM_DROP; dy--) {
                    int y = from[1] + dy;
                    if (!standable(x, y, z)) continue;
                    if (dy == 1 && blocked(from[0], from[1] + 2, from[2])) break;
                    if (dy < 0 && !clearColumn(x, y + 2, from[1] + 1, z)) break;
                    if (seen.add(cell(x, y, z))) queue.add(new int[]{x, y, z});
                    break;
                }
            }
        }
        return seen;
    }

    private static boolean standable(int x, int y, int z) {
        return blocked(x, y - 1, z) && !tallerThanAStep(x, y - 1, z) && !blocked(x, y, z) && !blocked(x, y + 1, z);
    }

    private static boolean clearColumn(int x, int fromY, int toY, int z) {
        for (int y = fromY; y <= toY; y++) {
            if (blocked(x, y, z)) return false;
        }
        return true;
    }

    private static boolean tallerThanAStep(int x, int y, int z) {
        return map.blockAt(x, y, z).registry().collisionShape().relativeEnd().y() > 1;
    }

    private static boolean blocked(int x, int y, int z) {
        Block block = map.blockAt(x, y, z);
        if (block.compare(Block.BARRIER) && doorColumns.contains(column(x, z))) return false;
        return block.registry().collisionShape().relativeEnd().y() > 0;
    }

    private static long column(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static long cell(int x, int y, int z) {
        return ((long) (x + 2048) << 40) | ((long) (y + 256) << 24) | (z + 2048);
    }
}
