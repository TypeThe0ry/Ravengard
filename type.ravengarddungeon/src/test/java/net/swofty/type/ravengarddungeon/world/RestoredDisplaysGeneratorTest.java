package net.swofty.type.ravengarddungeon.world;

import com.google.gson.Gson;
import net.hollowcube.polar.PolarReader;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.DoubleBinaryTag;
import net.kyori.adventure.nbt.FloatBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.NumberBinaryTag;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.swofty.type.generic.world.HypixelWorldLoader;
import net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig;
import net.swofty.type.ravengarddungeon.interactables.DungeonDoor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Rebuilds {@code dungeon_1.restored_displays.nbt}. Parts of the map were captured without their
 * display entities, leaving only the barrier blocks that gave the props their collision. Each such
 * barrier footprint gets the props of a captured footprint of the same size, preferably the same
 * cell layout, turned so its walls line up. Run with {@code RAVENGARD_REGENERATE_DISPLAYS=1}.
 */
@EnabledIfEnvironmentVariable(named = "RAVENGARD_REGENERATE_DISPLAYS", matches = "1")
class RestoredDisplaysGeneratorTest {
    private static final Path DIRECTORY = Path.of("..", "configuration", "ravengard");
    private static final double OWNER_REACH = 0.75;
    private static final double BLANK_CLEARANCE = 2.5;
    private static final List<String> NOT_PROPS = List.of("/doors/", "/portals/", "/altar/", "portal_pillar");

    private record Display(CompoundBinaryTag data, double x, double y, double z, float yaw, String model) {
    }

