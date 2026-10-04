package net.swofty.type.ravengarddungeon.game;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The floor a player can walk to from the spawn pads: one-block steps up, drops of up to four
 * blocks, and doorways counted as open.
 */
final class WalkableFloor {
    private static final int MAP_RADIUS = 320;
    private static final int DOOR_RADIUS = 2;
    private static final int MAXIMUM_DROP = 4;

    private final DungeonInstanceRegistry.BlockLookup blocks;
    private final Set<Long> doorColumns = new HashSet<>();
    private final Set<Long> cells = new HashSet<>();

    private WalkableFloor(DungeonInstanceRegistry.BlockLookup blocks) {
        this.blocks = blocks;
    }

    static WalkableFloor from(List<Pos> starts, List<RavengardDungeonConfig.DungeonObject> doors,
                              DungeonInstanceRegistry.BlockLookup blocks) {
        WalkableFloor floor = new WalkableFloor(blocks);
        for (var door : doors) {
            if (!"door".equals(door.category())) continue;
            int doorX = (int) Math.floor(door.x());
            int doorZ = (int) Math.floor(door.z());
            for (int dx = -DOOR_RADIUS; dx <= DOOR_RADIUS; dx++) {
                for (int dz = -DOOR_RADIUS; dz <= DOOR_RADIUS; dz++) {
                    floor.doorColumns.add(column(doorX + dx, doorZ + dz));
                }
            }
        }
        floor.flood(starts);
        return floor;
    }

    boolean reaches(double x, double y, double z) {
        int blockX = (int) Math.floor(x), blockY = (int) Math.floor(y), blockZ = (int) Math.floor(z);
        for (int dy = -1; dy <= 1; dy++) {
            if (cells.contains(cell(blockX, blockY + dy, blockZ))) return true;
        }
        return false;
    }

    private void flood(List<Pos> starts) {
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (Pos start : starts) {
            int[] origin = {start.blockX(), start.blockY(), start.blockZ()};
            if (cells.add(cell(origin[0], origin[1], origin[2]))) queue.add(origin);
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
                    if (cells.add(cell(x, y, z))) queue.add(new int[]{x, y, z});
                    break;
                }
            }
        }
    }

    private boolean standable(int x, int y, int z) {
        return blocked(x, y - 1, z) && !tallerThanAStep(x, y - 1, z) && !blocked(x, y, z) && !blocked(x, y + 1, z);
    }

    private boolean clearColumn(int x, int fromY, int toY, int z) {
        for (int y = fromY; y <= toY; y++) {
            if (blocked(x, y, z)) return false;
        }
        return true;
    }

    private boolean tallerThanAStep(int x, int y, int z) {
        return blocks.blockAt(x, y, z).registry().collisionShape().relativeEnd().y() > 1;
    }

    private boolean blocked(int x, int y, int z) {
        Block block = blocks.blockAt(x, y, z);
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
