package net.swofty.type.ravengarddungeon.world;

import net.minestom.server.event.instance.InstanceChunkLoadEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.swofty.type.ravengarddungeon.generator.DungeonTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * The captured city has holes into the void: seven chunks inside the west wall were never
 * captured, and stretches of the outer edge have floor with no wall. Every empty column next to
 * anything solid gets an invisible wall, put in as its chunk loads, so nobody walks off the map.
 */
public final class VoidSeal {
    static final int WALL_MIN_Y = 56;
    static final int WALL_MAX_Y = 90;
    private static final int GROUND_MIN_Y = 40;
    private static final int GROUND_MAX_Y = 70;
    private static final int MAP_RADIUS = 336;

    private static volatile Map<Long, int[]> columnsByChunk;

    private VoidSeal() {
    }

    public static void apply(InstanceContainer container) {
        Map<Long, int[]> columns = columns();
        container.eventNode().addListener(InstanceChunkLoadEvent.class,
                event -> seal(event.getChunk(), columns));
        container.getChunks().forEach(chunk -> seal(chunk, columns));
    }

    /** Worked out once from the map file; every dungeon is built on the same city. */
    public static Map<Long, int[]> columns() {
        Map<Long, int[]> columns = columnsByChunk;
        if (columns != null) return columns;
        synchronized (VoidSeal.class) {
            if (columnsByChunk == null) columnsByChunk = compute(DungeonTemplate.get());
            return columnsByChunk;
        }
    }

    static boolean sealed(int x, int z) {
        int[] columns = columns().get(chunkKey(x >> 4, z >> 4));
        if (columns == null) return false;
        for (int index = 0; index < columns.length; index += 2) {
            if (columns[index] == x && columns[index + 1] == z) return true;
        }
        return false;
    }

    private static void seal(Chunk chunk, Map<Long, int[]> columns) {
        int[] cells = columns.get(chunkKey(chunk.getChunkX(), chunk.getChunkZ()));
        if (cells == null) return;
        boolean lock = !chunk.holdsWriteLock();
        if (lock) chunk.lockWriteLock();
        try {
            for (int index = 0; index < cells.length; index += 2) {
                for (int y = WALL_MIN_Y; y <= WALL_MAX_Y; y++) {
                    chunk.setBlock(cells[index], y, cells[index + 1], Block.BARRIER);
                }
            }
        } finally {
            if (lock) chunk.unlockWriteLock();
        }
    }

    private static Map<Long, int[]> compute(DungeonTemplate template) {
        int size = MAP_RADIUS * 2 + 1;
        boolean[][] hole = new boolean[size][size];
        for (int x = -MAP_RADIUS; x <= MAP_RADIUS; x++) {
            for (int z = -MAP_RADIUS; z <= MAP_RADIUS; z++) {
                hole[x + MAP_RADIUS][z + MAP_RADIUS] = !hasGround(template, x, z);
            }
        }
        Map<Long, java.util.List<Integer>> grouped = new HashMap<>();
        for (int x = -MAP_RADIUS + 1; x < MAP_RADIUS; x++) {
            for (int z = -MAP_RADIUS + 1; z < MAP_RADIUS; z++) {
                if (!hole[x + MAP_RADIUS][z + MAP_RADIUS] || !bordersGround(hole, x, z)) continue;
                java.util.List<Integer> cells = grouped.computeIfAbsent(chunkKey(x >> 4, z >> 4),
                        ignored -> new java.util.ArrayList<>());
                cells.add(x);
                cells.add(z);
            }
        }
        Map<Long, int[]> columns = new HashMap<>();
        grouped.forEach((key, cells) -> columns.put(key, cells.stream().mapToInt(Integer::intValue).toArray()));
        return Map.copyOf(columns);
    }

    private static boolean hasGround(DungeonTemplate template, int x, int z) {
        for (int y = GROUND_MIN_Y; y <= GROUND_MAX_Y; y++) {
            if (template.blockAt(x, y, z).registry().collisionShape().relativeEnd().y() > 0) return true;
        }
        return false;
    }

    private static boolean bordersGround(boolean[][] hole, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!hole[x + dx + MAP_RADIUS][z + dz + MAP_RADIUS]) return true;
            }
        }
        return false;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
