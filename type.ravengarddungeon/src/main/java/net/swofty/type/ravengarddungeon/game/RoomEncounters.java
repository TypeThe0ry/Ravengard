package net.swofty.type.ravengarddungeon.game;

import net.minestom.server.coordinate.Pos;
import net.swofty.dungeons.ravengard.RavengardRoomCatalog;
import net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Tops every room up to one or two mobs (one per long corridor), so any two rooms hold two or
 * three between them. The captured spawn markers only cover a few rooms; mobs already planned in
 * a room count towards it. The open citadel is split into room-sized squares.
 */
final class RoomEncounters {
    static final int CITADEL_SQUARE = 24;
    static final int LARGE_ROOM_CELLS = 500;
    static final int LONG_CORRIDOR_CELLS = 120;
    static final double SPAWN_PAD_CLEARANCE = 24.0;
    static final double MOB_SEPARATION = 6.0;
    static final int FLOOR_SEARCH_FROM = 58;
    static final int FLOOR_SEARCH_TO = 70;
    static final String MARKER_TYPE = "splinter";

    record Unit(String id, String color, List<long[]> cells) {
        int quota() {
            if ("brown".equalsIgnoreCase(color)) return cells.size() >= LONG_CORRIDOR_CELLS ? 1 : 0;
            return cells.size() >= LARGE_ROOM_CELLS ? 2 : 1;
        }
    }

    private RoomEncounters() {
    }

    static List<Unit> units(RavengardRoomCatalog catalog) {
        List<Unit> units = new ArrayList<>();
        for (RavengardRoomCatalog.DungeonRoom room : catalog.getRooms()) {
            units.add(new Unit(room.getId(), room.getColor(), cells(room)));
        }
        RavengardRoomCatalog.DungeonRoom citadel = catalog.getCitadel();
        if (citadel != null) {
            Map<Long, List<long[]>> squares = new LinkedHashMap<>();
            for (long[] cell : cells(citadel)) {
                long key = (Math.floorDiv(cell[0], CITADEL_SQUARE) << 32)
                        | (Math.floorDiv(cell[1], CITADEL_SQUARE) & 0xFFFFFFFFL);
                squares.computeIfAbsent(key, ignored -> new ArrayList<>()).add(cell);
            }
            squares.forEach((key, cells) -> units.add(new Unit(
                    citadel.getId() + "_" + (key >> 32) + "_" + (int) (long) key, citadel.getColor(), cells)));
        }
        return units;
    }

    private static List<long[]> cells(RavengardRoomCatalog.DungeonRoom room) {
        List<long[]> cells = new ArrayList<>();
        for (int[] run : room.getMask()) {
            for (int x = run[1]; x <= run[2]; x++) {
                cells.add(new long[]{room.getMinX() + x, room.getMinZ() + run[0]});
            }
        }
        return cells;
    }

    static List<DungeonInstanceRegistry.MobSpawnPlan> plan(
            List<Unit> units, List<DungeonInstanceRegistry.MobSpawnPlan> planned, Set<String> available,
            long seed, int firstEncounterId, List<Pos> spawnPads, DungeonInstanceRegistry.BlockLookup blocks,
            WalkableFloor walkable) {
        List<DungeonInstanceRegistry.MobSpawnPlan> added = new ArrayList<>();
        for (int index = 0; index < units.size(); index++) {
            Unit unit = units.get(index);
            Set<Long> footprint = new HashSet<>();
            unit.cells().forEach(cell -> footprint.add(key(cell[0], cell[1])));
            List<double[]> taken = new ArrayList<>();
            for (var plan : planned) {
                var marker = plan.marker();
                if (footprint.contains(key((long) Math.floor(marker.x()), (long) Math.floor(marker.z())))) {
                    taken.add(new double[]{marker.x(), marker.y(), marker.z()});
                }
            }
            int needed = unit.quota() - taken.size();
            if (needed <= 0) continue;
            List<double[]> spots = spots(unit, footprint, spawnPads, blocks, walkable);
            if (spots.isEmpty()) continue;
            Random random = new Random(seed ^ (0xC2B2AE3D27D4EB4FL * (index + 1L)));
            var types = DungeonInstanceRegistry.encounterMobTypes(List.of(new RavengardDungeonConfig.DungeonObject(
                    "entity_spawn", MARKER_TYPE, 0, 0, 0, 0f, 0f)), available, random, needed);
            for (String type : types) {
                double[] spot = farthestFrom(spots, taken);
                if (spot == null) break;
                taken.add(spot);
                added.add(new DungeonInstanceRegistry.MobSpawnPlan(type, new RavengardDungeonConfig.DungeonObject(
                        "entity_spawn", MARKER_TYPE, spot[0], spot[1], spot[2], random.nextInt(4) * 90f, 0f),
                        firstEncounterId + index));
            }
        }
        return added;
    }