    private record Cluster(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, Set<Long> shape,
                           boolean[] walls) {
        double centerX() {
            return minX + sizeX / 2.0;
        }

        double centerZ() {
            return minZ + sizeZ / 2.0;
        }

        double distanceTo(double x, double y, double z) {
            double dx = Math.max(0, Math.max(minX - x, x - (minX + sizeX)));
            double dz = Math.max(0, Math.max(minZ - z, z - (minZ + sizeZ)));
            double dy = Math.max(0, Math.max(minY - 1.0 - y, y - (minY + sizeY + 1.0)));
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    private record Template(Cluster cluster, List<Display> displays) {
    }

    @Test
    void regenerate() throws Exception {
        MinecraftServer.init();
        Path polar = DIRECTORY.resolve("dungeon_1.polar");
        List<Display> displays = readDisplays(polar);
        var manager = MinecraftServer.getInstanceManager();
        Instance instance = HypixelWorldLoader.loadFrom(polar, manager.createInstanceContainer(), manager);
        for (int chunkX = -20; chunkX <= 20; chunkX++) {
            for (int chunkZ = -20; chunkZ <= 20; chunkZ++) instance.loadChunk(chunkX, chunkZ).join();
        }
        RavengardDungeonConfig config = new Gson().fromJson(
                Files.readString(DIRECTORY.resolve("dungeon_1.json")), RavengardDungeonConfig.class);
        DungeonDoor.spawnAll(instance, config.objects());
        List<Vec> markers = config.objects().stream()
                .filter(object -> !"entity_spawn".equals(object.category()) && !"trinket".equals(object.category()))
                .map(object -> new Vec(object.x(), object.y(), object.z()))
                .toList();

        List<Cluster> clusters = clusters(instance);
        List<Template> templates = new ArrayList<>();
        List<Cluster> blanks = new ArrayList<>();
        for (Cluster cluster : clusters) {
            List<Display> owned = displays.stream()
                    .filter(display -> cluster.distanceTo(display.x(), display.y(), display.z()) <= OWNER_REACH)
                    .toList();
            if (!owned.isEmpty()) {
                if (owned.stream().noneMatch(display -> NOT_PROPS.stream().anyMatch(display.model()::contains))) {
                    templates.add(new Template(cluster, owned));
                }
                continue;
            }
            boolean nearDisplay = displays.stream()
                    .anyMatch(display -> cluster.distanceTo(display.x(), display.y(), display.z()) <= BLANK_CLEARANCE);
            boolean nearMarker = markers.stream()
                    .anyMatch(marker -> cluster.distanceTo(marker.x(), marker.y(), marker.z()) <= BLANK_CLEARANCE);
            if (!nearDisplay && !nearMarker) blanks.add(cluster);
        }

        ListBinaryTag.Builder<CompoundBinaryTag> restored = ListBinaryTag.builder(BinaryTagTypes.COMPOUND);
        Map<String, Integer> placedModels = new TreeMap<>();
        Map<String, Integer> unmatched = new TreeMap<>();
        List<String> unmatchedAt = new ArrayList<>();
        int filled = 0;
        for (Cluster blank : blanks) {
            List<int[]> fits = new ArrayList<>();
            int bestScore = -1;
            for (int index = 0; index < templates.size(); index++) {
                Cluster source = templates.get(index).cluster();
                if (source.sizeY() != blank.sizeY()) continue;
                for (int turns = 0; turns < 4; turns++) {
                    int turnedX = turns % 2 == 0 ? source.sizeX() : source.sizeZ();
                    int turnedZ = turns % 2 == 0 ? source.sizeZ() : source.sizeX();
                    if (turnedX != blank.sizeX() || turnedZ != blank.sizeZ()) continue;
                    int score = rotatedShape(source, turns).equals(blank.shape()) ? 10 : 0;
                    for (int side = 0; side < 4; side++) {
                        if (source.walls()[side] == blank.walls()[(side + turns) % 4]) score++;
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        fits.clear();
                    }
                    if (score == bestScore) fits.add(new int[]{index, turns});
                }
            }
            String key = blank.sizeX() + "x" + blank.sizeY() + "x" + blank.sizeZ();
            if (fits.isEmpty()) {
                unmatched.merge(key, 1, Integer::sum);
                unmatchedAt.add("unmatched " + blank.minX() + " " + blank.minY() + " " + blank.minZ() + " " + key
                        + " walls=" + java.util.Arrays.toString(blank.walls()));
                continue;
            }
            int pick = Math.floorMod(31 * (31 * blank.minX() + blank.minY()) + blank.minZ(), fits.size());
            Template template = templates.get(fits.get(pick)[0]);
            int turns = fits.get(pick)[1];
            for (Display display : template.displays()) {
                double offsetX = display.x() - template.cluster().centerX();
                double offsetZ = display.z() - template.cluster().centerZ();
                for (int turn = 0; turn < turns; turn++) {
                    double previousX = offsetX;
                    offsetX = -offsetZ;
                    offsetZ = previousX;
                }
                double y = blank.minY() + (display.y() - template.cluster().minY());
                restored.add(display.data()
                        .put("Pos", ListBinaryTag.builder(BinaryTagTypes.DOUBLE)
                                .add(DoubleBinaryTag.doubleBinaryTag(blank.centerX() + offsetX))
                                .add(DoubleBinaryTag.doubleBinaryTag(y))
                                .add(DoubleBinaryTag.doubleBinaryTag(blank.centerZ() + offsetZ)).build())
                        .put("Rotation", ListBinaryTag.builder(BinaryTagTypes.FLOAT)
                                .add(FloatBinaryTag.floatBinaryTag(display.yaw() + 90f * turns))
                                .add(FloatBinaryTag.floatBinaryTag(0f)).build())
                        .remove("UUID"));
                placedModels.merge(display.model(), 1, Integer::sum);
            }
            filled++;
        }

        CompoundBinaryTag root = CompoundBinaryTag.builder()
                .putString("source", "dungeon_1.polar")
                .put("block_displays", restored.build())
                .build();
        BinaryTagIO.writer().write(root, DIRECTORY.resolve("dungeon_1.restored_displays.nbt"), BinaryTagIO.Compression.GZIP);

        List<String> report = new ArrayList<>();
        report.add("clusters=" + clusters.size() + " templates=" + templates.size() + " blank=" + blanks.size()
                + " filled=" + filled);
        report.add("unmatched " + unmatched);
        placedModels.forEach((model, count) -> report.add(count + " " + model));
        report.addAll(unmatchedAt);
        for (Cluster blank : blanks) {
            report.add("blank " + blank.minX() + " " + blank.minY() + " " + blank.minZ() + " "
                    + blank.sizeX() + "x" + blank.sizeY() + "x" + blank.sizeZ());
        }
        Files.write(Path.of("build", "restored_displays_report.txt"), report);
    }

    private static List<Display> readDisplays(Path polar) throws Exception {
        byte[] userData = PolarReader.read(Files.readAllBytes(polar)).userData();
        CompoundBinaryTag root = BinaryTagIO.unlimitedReader()
                .read(new ByteArrayInputStream(userData), BinaryTagIO.Compression.GZIP);
        Map<String, Display> unique = new LinkedHashMap<>();
        for (BinaryTag tag : root.getList("block_displays", BinaryTagTypes.COMPOUND)) {
            CompoundBinaryTag data = (CompoundBinaryTag) tag;
            ListBinaryTag position = data.getList("Pos");
            ListBinaryTag rotation = data.getList("Rotation");
            String model = data.getCompound("item").getCompound("components").getString("minecraft:item_model",
                    data.getString("id"));
            Display display = new Display(data, number(position, 0), number(position, 1), number(position, 2),
                    (float) number(rotation, 0), model);
            unique.putIfAbsent(model + "@" + display.x() + "," + display.y() + "," + display.z() + "," + display.yaw(), display);
        }
        return List.copyOf(unique.values());
    }

    private static double number(ListBinaryTag values, int index) {
        return values.size() > index && values.get(index) instanceof NumberBinaryTag number ? number.doubleValue() : 0;
    }

    private static List<Cluster> clusters(Instance instance) {
        Set<Long> barriers = new HashSet<>();
        for (int x = -300; x < 300; x++) {
            for (int z = -300; z < 300; z++) {
                for (int y = 40; y <= 100; y++) {
                    if (instance.getBlock(x, y, z).compare(Block.BARRIER)
                            && !DungeonDoor.isBarrierManaged(instance, new Vec(x, y, z))) {
                        barriers.add(pack(x, y, z));
                    }
                }
            }
        }
        List<Cluster> clusters = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (long start : barriers) {
            if (!seen.add(start)) continue;
            List<int[]> cells = new ArrayList<>();
            ArrayDeque<Long> queue = new ArrayDeque<>(List.of(start));
            while (!queue.isEmpty()) {
                int[] cell = unpack(queue.poll());
                cells.add(cell);
                for (int[] step : steps) {
                    long next = pack(cell[0] + step[0], cell[1] + step[1], cell[2] + step[2]);
                    if (barriers.contains(next) && seen.add(next)) queue.add(next);
                }
            }
            int minX = cells.stream().mapToInt(cell -> cell[0]).min().orElseThrow();
            int minY = cells.stream().mapToInt(cell -> cell[1]).min().orElseThrow();
            int minZ = cells.stream().mapToInt(cell -> cell[2]).min().orElseThrow();
            int sizeX = cells.stream().mapToInt(cell -> cell[0]).max().orElseThrow() - minX + 1;
            int sizeY = cells.stream().mapToInt(cell -> cell[1]).max().orElseThrow() - minY + 1;
            int sizeZ = cells.stream().mapToInt(cell -> cell[2]).max().orElseThrow() - minZ + 1;
            Set<Long> shape = new HashSet<>();
            for (int[] cell : cells) shape.add(pack(cell[0] - minX, cell[1] - minY, cell[2] - minZ));
            clusters.add(new Cluster(minX, minY, minZ, sizeX, sizeY, sizeZ, shape,
                    walls(instance, minX, minY, minZ, sizeX, sizeZ)));
        }
        clusters.sort(Comparator.comparingInt(Cluster::minX).thenComparingInt(Cluster::minZ).thenComparingInt(Cluster::minY));
        return clusters;
    }

    /** Whether the north, east, south and west neighbours along the footprint's base are mostly walls. */
    private static boolean[] walls(Instance instance, int minX, int y, int minZ, int sizeX, int sizeZ) {
        int[] solid = new int[4];
        for (int x = minX; x < minX + sizeX; x++) {
            if (wall(instance.getBlock(x, y, minZ - 1))) solid[0]++;
            if (wall(instance.getBlock(x, y, minZ + sizeZ))) solid[2]++;
        }
        for (int z = minZ; z < minZ + sizeZ; z++) {
            if (wall(instance.getBlock(minX + sizeX, y, z))) solid[1]++;
            if (wall(instance.getBlock(minX - 1, y, z))) solid[3]++;
        }
        return new boolean[]{solid[0] * 2 >= sizeX, solid[1] * 2 >= sizeZ, solid[2] * 2 >= sizeX, solid[3] * 2 >= sizeZ};
    }

    @SuppressWarnings("removal")
    private static boolean wall(Block block) {
        return !block.compare(Block.BARRIER) && block.isSolid();
    }

    /** The source footprint turned clockwise (as seen from above) {@code turns} quarter turns. */
    private static Set<Long> rotatedShape(Cluster cluster, int turns) {
        Set<Long> shape = cluster.shape();
        int width = cluster.sizeX(), depth = cluster.sizeZ();
        for (int turn = 0; turn < turns; turn++) {
            Set<Long> next = new HashSet<>();
            for (long packed : shape) {
                int[] cell = unpack(packed);
                next.add(pack(depth - 1 - cell[2], cell[1], cell[0]));
            }
            shape = next;
            int previous = width;
            width = depth;
            depth = previous;
        }
        return shape;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x + 1024) << 32) | ((long) (y + 1024) << 16) | (z + 1024);
    }

    private static int[] unpack(long packed) {
        return new int[]{(int) (packed >>> 32) - 1024, (int) ((packed >>> 16) & 0xFFFF) - 1024, (int) (packed & 0xFFFF) - 1024};
    }
}