    /**
     * Open floor inside the footprint, clear of walls, doors and props when the room allows it; in
     * cramped rooms any floor a mob can stand on will do.
     */
    private static List<double[]> spots(Unit unit, Set<Long> footprint, List<Pos> spawnPads,
                                        DungeonInstanceRegistry.BlockLookup blocks, WalkableFloor walkable) {
        boolean spawnRoom = spawnPads.stream().anyMatch(pad ->
                footprint.contains(key((long) Math.floor(pad.x()), (long) Math.floor(pad.z()))));
        if (spawnRoom) return List.of();
        double centreX = unit.cells().stream().mapToDouble(cell -> cell[0] + 0.5).average().orElse(0);
        double centreZ = unit.cells().stream().mapToDouble(cell -> cell[1] + 0.5).average().orElse(0);
        for (int margin = 2; margin >= 0; margin--) {
            List<double[]> spots = new ArrayList<>();
            for (long[] cell : unit.cells()) {
                int x = (int) cell[0];
                int z = (int) cell[1];
                if (!interior(footprint, x, z, margin)) continue;
                Integer floor = floor(blocks, x, z);
                if (floor == null || !openAround(blocks, x, floor, z, Math.min(margin, 1))) continue;
                double spotX = x + 0.5;
                double spotZ = z + 0.5;
                if (spawnPads.stream().anyMatch(pad ->
                        Math.hypot(pad.x() - spotX, pad.z() - spotZ) < SPAWN_PAD_CLEARANCE)) continue;
                if (!walkable.reaches(spotX, floor, spotZ)) continue;
                spots.add(new double[]{spotX, floor, spotZ});
            }
            if (!spots.isEmpty()) {
                spots.sort(Comparator.comparingDouble(spot -> Math.hypot(spot[0] - centreX, spot[2] - centreZ)));
                return spots;
            }
        }
        return List.of();
    }

    private static boolean interior(Set<Long> footprint, int x, int z, int margin) {
        for (int dx = -margin; dx <= margin; dx++) {
            for (int dz = -margin; dz <= margin; dz++) {
                if (!footprint.contains(key(x + dx, z + dz))) return false;
            }
        }
        return true;
    }

    private static Integer floor(DungeonInstanceRegistry.BlockLookup blocks, int x, int z) {
        for (int y = FLOOR_SEARCH_FROM; y <= FLOOR_SEARCH_TO; y++) {
            if (DungeonInstanceRegistry.standable(blocks, x, y, z)) return y;
        }
        return null;
    }

    private static boolean openAround(DungeonInstanceRegistry.BlockLookup blocks, int x, int y, int z, int reach) {
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (!DungeonInstanceRegistry.standable(blocks, x + dx, y, z + dz)) return false;
            }
        }
        return true;
    }

    /** The first spot (closest to the centre) when nothing is taken yet, else the one farthest from the taken ones. */
    private static double[] farthestFrom(List<double[]> spots, List<double[]> taken) {
        if (taken.isEmpty()) return spots.getFirst();
        double[] best = null;
        double bestDistance = MOB_SEPARATION;
        for (double[] spot : spots) {
            double nearest = taken.stream()
                    .mapToDouble(other -> Math.hypot(other[0] - spot[0], other[2] - spot[2]))
                    .min().orElse(Double.MAX_VALUE);
            if (nearest >= bestDistance) {
                best = spot;
                bestDistance = nearest;
            }
        }
        return best;
    }

    private static long key(long x, long z) {
        return (x << 32) | (z & 0xFFFFFFFFL);
    }
}
