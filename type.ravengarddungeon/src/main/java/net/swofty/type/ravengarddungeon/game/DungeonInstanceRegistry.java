package net.swofty.type.ravengarddungeon.game;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.ravengarddungeon.generator.RavengardDungeonGenerator;
import net.swofty.type.ravengarddungeon.generator.DungeonTemplate;
import net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig;
import net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer;
import net.swofty.type.ravengarddungeon.TypeRavengardDungeonLoader;
import net.swofty.dungeons.ravengard.RavengardDungeon.RoomPlacement;
import net.swofty.dungeons.ravengard.RavengardRoomCatalog.DungeonRoom;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class DungeonInstanceRegistry {
    public static final int MAX_INSTANCES = 4;
    private static final int STANDARD_ROOM_COUNT = 120;
    private static final int STANDARD_MAX_PLAYERS = 5;

    private static final Map<UUID, DungeonInstance> INSTANCES = new ConcurrentHashMap<>();

    private DungeonInstanceRegistry() {
    }

    public static final long EMPTY_LIFETIME_MILLIS = 30_000;

    public static final class DungeonInstance {
        private final UUID gameId = UUID.randomUUID();
        private final String mode;
        private final long seed;
        private final InstanceContainer instance;
        private final net.minestom.server.instance.SharedInstance shared;
        private final CompletableFuture<Void> ready;
        private final RavengardDungeonGenerator.GeneratedDungeon generated;
        private final RavengardDungeonGame game;
        private final Set<UUID> players = ConcurrentHashMap.newKeySet();
        private final Set<UUID> retirementRoutes = ConcurrentHashMap.newKeySet();
        private volatile long emptySince = System.currentTimeMillis();
        private volatile boolean retiring;

        public void markPlayerJoined(UUID player) {
            players.add(player);
            retirementRoutes.remove(player);
            emptySince = 0;
        }

        private void reconcilePlayerJoined(UUID player) {
            synchronized (DungeonInstanceRegistry.class) {
                long now = System.currentTimeMillis();
                for (DungeonInstance emptied : reconcilePlayerMemberships(
                        player, this, INSTANCES.values(), DungeonInstance::getPlayers)) {
                    emptied.emptySince = now;
                }
                emptySince = 0;
            }
        }

        public void markPlayerLeft(UUID player) {
            retirementRoutes.remove(player);
            if (players.remove(player) && players.isEmpty()) {
                emptySince = System.currentTimeMillis();
            }
        }

        public long getRemainingLifeSeconds() {
            if (!players.isEmpty() || emptySince == 0) return -1;
            return Math.max(0, (EMPTY_LIFETIME_MILLIS - (System.currentTimeMillis() - emptySince)) / 1000);
        }

        public boolean isExpired() {
            return ready.isDone() && players.isEmpty() && emptySince > 0
                    && System.currentTimeMillis() - emptySince > EMPTY_LIFETIME_MILLIS;
        }

        private boolean beginRetirementIfExpired() {
            synchronized (DungeonInstanceRegistry.class) {
                if (!retiring && !isExpired()) return false;
                retiring = true;
                return true;
            }
        }

        private void retainLivePlayer(UUID player) {
            players.add(player);
            emptySince = 0;
        }

        private DungeonInstance(String mode, long seed, InstanceContainer instance,
                                net.minestom.server.instance.SharedInstance shared,
                                RavengardDungeonGenerator.GeneratedDungeon generated,
                                CompletableFuture<Void> ready) {
            this.mode = mode;
            this.seed = seed;
            this.instance = instance;
            this.shared = shared;
            this.generated = generated;
            List<RavengardDungeonConfig.DungeonObject> generatedObjects = generatedObjects(generated);
            this.ready = ready;
            List<net.minestom.server.coordinate.Pos> extractions = extractionAnchors(generated);
            List<RavengardDungeonGenerator.PlacedObject> officialObjects =
                    TypeRavengardDungeonLoader.getGame().getConfig().objects().stream()
                            .map(object -> new RavengardDungeonGenerator.PlacedObject(
                                    object.category(), object.type(), object.x(), object.y(), object.z(), object.yaw()))
                            .toList();
            this.game = new RavengardDungeonGame(shared, TypeRavengardDungeonLoader.getGame().getConfig(),
                    RavengardDungeonGenerator.spawnPoints(generated, officialObjects), extractions);
            double[] bounds = bounds(generated);
            this.game.configureDeathWall(bounds[0], bounds[1], bounds[2]);
        }

        public UUID getGameId() {
            return gameId;
        }

        public String getMode() {
            return mode;
        }

        public long getSeed() {
            return seed;
        }

        /** The instance players actually join, sharing the container's chunks. */
        public net.minestom.server.instance.Instance getInstance() {
            return shared;
        }

        public InstanceContainer getContainer() {
            return instance;
        }

        public CompletableFuture<Void> whenReady() {
            return ready;
        }

        public RavengardDungeonGenerator.GeneratedDungeon getGenerated() {
            return generated;
        }

        public RavengardDungeonGame getGame() {
            return game;
        }

        public Set<UUID> getPlayers() {
            return players;
        }

        public boolean isAcceptingJoins() {
            return !retiring && mode.equals("STANDARD") && game.getState().isWaiting()
                    && game.getAvailableSlots() > 0
                    && players.size() < STANDARD_MAX_PLAYERS;
        }

        public record MapTile(double worldX, double worldZ, int roomIndex) {
        }

        private volatile List<MapTile> mapTiles;
        private final Set<Integer> visitedRooms = ConcurrentHashMap.newKeySet();

        public Set<Integer> getVisitedRooms() {
            return visitedRooms;
        }

        /** Fixed world-grid mosaic of the layout, one tile per grid cell a room covers. */
        public List<MapTile> getMapTiles(double blocksPerTile) {
            if (mapTiles == null) {
                synchronized (this) {
                    if (mapTiles == null) {
                        List<MapTile> tiles = new ArrayList<>();
                        var placements = generated.dungeon().getPlacements();
                        java.util.Map<Long, Integer> grid = new java.util.HashMap<>();
                        for (int index = 0; index < placements.size(); index++) {
                            var placement = placements.get(index);
                            final int roomIndex = index;
                            placement.forEachMaskCell((localX, localZ) -> {
                                long gridX = Math.round((placement.originX() + localX) / blocksPerTile);
                                long gridZ = Math.round((placement.originZ() + localZ) / blocksPerTile);
                                grid.putIfAbsent((gridX << 32) | (gridZ & 0xFFFFFFFFL), roomIndex);
                            });
                        }
                        for (var entry : grid.entrySet()) {
                            long key = entry.getKey();
                            tiles.add(new MapTile((key >> 32) * blocksPerTile,
                                    ((int) key) * blocksPerTile, entry.getValue()));
                        }
                        mapTiles = tiles;
                    }
                }
            }
            return mapTiles;
        }

        /** Index of the placement whose footprint contains the position, or -1. */
        public int roomIndexAt(double worldX, double worldZ) {
            var placements = generated.dungeon().getPlacements();
            for (int index = 0; index < placements.size(); index++) {
                var placement = placements.get(index);
                int localX = (int) Math.floor(worldX) - placement.originX();
                int localZ = (int) Math.floor(worldZ) - placement.originZ();
                if (localX < 0 || localZ < 0 || localX >= placement.getFootprintWidth()
                        || localZ >= placement.getFootprintDepth()) {
                    continue;
                }
                int[] source = placement.toSourceFrame(localX, localZ);
                for (int[] run : placement.room().getMask()) {
                    if (run[0] == source[1] && source[0] >= run[1] && source[0] <= run[2]) {
                        return index;
                    }
                }
            }
            return -1;
        }
    }

    public static DungeonInstance create(String mode) {
        long seed;
        int roomCount = STANDARD_ROOM_COUNT;
        if (mode.startsWith("ADMIN")) {
            String[] parts = mode.split(":");
            seed = parts.length > 1 ? Long.parseLong(parts[1]) : ThreadLocalRandom.current().nextLong();
            if (parts.length > 2) roomCount = Integer.parseInt(parts[2]);
        } else {
            seed = ThreadLocalRandom.current().nextLong();
        }

        RavengardDungeonGenerator.GeneratedDungeon generated =
                RavengardDungeonGenerator.generate(seed, roomCount);
        // the skyblock island recipe: fullbright dimension, container for the
        // blocks, shared instance for the players. The blueprint city IS the
        // dungeon; only swapped slot interiors get restamped on top of it.
        var dimensionKey = MinecraftServer.getDimensionTypeRegistry()
                .getKey(net.kyori.adventure.key.Key.key("ravengard:dungeon"));
        InstanceContainer instance = MinecraftServer.getInstanceManager()
                .createInstanceContainer(dimensionKey);
        net.minestom.server.instance.SharedInstance shared;
        try {
            shared = net.swofty.type.generic.world.HypixelWorldLoader.loadFrom(
                    java.nio.file.Path.of("./configuration/ravengard/dungeon_1.polar"),
                    instance, MinecraftServer.getInstanceManager());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not load dungeon city base", exception);
        }
        CompletableFuture<Void> ready = RavengardDungeonGenerator.stampSwaps(generated, instance);

        DungeonInstance dungeonInstance = new DungeonInstance(
                mode.startsWith("ADMIN") ? "ADMIN" : "STANDARD", seed, instance, shared, generated, ready);
        ready.thenRun(() -> spawnInteractables(dungeonInstance));
        INSTANCES.put(dungeonInstance.getGameId(), dungeonInstance);
        return dungeonInstance;
    }

    private static void spawnInteractables(DungeonInstance dungeonInstance) {
        try {
            var container = dungeonInstance.getContainer();
            var generatedObjects = new ArrayList<>(generatedObjects(dungeonInstance.getGenerated()));
            var doorMerge = mergeDoorMarkers(
                    generatedObjects, TypeRavengardDungeonLoader.getGame().getConfig().objects());
            generatedObjects = new ArrayList<>(doorMerge.objects());
            org.tinylog.Logger.info(
                    "Dungeon door markers: generated={} official={} duplicates={} merged={}",
                    doorMerge.generatedDoorMarkers(), doorMerge.officialDoorMarkers(),
                    doorMerge.duplicateDoorMarkers(), doorMerge.mergedDoorMarkers());

            var encounterMerge = mergeEncounterMarkers(
                    generatedObjects, TypeRavengardDungeonLoader.getGame().getConfig().objects());
            generatedObjects = new ArrayList<>(encounterMerge.objects());
            org.tinylog.Logger.info(
                    "Dungeon encounter markers: generated={} official={} duplicates={} merged={}",
                    encounterMerge.generatedMarkers(), encounterMerge.officialMarkers(),
                    encounterMerge.duplicateMarkers(), encounterMerge.mergedMarkers());

            var spawn = dungeonInstance.getGenerated().spawn();
            int mobs = registerEncounters(dungeonInstance.getInstance(), generatedObjects,
                    dungeonInstance.getSeed(), dungeonInstance.getGame().getSpawnPoints());

            java.util.Set<Long> chunks = new java.util.HashSet<>();
            for (var object : generatedObjects) {
                if (object.type().equals("door_1") || object.type().equals("door_1_top")
                        || object.type().equals("iron_gate")) {
                    chunks.add((((long) ((int) object.x() >> 4)) << 32)
                            | (((int) object.z() >> 4) & 0xFFFFFFFFL));
                }
            }
            java.util.List<CompletableFuture<?>> loads = new java.util.ArrayList<>();
            for (long key : chunks) {
                loads.add(container.loadChunk((int) (key >> 32), (int) key));
            }
            CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).join();
            var doorSpawns = net.swofty.type.ravengarddungeon.interactables.DungeonDoor.spawnAll(
                    dungeonInstance.getInstance(), generatedObjects);
            org.tinylog.Logger.info(
                    "Dungeon doors spawned: inputMarkers={} uniqueLeaves={} uniqueTops={} "
                            + "completePairs={} partialTops={} orphanLeaves={} displays={} "
                            + "interactions={} barrierRefs={} uniqueBarrierCells={}",
                    doorSpawns.inputMarkers(), doorSpawns.uniqueLeafMarkers(),
                    doorSpawns.uniqueTopMarkers(), doorSpawns.completeDoorways(),
                    doorSpawns.partialDoorways(), doorSpawns.orphanLeaves(), doorSpawns.displays(),
                    doorSpawns.interactions(), doorSpawns.barrierReferences(),
                    doorSpawns.uniqueBarrierCells());

            generatedObjects.stream()
                    .filter(object -> "door".equals(object.category()))
                    .sorted(java.util.Comparator.comparingDouble(object ->
                            Math.hypot(object.x() - spawn.x(), object.z() - spawn.z())))
                    .limit(12)
                    .forEach(object -> org.tinylog.Logger.info(
                            "Dungeon door marker: type={} pos=({}, {}, {}) distance={}",
                            object.type(), object.x(), object.y(), object.z(),
                            Math.hypot(object.x() - spawn.x(), object.z() - spawn.z())));

            int chests = net.swofty.type.ravengarddungeon.interactables.DungeonChest
                    .convertDecorChests(dungeonInstance.getInstance(), dungeonInstance.getSeed());
            List<net.minestom.server.coordinate.Pos> extractions = dungeonInstance.getGame().getExtractionPoints();
            for (var extraction : extractions) {
                net.swofty.type.ravengarddungeon.interactables.DungeonExtraction.spawn(
                        dungeonInstance.getInstance(), dungeonInstance.getGame(), extraction);
            }
            org.tinylog.Logger.info("Registered interactables: {} proximity mob encounters, {} door chunks loaded, {} chests converted",
                    mobs, chunks.size(), chests);
            org.tinylog.Logger.info("Registered {} dynamic cyan-room extraction points", extractions.size());
        } catch (Exception exception) {
            org.tinylog.Logger.error(exception, "Failed to spawn dungeon interactables");
        }
    }

    /** Spawns the captured entity markers into the static dungeon instance used by this server. */
    public static void spawnInteractables(net.minestom.server.instance.Instance instance,
                                           RavengardDungeonConfig config, long seed) {
        int mobs = registerEncounters(instance, config.objects(), seed, RavengardDungeonGame.SPAWN_POINTS);
        var doorSpawns = net.swofty.type.ravengarddungeon.interactables.DungeonDoor.spawnAll(instance, config.objects());
        org.tinylog.Logger.info(
                "Static dungeon doors spawned: inputMarkers={} completePairs={} partialTops={} "
                        + "orphanLeaves={} displays={} interactions={} uniqueBarrierCells={}",
                doorSpawns.inputMarkers(), doorSpawns.completeDoorways(), doorSpawns.partialDoorways(),
                doorSpawns.orphanLeaves(), doorSpawns.displays(), doorSpawns.interactions(),
                doorSpawns.uniqueBarrierCells());
        int chests = net.swofty.type.ravengarddungeon.interactables.DungeonChest.convertDecorChests(instance, seed);
        RavengardDungeonGame staticGame = TypeRavengardDungeonLoader.getGame();
        List<net.minestom.server.coordinate.Pos> extractions = staticGame == null
                ? List.of() : staticGame.getExtractionPoints();
        for (var extraction : extractions) {
            net.swofty.type.ravengarddungeon.interactables.DungeonExtraction.spawn(
                    instance, staticGame, extraction);
        }
        org.tinylog.Logger.info("Registered static dungeon interactables: {} proximity mob encounters, {} chests, {} extraction points",
                mobs, chests, extractions.size());
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            EncounterRuntime runtime = ENCOUNTERS.get(instance);
            if (runtime != null && staticGame != null
                    && staticGame.getState() == net.swofty.type.game.game.GameState.IN_PROGRESS
                    && !instance.getPlayers().isEmpty()) {
                runtime.activate(instance, instance.getPlayers().stream().collect(
                        java.util.stream.Collectors.toMap(
                                net.minestom.server.entity.Player::getUuid,
                                net.minestom.server.entity.Player::getPosition)));
            }
        }).repeat(net.minestom.server.timer.TaskSchedule.seconds(1)).schedule();
    }

    static DoorMarkerMerge mergeDoorMarkers(
            List<RavengardDungeonConfig.DungeonObject> generated,
            List<RavengardDungeonConfig.DungeonObject> official) {
        var objects = new ArrayList<RavengardDungeonConfig.DungeonObject>(
                generated.size() + official.size());
        var doorKeys = new java.util.LinkedHashSet<DoorMarkerKey>();
        int generatedDoorMarkers = 0;
        int officialDoorMarkers = 0;
        int duplicateDoorMarkers = 0;

        for (var object : generated) {
            if (!isDoorMarker(object)) {
                objects.add(object);
                continue;
            }
            generatedDoorMarkers++;
            if (doorKeys.add(doorMarkerKey(object))) {
                objects.add(object);
            } else {
                duplicateDoorMarkers++;
            }
        }
        for (var object : official) {
            if (!isDoorMarker(object)) continue;
            officialDoorMarkers++;
            if (doorKeys.add(doorMarkerKey(object))) {
                objects.add(object);
            } else {
                duplicateDoorMarkers++;
            }
        }

        return new DoorMarkerMerge(objects, generatedDoorMarkers, officialDoorMarkers,
                duplicateDoorMarkers, doorKeys.size());
    }

    private static boolean isDoorMarker(RavengardDungeonConfig.DungeonObject object) {
        return "door_1".equals(object.type())
                || "door_1_top".equals(object.type())
                || "iron_gate".equals(object.type());
    }

    private static DoorMarkerKey doorMarkerKey(RavengardDungeonConfig.DungeonObject object) {
        long yaw = Math.round(normalizedYaw(object.yaw()) * 1000.0) % 360_000L;
        return new DoorMarkerKey(object.type(),
                Math.round(object.x() * 1000.0),
                Math.round(object.y() * 1000.0),
                Math.round(object.z() * 1000.0), yaw);
    }

    private static double normalizedYaw(float yaw) {
        return ((yaw % 360.0) + 360.0) % 360.0;
    }

    record DoorMarkerMerge(
            List<RavengardDungeonConfig.DungeonObject> objects,
            int generatedDoorMarkers,
            int officialDoorMarkers,
            int duplicateDoorMarkers,
            int mergedDoorMarkers) {
        DoorMarkerMerge {
            objects = List.copyOf(objects);
        }
    }

    private record DoorMarkerKey(String type, long x, long y, long z, long yaw) {
    }

    static MarkerMerge mergeEncounterMarkers(
            List<RavengardDungeonConfig.DungeonObject> generated,
            List<RavengardDungeonConfig.DungeonObject> official) {
        var objects = new ArrayList<RavengardDungeonConfig.DungeonObject>(
                generated.size() + official.size());
        var keys = new java.util.LinkedHashSet<EncounterMarkerKey>();
        int generatedMarkers = 0;
        int officialMarkers = 0;
        int duplicateMarkers = 0;
        for (var object : generated) {
            if (!"entity_spawn".equals(object.category())) {
                objects.add(object);
                continue;
            }
            generatedMarkers++;
            if (keys.add(encounterMarkerKey(object))) objects.add(object);
            else duplicateMarkers++;
        }
        for (var object : official) {
            if (!"entity_spawn".equals(object.category())) continue;
            officialMarkers++;
            if (keys.add(encounterMarkerKey(object))) objects.add(object);
            else duplicateMarkers++;
        }
        return new MarkerMerge(objects, generatedMarkers, officialMarkers,
                duplicateMarkers, keys.size());
    }

    private static EncounterMarkerKey encounterMarkerKey(RavengardDungeonConfig.DungeonObject object) {
        return new EncounterMarkerKey(object.type(),
                Math.round(object.x() * 1000.0),
                Math.round(object.y() * 1000.0),
                Math.round(object.z() * 1000.0));
    }

    record MarkerMerge(List<RavengardDungeonConfig.DungeonObject> objects,
                       int generatedMarkers, int officialMarkers,
                       int duplicateMarkers, int mergedMarkers) {
        MarkerMerge {
            objects = List.copyOf(objects);
        }
    }

    private record EncounterMarkerKey(String type, long x, long y, long z) {
    }

    private static final int MARKERS_PER_MOB = 3;
    private static final int MAX_MOBS_PER_CLUSTER = 5;
    private static final double ENCOUNTER_MOB_SEPARATION_SQUARED = 2.25 * 2.25;
    private static final double ENCOUNTER_ACTIVATION_RADIUS = 72.0;
    private static final double ENCOUNTER_ACTIVATION_RADIUS_SQUARED =
            ENCOUNTER_ACTIVATION_RADIUS * ENCOUNTER_ACTIVATION_RADIUS;
    private static final int OPENING_ENCOUNTERS_PER_REGION = 4;
    private static final int OPENING_TYPES_PER_SPAWN = 4;
    private static final int OPENING_MOBS_PER_SPAWN = 8;
    private static final double OPENING_SPAWN_RADIUS_SQUARED = 112.0 * 112.0;
    private static final double ENCOUNTER_ACTIVATION_MOVEMENT = 10.0;
    private static final int ENCOUNTERS_PER_ACTIVATION = 3;
    private static final int IMMEDIATE_ACTIVATION_MOBS = 32;
    private static final int ENCOUNTER_GROUND_SEARCH_RADIUS = 4;
    private static final int ENCOUNTER_GROUND_SEARCH_UP = 2;
    private static final int ENCOUNTER_GROUND_SEARCH_DOWN = 12;
    private static final Map<net.minestom.server.instance.Instance, EncounterRuntime> ENCOUNTERS =
            new ConcurrentHashMap<>();
    private static final Set<String> COMBAT_MOB_TYPES = Set.of(
            "skeleton_archer", "skeleton_knight", "skeleton_warrior",
            "zombie_knight", "demon_grunt", "demon_imp", "oculus",
            "skeleton_archer_elite", "skeleton_knight_elite", "skeleton_warrior_elite",
            "zombie_knight_elite",
            "demon_grunt_elite", "demon_imp_elite", "oculus_elite",
            "demon_archer", "demon_archer_elite",
            "silver_mimic", "bottle_mimic_short_yellow", "bottle_mimic_short_green",
            "bottle_mimic_short_red", "bottle_mimic_tall_aqua", "bottle_mimic_tall_green",
            "bottle_mimic_tall_pink", "normal_mimic", "mini_mimic", "dire_wolf",
            "cursed_knight", "guardian", "iron_core", "wraith", "warden", "wraith_boss");
    private static final List<String> RARE_MOB_POOL = List.of(
            "silver_mimic", "bottle_mimic_short_yellow", "bottle_mimic_short_green",
            "bottle_mimic_short_red", "bottle_mimic_tall_aqua", "bottle_mimic_tall_green",
            "bottle_mimic_tall_pink", "normal_mimic", "mini_mimic", "wraith_boss");
    private static final int RARE_MOB_ROLL = 18;
    private static final List<String> SPLINTER_MOB_POOL = List.of(
            "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight", "demon_grunt",
            "skeleton_archer_elite", "skeleton_knight_elite", "skeleton_warrior_elite",
            "zombie_knight_elite", "demon_grunt_elite",
            "demon_archer", "demon_archer_elite", "dire_wolf", "cursed_knight",
            "guardian", "iron_core", "wraith", "warden");
    private static final List<String> EYE_MOB_POOL = List.of(
            "oculus", "oculus_elite", "skeleton_archer", "skeleton_archer_elite");
    private static final List<String> FIRE_MOB_POOL = List.of(
            "demon_imp", "demon_imp_elite", "demon_grunt", "demon_grunt_elite",
            "zombie_knight", "zombie_knight_elite");
    private static final List<String> TORNADO_MOB_POOL = List.of(
            "zombie_knight", "zombie_knight_elite", "skeleton_knight", "skeleton_knight_elite",
            "demon_grunt", "demon_grunt_elite");

    private static int registerEncounters(net.minestom.server.instance.Instance instance,
                                          List<RavengardDungeonConfig.DungeonObject> objects,
                                          long seed,
                                          List<net.minestom.server.coordinate.Pos> initialSpawnPoints) {
        var direct = new ArrayList<RavengardDungeonConfig.DungeonObject>();
        var markers = new ArrayList<RavengardDungeonConfig.DungeonObject>();
        var availableClips = new java.util.HashSet<>(
                net.swofty.type.ravengardgeneric.entity.animation.RavengardMobClip.available());
        var available = runtimeCombatAppearances(availableClips);
        for (var object : objects) {
            if (!"entity_spawn".equals(object.category())) continue;
            if (isChestMimicType(object.type())) continue;
            if (availableClips.contains(object.type()) && COMBAT_MOB_TYPES.contains(object.type())) {
                direct.add(object);
            } else {
                markers.add(object);
            }
        }

        var plans = new ArrayList<MobSpawnPlan>();
        int nextEncounterId = 0;
        for (var object : direct) plans.add(new MobSpawnPlan(object.type(), object, nextEncounterId++));
        plans.addAll(planEncounterMobs(clusterMarkers(markers, 6.0), available, seed, nextEncounterId));
        SpawnResolution resolution = resolveSpawnPlans(instance, plans);
        List<Integer> opening = openingEncounterIndexes(
                resolution.plans(), initialSpawnPoints, OPENING_ENCOUNTERS_PER_REGION);
        EncounterRuntime runtime = new EncounterRuntime(resolution.plans(), initialSpawnPoints, opening);
        EncounterRuntime previous = ENCOUNTERS.put(instance, runtime);
        if (previous != null) previous.clear();
        runtime.prime(instance);
        var distribution = encounterDistribution(resolution.plans());
        var activation = encounterActivationDiagnostics(resolution.plans(), initialSpawnPoints, opening);
        org.tinylog.Logger.info(
                "Registered {} captured encounter markers as grounded proximity spawns; "
                        + "planned={} opening={} openingTypes={} initialProximityEligible={} dormant={} "
                        + "relocated={} unresolved={} spawnCoverage={} types={} distribution={}",
                markers.size() + direct.size(), activation.planned(), activation.opening(),
                activation.openingTypes(), activation.initialNearby(), activation.dormant(),
                resolution.relocated(), resolution.unresolved(),
                encounterSpawnCoverage(resolution.plans(), opening, initialSpawnPoints),
                encounterTypeDistribution(resolution.plans()), distribution);
        return resolution.plans().size();
    }

    private static void loadEncounterChunks(net.minestom.server.instance.Instance instance,
                                            List<MobSpawnPlan> plans) {
        var chunks = new java.util.LinkedHashSet<Long>();
        for (MobSpawnPlan plan : plans) {
            int blockX = (int) Math.floor(plan.marker().x());
            int blockZ = (int) Math.floor(plan.marker().z());
            int minimumChunkX = (blockX - ENCOUNTER_GROUND_SEARCH_RADIUS) >> 4;
            int maximumChunkX = (blockX + ENCOUNTER_GROUND_SEARCH_RADIUS) >> 4;
            int minimumChunkZ = (blockZ - ENCOUNTER_GROUND_SEARCH_RADIUS) >> 4;
            int maximumChunkZ = (blockZ + ENCOUNTER_GROUND_SEARCH_RADIUS) >> 4;
            for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
                for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                    chunks.add((((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL));
                }
            }
        }
        var loads = chunks.stream()
                .map(key -> instance.loadChunk((int) (key >> 32), (int) (long) key))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(loads).join();
    }

    static SpawnResolution resolveSpawnPlans(net.minestom.server.instance.Instance instance,
                                             List<MobSpawnPlan> plans) {
        loadEncounterChunks(instance, plans);
        return resolveSpawnPlans(plans, (x, y, z) -> instance.getBlock(x, y, z,
                net.minestom.server.instance.block.Block.Getter.Condition.TYPE));
    }

    static Map<String, Integer> encounterTypeDistribution(List<MobSpawnPlan> plans) {
        var distribution = new java.util.TreeMap<String, Integer>();
        plans.forEach(plan -> distribution.merge(plan.mobType(), 1, Integer::sum));
        return Map.copyOf(distribution);
    }

    static List<MobSpawnPlan> plansWithinActivationRadius(List<MobSpawnPlan> plans,
                                                           List<net.minestom.server.coordinate.Pos> players,
                                                           Set<Integer> activated) {
        return planIndexesWithinActivationRadius(plans, players, activated).stream()
                .map(plans::get)
                .toList();
    }

    static List<Integer> planIndexesWithinActivationRadius(List<MobSpawnPlan> plans,
                                                            List<net.minestom.server.coordinate.Pos> players,
                                                            Set<Integer> activated) {
        if (players.isEmpty()) return List.of();
        var selected = new ArrayList<Integer>();
        for (int index = 0; index < plans.size(); index++) {
            if (activated.contains(index)) continue;
            var marker = plans.get(index).marker();
            boolean nearby = players.stream().anyMatch(player -> {
                double dx = marker.x() - player.x();
                double dz = marker.z() - player.z();
                return dx * dx + dz * dz <= ENCOUNTER_ACTIVATION_RADIUS_SQUARED;
            });
            if (nearby) selected.add(index);
        }
        return List.copyOf(selected);
    }

    static EncounterActivationDiagnostics encounterActivationDiagnostics(
            List<MobSpawnPlan> plans, List<net.minestom.server.coordinate.Pos> initialSpawnPoints) {
        return encounterActivationDiagnostics(plans, initialSpawnPoints,
                openingEncounterIndexes(plans, initialSpawnPoints, OPENING_ENCOUNTERS_PER_REGION));
    }

    static EncounterActivationDiagnostics encounterActivationDiagnostics(
            List<MobSpawnPlan> plans, List<net.minestom.server.coordinate.Pos> initialSpawnPoints,
            List<Integer> opening) {
        int initialNearby = planIndexesWithinActivationRadius(plans, initialSpawnPoints, Set.of()).size();
        int openingTypes = (int) opening.stream().map(plans::get).map(MobSpawnPlan::mobType).distinct().count();
        return new EncounterActivationDiagnostics(
                plans.size(), initialNearby, opening.size(), openingTypes, plans.size() - opening.size());
    }

    static List<EncounterSpawnCoverage> encounterSpawnCoverage(
            List<MobSpawnPlan> plans,
            List<Integer> opening,
            List<net.minestom.server.coordinate.Pos> spawnPoints) {
        var coverage = new ArrayList<EncounterSpawnCoverage>();
        for (int spawnIndex = 0; spawnIndex < spawnPoints.size(); spawnIndex++) {
            var spawnPoint = spawnPoints.get(spawnIndex);
            var nearby = opening.stream()
                    .map(plans::get)
                    .filter(plan -> horizontalDistanceSquared(
                            plan.marker(), spawnPoint) <= OPENING_SPAWN_RADIUS_SQUARED)
                    .toList();
            coverage.add(new EncounterSpawnCoverage(
                    spawnIndex, nearby.size(), (int) nearby.stream()
                    .map(MobSpawnPlan::mobType).distinct().count()));
        }
        return List.copyOf(coverage);
    }

    private static void activateNearbyEncounters() {
        for (DungeonInstance dungeon : INSTANCES.values()) {
            if (dungeon.getGame().getState() != net.swofty.type.game.game.GameState.IN_PROGRESS) continue;
            var instance = dungeon.getInstance();
            EncounterRuntime runtime = ENCOUNTERS.get(instance);
            if (runtime == null || instance.getPlayers().isEmpty()) continue;
            runtime.activate(instance, instance.getPlayers().stream().collect(
                    java.util.stream.Collectors.toMap(
                            net.minestom.server.entity.Player::getUuid,
                            net.minestom.server.entity.Player::getPosition)));
        }
    }

    private static void removeEncounters(net.minestom.server.instance.Instance instance) {
        EncounterRuntime runtime = ENCOUNTERS.remove(instance);
        if (runtime != null) runtime.clear();
    }

    private static final class EncounterRuntime {
        private final List<MobSpawnPlan> plans;
        private final List<net.minestom.server.coordinate.Pos> spawnPoints;
        private final List<Integer> opening;
        private final Set<Integer> activated = ConcurrentHashMap.newKeySet();
        private final Set<Integer> retryable = new java.util.LinkedHashSet<>();
        private final Map<UUID, net.minestom.server.coordinate.Pos> lastObservedPositions = new java.util.HashMap<>();
        private final Map<UUID, Double> activationMovement = new java.util.HashMap<>();
        private int spawned;

        private EncounterRuntime(List<MobSpawnPlan> plans,
                                 List<net.minestom.server.coordinate.Pos> spawnPoints,
                                 List<Integer> opening) {
            this.plans = List.copyOf(plans);
            this.spawnPoints = List.copyOf(spawnPoints);
            this.opening = List.copyOf(opening);
        }

        private synchronized void prime(net.minestom.server.instance.Instance instance) {
            var primedPlans = new ArrayList<MobSpawnPlan>();
            for (int index : opening) {
                MobSpawnPlan plan = plans.get(index);
                if (activatePlan(activated, index,
                        () -> spawnMob(instance, plan.mobType(), plan.marker(), spawned))) {
                    spawned++;
                    primedPlans.add(plan);
                }
            }
            org.tinylog.Logger.info(
                    "Primed {} Ravengard encounter mobs near player spawns and across outer regions; "
                            + "active={}/{} types={} distribution={}",
                    primedPlans.size(), activated.size(), plans.size(),
                    encounterTypeDistribution(primedPlans), encounterDistribution(primedPlans));
        }

        private synchronized void activate(net.minestom.server.instance.Instance instance,
                                           Map<UUID, net.minestom.server.coordinate.Pos> players) {
            lastObservedPositions.keySet().retainAll(players.keySet());
            activationMovement.keySet().retainAll(players.keySet());
            var spawnedPlans = new ArrayList<MobSpawnPlan>();
            var selected = new java.util.LinkedHashSet<>(retryable);
            for (var player : players.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                var previous = lastObservedPositions.put(player.getKey(), player.getValue());
                if (previous == null) {
                    selected.addAll(nearestUnactivatedPlans(
                            plans, player.getValue(), activated, IMMEDIATE_ACTIVATION_MOBS));
                    activationMovement.put(player.getKey(), 0.0);
                    continue;
                }
                double movement = accumulateEncounterMovement(
                        activationMovement.getOrDefault(player.getKey(), 0.0), previous, player.getValue());
                if (!movedForEncounterActivation(movement)) {
                    activationMovement.put(player.getKey(), movement);
                    continue;
                }
                activationMovement.put(player.getKey(), 0.0);
                selected.addAll(nearestUnactivatedEncounterClusters(
                        plans, player.getValue(), activated, ENCOUNTERS_PER_ACTIVATION));
            }
            retryable.clear();
            for (int index : selected) {
                MobSpawnPlan plan = plans.get(index);
                if (activatePlan(activated, index,
                        () -> spawnMob(instance, plan.mobType(), plan.marker(), spawned))) {
                    spawned++;
                    spawnedPlans.add(plan);
                } else if (!activated.contains(index)) {
                    retryable.add(index);
                }
            }
            if (!selected.isEmpty()) {
                org.tinylog.Logger.info(
                        "Activated {} of {} Ravengard encounter mobs from nearby movement and retries; "
                                + "retryable={} active={}/{} types={} distribution={}",
                        spawnedPlans.size(), selected.size(), retryable.size(), activated.size(), plans.size(),
                        encounterTypeDistribution(spawnedPlans), encounterDistribution(spawnedPlans));
            }
        }

        private void clear() {
            activated.clear();
            retryable.clear();
            lastObservedPositions.clear();
            activationMovement.clear();
        }
    }

    static boolean activatePlan(Set<Integer> activated, int index,
                                java.util.function.BooleanSupplier spawner) {
        if (index < 0 || activated.contains(index) || !spawner.getAsBoolean()) return false;
        activated.add(index);
        return true;
    }

    static List<Integer> openingEncounterIndexes(List<MobSpawnPlan> plans, int perRegion) {
        return openingOuterEncounterIndexes(plans, perRegion);
    }

    static List<Integer> openingEncounterIndexes(
            List<MobSpawnPlan> plans,
            List<net.minestom.server.coordinate.Pos> spawnPoints,
            int perRegion) {
        if (plans.isEmpty()) return List.of();
        var selected = new java.util.LinkedHashSet<Integer>();
        for (var spawnPoint : spawnPoints) {
            addOpeningSpawnEncounters(plans, spawnPoint, selected);
        }
        selected.addAll(openingOuterEncounterIndexes(plans, perRegion));
        return List.copyOf(selected);
    }

    private static void addOpeningSpawnEncounters(
            List<MobSpawnPlan> plans,
            net.minestom.server.coordinate.Pos spawnPoint,
            Set<Integer> selected) {
        var candidates = new ArrayList<Integer>();
        for (int index = 0; index < plans.size(); index++) {
            if (selected.contains(index)) continue;
            var marker = plans.get(index).marker();
            if (horizontalDistanceSquared(marker, spawnPoint) <= OPENING_SPAWN_RADIUS_SQUARED) {
                candidates.add(index);
            }
        }
        candidates.sort(java.util.Comparator
                .comparingDouble((Integer index) -> horizontalDistanceSquared(
                        plans.get(index).marker(), spawnPoint))
                .thenComparingInt(Integer::intValue));
        var usedTypes = new java.util.HashSet<String>();
        for (int index : candidates) {
            if (!usedTypes.add(plans.get(index).mobType())) continue;
            selected.add(index);
            if (usedTypes.size() >= OPENING_TYPES_PER_SPAWN
                    || selectedNearSpawn(plans, spawnPoint, selected) >= OPENING_MOBS_PER_SPAWN) {
                break;
            }
        }
        for (int index : candidates) {
            if (selectedNearSpawn(plans, spawnPoint, selected) >= OPENING_MOBS_PER_SPAWN) break;
            selected.add(index);
        }
    }

    private static int selectedNearSpawn(
            List<MobSpawnPlan> plans,
            net.minestom.server.coordinate.Pos spawnPoint,
            Set<Integer> selected) {
        return (int) selected.stream()
                .filter(index -> horizontalDistanceSquared(
                        plans.get(index).marker(), spawnPoint) <= OPENING_SPAWN_RADIUS_SQUARED)
                .count();
    }

    private static List<Integer> openingOuterEncounterIndexes(List<MobSpawnPlan> plans, int perRegion) {
        if (plans.isEmpty() || perRegion <= 0) return List.of();
        var selected = new ArrayList<Integer>();
        for (String region : List.of("WN", "WS", "EN", "ES")) {
            var candidates = new ArrayList<Integer>();
            for (int index = 0; index < plans.size(); index++) {
                if (encounterRegion(plans.get(index).marker()).equals(region)) candidates.add(index);
            }
            candidates.sort(java.util.Comparator
                    .comparingDouble((Integer index) -> -distanceFromCentreSquared(plans.get(index).marker()))
                    .thenComparingInt(Integer::intValue));
            var usedTypes = new java.util.HashSet<String>();
            for (int index : candidates) {
                if (!usedTypes.add(plans.get(index).mobType())) continue;
                selected.add(index);
                if (usedTypes.size() >= perRegion) break;
            }
            if (usedTypes.size() >= perRegion) continue;
            int selectedInRegion = usedTypes.size();
            for (int index : candidates) {
                if (selected.contains(index)) continue;
                selected.add(index);
                if (++selectedInRegion >= perRegion) break;
            }
        }
        return List.copyOf(selected);
    }

    static List<Integer> nearestUnactivatedEncounterCluster(
            List<MobSpawnPlan> plans,
            net.minestom.server.coordinate.Pos player,
            Set<Integer> activated) {
        return nearestUnactivatedEncounterClusters(plans, player, activated, 1);
    }

    static List<Integer> nearestUnactivatedEncounterClusters(
            List<MobSpawnPlan> plans,
            net.minestom.server.coordinate.Pos player,
            Set<Integer> activated,
            int maximumEncounters) {
        if (maximumEncounters <= 0) return List.of();
        var distances = new java.util.HashMap<Integer, Double>();
        for (int index = 0; index < plans.size(); index++) {
            if (activated.contains(index)) continue;
            var plan = plans.get(index);
            double distance = horizontalDistanceSquared(plan.marker(), player);
            if (distance > ENCOUNTER_ACTIVATION_RADIUS_SQUARED) continue;
            distances.merge(plan.encounterId(), distance, Math::min);
        }
        var encounterIds = distances.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue()
                        .thenComparingInt(Map.Entry::getKey))
                .limit(maximumEncounters)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        if (encounterIds.isEmpty()) return List.of();
        var selected = new ArrayList<Integer>();
        for (int index = 0; index < plans.size(); index++) {
            if (!activated.contains(index) && encounterIds.contains(plans.get(index).encounterId())) {
                selected.add(index);
            }
        }
        return List.copyOf(selected);
    }

    static List<Integer> nearestUnactivatedPlans(
            List<MobSpawnPlan> plans,
            net.minestom.server.coordinate.Pos player,
            Set<Integer> activated,
            int maximumPlans) {
        if (maximumPlans <= 0) return List.of();
        return java.util.stream.IntStream.range(0, plans.size())
                .filter(index -> !activated.contains(index))
                .boxed()
                .filter(index -> horizontalDistanceSquared(plans.get(index).marker(), player)
                        <= ENCOUNTER_ACTIVATION_RADIUS_SQUARED)
                .sorted(java.util.Comparator
                        .comparingDouble((Integer index) -> horizontalDistanceSquared(
                                plans.get(index).marker(), player))
                        .thenComparingInt(Integer::intValue))
                .limit(maximumPlans)
                .toList();
    }

    static boolean movedForEncounterActivation(
            net.minestom.server.coordinate.Pos previous,
            net.minestom.server.coordinate.Pos current) {
        return movedForEncounterActivation(accumulateEncounterMovement(0, previous, current));
    }

    static boolean movedForEncounterActivation(double movement) {
        return movement >= ENCOUNTER_ACTIVATION_MOVEMENT;
    }

    static double accumulateEncounterMovement(
            double accumulated,
            net.minestom.server.coordinate.Pos previous,
            net.minestom.server.coordinate.Pos current) {
        double dx = current.x() - previous.x();
        double dz = current.z() - previous.z();
        return accumulated + Math.sqrt(dx * dx + dz * dz);
    }

    private static net.minestom.server.coordinate.Pos nearestSpawnPoint(
            net.minestom.server.coordinate.Pos player,
            List<net.minestom.server.coordinate.Pos> spawnPoints) {
        return spawnPoints.stream()
                .min(java.util.Comparator.comparingDouble(spawn -> {
                    double dx = player.x() - spawn.x();
                    double dz = player.z() - spawn.z();
                    return dx * dx + dz * dz;
                }))
                .orElse(player);
    }

    private static String encounterRegion(RavengardDungeonConfig.DungeonObject marker) {
        return (marker.x() < 0 ? "W" : "E") + (marker.z() < 0 ? "N" : "S");
    }

    private static double distanceFromCentreSquared(RavengardDungeonConfig.DungeonObject marker) {
        return marker.x() * marker.x() + marker.z() * marker.z();
    }

    record MobSpawnPlan(String mobType, RavengardDungeonConfig.DungeonObject marker, int encounterId) {
        MobSpawnPlan(String mobType, RavengardDungeonConfig.DungeonObject marker) {
            this(mobType, marker, encounterIdFor(marker));
        }
    }

    record EncounterActivationDiagnostics(
            int planned, int initialNearby, int opening, int openingTypes, int dormant) {
    }

    record EncounterSpawnCoverage(int spawnIndex, int mobs, int types) {
    }

    static List<MobSpawnPlan> planEncounterMobs(
            List<List<RavengardDungeonConfig.DungeonObject>> clusters,
            Set<String> available, long seed) {
        return planEncounterMobs(clusters, available, seed, 0);
    }

    private static List<MobSpawnPlan> planEncounterMobs(
            List<List<RavengardDungeonConfig.DungeonObject>> clusters,
            Set<String> available, long seed, int firstEncounterId) {
        var plans = new ArrayList<MobSpawnPlan>();
        for (int clusterIndex = 0; clusterIndex < clusters.size(); clusterIndex++) {
            var cluster = clusters.get(clusterIndex);
            if (cluster.isEmpty()) continue;
            var candidates = encounterSpawnMarkers(cluster, encounterMobCount(cluster));
            java.util.Random random = new java.util.Random(
                    seed ^ (0x9E3779B97F4A7C15L * (clusterIndex + 1L)));
            var types = encounterMobTypes(cluster, available, random, candidates.size());
            for (int spawnIndex = 0; spawnIndex < candidates.size(); spawnIndex++) {
                plans.add(new MobSpawnPlan(types.get(spawnIndex), candidates.get(spawnIndex),
                        firstEncounterId + clusterIndex));
            }
        }
        return ensureAppearanceCoverage(plans, available, seed);
    }

    static List<MobSpawnPlan> ensureAppearanceCoverage(List<MobSpawnPlan> plans,
                                                        Set<String> available, long seed) {
        if (plans.isEmpty() || available.isEmpty()) return List.copyOf(plans);
        var covered = plans.stream().map(MobSpawnPlan::mobType)
                .collect(java.util.stream.Collectors.toSet());
        var missing = available.stream()
                .filter(COMBAT_MOB_TYPES::contains)
                .filter(type -> !covered.contains(type))
                .sorted()
                .toList();
        if (missing.isEmpty()) return List.copyOf(plans);

        var replaceable = new ArrayList<Integer>();
        for (int index = 0; index < plans.size(); index++) {
            MobSpawnPlan plan = plans.get(index);
            if (!"splinter".equals(plan.marker().type())) continue;
            if (encounterTypeCount(plans, plan.mobType()) <= 1) continue;
            replaceable.add(index);
        }
        java.util.Collections.shuffle(replaceable, new java.util.Random(seed ^ 0x52A4_5645_4E47_4152L));
        if (replaceable.size() < missing.size()) return List.copyOf(plans);

        var balanced = new ArrayList<>(plans);
        for (int index = 0; index < missing.size(); index++) {
            int planIndex = replaceable.get(index);
            balanced.set(planIndex, new MobSpawnPlan(
                    missing.get(index), plans.get(planIndex).marker(), plans.get(planIndex).encounterId()));
        }
        return List.copyOf(balanced);
    }

    private static long encounterTypeCount(List<MobSpawnPlan> plans, String type) {
        return plans.stream().filter(plan -> plan.mobType().equals(type)).count();
    }

    static Map<String, Integer> encounterDistribution(List<MobSpawnPlan> plans) {
        var distribution = new java.util.TreeMap<String, Integer>();
        for (var plan : plans) {
            var marker = plan.marker();
            String quadrant = (marker.x() < 0 ? "W" : "E") + (marker.z() < 0 ? "N" : "S");
            distribution.merge(quadrant, 1, Integer::sum);
            if (Math.abs(marker.x()) >= 200 || Math.abs(marker.z()) >= 200) {
                distribution.merge("outer200", 1, Integer::sum);
            }
        }
        return Map.copyOf(distribution);
    }

    static Set<String> combatAppearances(Set<String> availableClips) {
        var appearances = new java.util.HashSet<String>();
        for (String type : COMBAT_MOB_TYPES) {
            if (availableClips.contains(animationClipType(type))) appearances.add(type);
        }
        return Set.copyOf(appearances);
    }

    static Set<String> runtimeCombatAppearances(Set<String> availableClips) {
        var appearances = new java.util.HashSet<>(combatAppearances(availableClips));
        appearances.removeIf(DungeonInstanceRegistry::isChestMimicType);
        return Set.copyOf(appearances);
    }

    static boolean isChestMimicType(String type) {
        return type != null && (type.equals("silver_mimic")
                || type.equals("normal_mimic")
                || type.equals("mini_mimic")
                || type.startsWith("bottle_mimic_"));
    }

    private static String animationClipType(String type) {
        if (type.equals("demon_archer") || type.equals("demon_archer_elite")) {
            return "skeleton_archer";
        }
        if (type.equals("normal_mimic") || type.equals("mini_mimic")) return "silver_mimic";
        if (type.equals("dire_wolf")) return "zombie_knight";
        if (type.equals("cursed_knight") || type.equals("guardian") || type.equals("wraith_boss")) {
            return "skeleton_knight";
        }
        if (type.equals("iron_core") || type.equals("warden")) return "demon_grunt";
        if (type.equals("wraith")) return "skeleton_warrior";
        if (type.startsWith("bottle_mimic_")) return "bottle_mimic_short_yellow";
        return type.endsWith("_elite") ? type.substring(0, type.length() - "_elite".length()) : type;
    }

    static int encounterMobCount(int markerCount) {
        if (markerCount <= 0) return 0;
        return Math.min(MAX_MOBS_PER_CLUSTER, Math.max(1, (markerCount + MARKERS_PER_MOB - 1) / MARKERS_PER_MOB));
    }

    static int encounterMobCount(List<RavengardDungeonConfig.DungeonObject> cluster) {
        long splinters = cluster.stream().filter(marker -> marker.type().equals("splinter")).count();
        long specials = cluster.size() - splinters;
        int splinterMobs = splinters == 0 ? 0 : (int) ((splinters + MARKERS_PER_MOB - 1) / MARKERS_PER_MOB);
        int specialMobs = specials == 0 ? 0 : (int) ((specials + MARKERS_PER_MOB - 1) / MARKERS_PER_MOB);
        return Math.min(MAX_MOBS_PER_CLUSTER, Math.max(splinterMobs, specialMobs));
    }

    static String encounterMobType(List<RavengardDungeonConfig.DungeonObject> cluster,
                                   java.util.Set<String> available, java.util.Random random, int spawnIndex) {
        var selected = encounterMobTypes(cluster, available, random, spawnIndex + 1);
        return selected.isEmpty() ? null : selected.get(spawnIndex);
    }

    static List<String> encounterMobTypes(List<RavengardDungeonConfig.DungeonObject> cluster,
                                          java.util.Set<String> available, java.util.Random random, int count) {
        java.util.Set<String> markerTypes = cluster.stream()
                .map(RavengardDungeonConfig.DungeonObject::type)
                .collect(java.util.stream.Collectors.toSet());
        List<String> preferred;
        String signatureType = null;
        if (markerTypes.contains("mini_eye") || markerTypes.contains("eye_beam")) {
            preferred = EYE_MOB_POOL;
            signatureType = "oculus";
        } else if (markerTypes.contains("fire_spit")) {
            preferred = FIRE_MOB_POOL;
            signatureType = "demon_imp";
        } else if (markerTypes.contains("dark_tornado")) {
            preferred = TORNADO_MOB_POOL;
        } else {
            preferred = SPLINTER_MOB_POOL;
        }
        var usable = preferred.stream()
                .filter(COMBAT_MOB_TYPES::contains)
                .filter(available::contains)
                .toList();
        if (usable.isEmpty()) {
            usable = available.stream()
                    .filter(COMBAT_MOB_TYPES::contains)
                    .sorted()
                    .toList();
        }
        if (usable.isEmpty() || count <= 0) return List.of();
        var ordered = new ArrayList<>(usable);
        java.util.Collections.shuffle(ordered, random);
        var selected = new ArrayList<String>(count);
        if (signatureType != null && ordered.remove(signatureType)) {
            selected.add(signatureType);
        }
        for (int index = 0; index < count; index++) {
            if (selected.size() >= count) break;
            selected.add(ordered.isEmpty()
                    ? signatureType
                    : ordered.get(index % ordered.size()));
        }
        var rare = RARE_MOB_POOL.stream().filter(available::contains).toList();
        int rareStart = signatureType == null ? 0 : 1;
        for (int index = rareStart; index < selected.size() && !rare.isEmpty(); index++) {
            if (random.nextInt(RARE_MOB_ROLL) == 0) {
                selected.set(index, rare.get(random.nextInt(rare.size())));
            }
        }
        return List.copyOf(selected);
    }

    static List<RavengardDungeonConfig.DungeonObject> encounterSpawnMarkers(
            List<RavengardDungeonConfig.DungeonObject> cluster, int count) {
        if (count <= 0 || cluster.isEmpty()) return List.of();
        var ordered = cluster.stream()
                .sorted(java.util.Comparator.comparingDouble(RavengardDungeonConfig.DungeonObject::x)
                        .thenComparingDouble(RavengardDungeonConfig.DungeonObject::z)
                        .thenComparingDouble(RavengardDungeonConfig.DungeonObject::y))
                .toList();
        var selected = new ArrayList<RavengardDungeonConfig.DungeonObject>();
        selected.add(furthestFromCentre(ordered));
        while (selected.size() < count) {
            RavengardDungeonConfig.DungeonObject furthest = null;
            double bestDistance = -1;
            for (var candidate : ordered) {
                if (selected.contains(candidate)) continue;
                double nearest = selected.stream()
                        .mapToDouble(existing -> horizontalDistanceSquared(candidate, existing))
                        .min().orElse(Double.POSITIVE_INFINITY);
                if (nearest > bestDistance) {
                    bestDistance = nearest;
                    furthest = candidate;
                }
            }
            if (furthest == null) break;
            if (bestDistance < ENCOUNTER_MOB_SEPARATION_SQUARED && selected.size() > 0) {
                furthest = ordered.stream().filter(candidate -> !selected.contains(candidate)).findFirst().orElse(null);
                if (furthest == null) break;
            }
            selected.add(furthest);
        }
        return selected;
    }

    private static RavengardDungeonConfig.DungeonObject furthestFromCentre(
            List<RavengardDungeonConfig.DungeonObject> cluster) {
        var centre = clusterCentre(cluster);
        return cluster.stream()
                .max(java.util.Comparator.comparingDouble(candidate -> horizontalDistanceSquared(candidate, centre)))
                .orElse(cluster.getFirst());
    }

    private static double horizontalDistanceSquared(RavengardDungeonConfig.DungeonObject first,
                                                     RavengardDungeonConfig.DungeonObject second) {
        double x = first.x() - second.x();
        double z = first.z() - second.z();
        return x * x + z * z;
    }

    private static double horizontalDistanceSquared(
            RavengardDungeonConfig.DungeonObject marker,
            net.minestom.server.coordinate.Pos position) {
        double x = marker.x() - position.x();
        double z = marker.z() - position.z();
        return x * x + z * z;
    }

    private static int encounterIdFor(RavengardDungeonConfig.DungeonObject marker) {
        long x = Math.round(marker.x() * 1000.0);
        long y = Math.round(marker.y() * 1000.0);
        long z = Math.round(marker.z() * 1000.0);
        return java.util.Objects.hash(marker.type(), x, y, z) & Integer.MAX_VALUE;
    }

    private static boolean spawnMob(net.minestom.server.instance.Instance instance, String mobType,
                                    RavengardDungeonConfig.DungeonObject object, int index) {
        try {
            var clip = net.swofty.type.ravengardgeneric.entity.animation.RavengardMobClip.load(animationClipType(mobType));
            new net.swofty.type.ravengardgeneric.entity.mob.RavengardMob(clip,
                    new net.minestom.server.coordinate.Pos(object.x(), object.y(), object.z(),
                            object.yaw(), object.pitch()), mobType,
                    defeated -> net.swofty.type.ravengarddungeon.interactables.DungeonSatchel
                            .spawn(instance, defeated.getPosition()))
                    .spawnMob(instance);
            return true;
        } catch (RuntimeException exception) {
            org.tinylog.Logger.warn("Unable to spawn Ravengard mob #{} type {} at {},{},{}: {}",
                    index, mobType, object.x(), object.y(), object.z(), exception.getMessage());
            return false;
        }
    }

    private static double horizontalDistance(double firstX, double firstZ, double secondX, double secondZ) {
        return Math.hypot(firstX - secondX, firstZ - secondZ);
    }

    private static List<RavengardDungeonConfig.DungeonObject> generatedObjects(
            RavengardDungeonGenerator.GeneratedDungeon generated) {
        return generated.objects().stream()
                .map(object -> new RavengardDungeonConfig.DungeonObject(
                        object.category(), object.type(), object.x(), object.y(), object.z(),
                        (float) object.yaw(), 0f))
                .toList();
    }

    static List<net.minestom.server.coordinate.Pos> extractionAnchors(
            RavengardDungeonGenerator.GeneratedDungeon generated) {
        List<net.minestom.server.coordinate.Pos> anchors = new ArrayList<>();
        for (RoomPlacement placement : generated.dungeon().getPlacements()) {
            DungeonRoom room = placement.room();
            if (!"cyan".equalsIgnoreCase(room.getColor())) continue;
            ExtractionAnchorSource source = cyanAnchorSource(room);
            double[] local = placement.toPlacementFrame(source.x(), source.z());
            Integer worldFeetY = extractionGroundY(placement,
                    (int) Math.round(local[0]), (int) Math.round(local[1]));
            anchors.add(new net.minestom.server.coordinate.Pos(
                    placement.originX() + local[0] + 0.5,
                    worldFeetY == null ? source.feetY() : worldFeetY,
                    placement.originZ() + local[1] + 0.5,
                    extractionYaw(placement.rotation()), 0f));
        }
        return List.copyOf(anchors);
    }

    public static List<net.minestom.server.coordinate.Pos> officialExtractionAnchors() {
        List<net.minestom.server.coordinate.Pos> anchors = new ArrayList<>();
        for (DungeonRoom room : RavengardDungeonGenerator.getCatalog().getRooms()) {
            if (!"cyan".equalsIgnoreCase(room.getColor())) continue;
            ExtractionAnchorSource source = cyanAnchorSource(room);
            Integer feetY = extractionGroundY(room, source.x(), source.z());
            anchors.add(new net.minestom.server.coordinate.Pos(
                    room.getMinX() + source.x() + 0.5,
                    feetY == null ? source.feetY() : feetY,
                    room.getMinZ() + source.z() + 0.5,
                    0f, 0f));
        }
        return List.copyOf(anchors);
    }

    private static float extractionYaw(net.swofty.dungeons.ravengard.Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> 90f;
            case CLOCKWISE_180 -> 180f;
            case CLOCKWISE_270 -> 270f;
            case NONE -> 0f;
        };
    }

    static ExtractionAnchorSource cyanAnchorSource(DungeonRoom room) {
        List<int[]> cells = new ArrayList<>();
        for (int[] run : room.getMask()) {
            for (int x = run[1]; x <= run[2]; x++) cells.add(new int[]{x, run[0]});
        }
        if (cells.isEmpty()) return new ExtractionAnchorSource(
                Math.max(0, room.getWidth() / 2), Math.max(0, room.getDepth() / 2), 67);
        java.util.Set<Long> mask = new java.util.HashSet<>();
        for (int[] cell : cells) {
            mask.add((((long) cell[0]) << 32) | (cell[1] & 0xFFFFFFFFL));
        }
        java.util.Set<Long> primaryComponent = largestMaskComponent(mask);
        cells.removeIf(cell -> !primaryComponent.contains(maskKey(cell[0], cell[1])));
        mask.removeIf(key -> !primaryComponent.contains(key));

        if (room.getId().equals("room_22") || room.getId().equals("room_46")) {
            ExtractionAnchorSource openEnd = widestHorizontalRunAnchor(room, mask);
            if (openEnd != null) return openEnd;
        }

        double centreX = cells.stream().mapToDouble(cell -> cell[0]).average().orElse(0);
        double centreZ = cells.stream().mapToDouble(cell -> cell[1]).average().orElse(0);

        // A portal occupies a 2x2 footprint.  Choosing a single mask cell
        // puts it on the edge of L-shaped/courtyard rooms, where the model
        // intersects a wall or the surrounding terrain.
        List<int[]> candidates = new ArrayList<>();
        for (int[] cell : cells) {
            int x = cell[0], z = cell[1];
            if (mask.contains(maskKey(x + 1, z))
                    && mask.contains(maskKey(x, z + 1))
                    && mask.contains(maskKey(x + 1, z + 1))) {
                candidates.add(new int[]{x, z});
            }
        }
        boolean hasTwoByTwoCandidates = !candidates.isEmpty();
        if (candidates.isEmpty()) candidates.addAll(cells);

        if (hasTwoByTwoCandidates) {
            candidates.removeIf(cell -> {
                Integer floorY = extractionGroundY(room, cell[0], cell[1]);
                if (floorY == null) return true;
                for (int dx = 0; dx < 2; dx++) {
                    for (int dz = 0; dz < 2; dz++) {
                        Integer neighbourY = extractionGroundY(room, cell[0] + dx, cell[1] + dz);
                        if (neighbourY == null || neighbourY.intValue() != floorY.intValue()) return true;
                    }
                }
                return false;
            });
            if (candidates.isEmpty()) candidates.addAll(cells);
        }

        int candidateMinX = candidates.stream().mapToInt(cell -> cell[0]).min().orElse(0);
        int candidateMaxX = candidates.stream().mapToInt(cell -> cell[0]).max().orElse(0);
        boolean candidatesOnlyOnRoomEdge = candidateMaxX - candidateMinX <= 2
                && (candidateMinX <= 1 || candidateMaxX >= room.getWidth() - 3);
        if (candidatesOnlyOnRoomEdge) {
            ExtractionAnchorSource openEnd = widestHorizontalRunAnchor(room, mask);
            if (openEnd != null) return openEnd;
        }

        java.util.Map<Integer, Integer> groundCounts = new java.util.HashMap<>();
        java.util.Map<Long, Integer> groundByCell = new java.util.HashMap<>();
        for (int[] cell : candidates) {
            Integer feetY = extractionGroundY(room, cell[0], cell[1]);
            if (feetY != null) {
                groundByCell.put(maskKey(cell[0], cell[1]), feetY);
                groundCounts.merge(feetY, 1, Integer::sum);
            }
        }
        int preferredFeetY = groundCounts.entrySet().stream()
                .max(java.util.Comparator.<java.util.Map.Entry<Integer, Integer>>comparingInt(java.util.Map.Entry::getValue)
                        .thenComparingInt(entry -> -Math.abs(entry.getKey() - 67)))
                .map(java.util.Map.Entry::getKey).orElse(67);

        candidates.sort(java.util.Comparator
                .<int[]>comparingInt(cell -> -interiorClearance(cell[0], cell[1], mask,
                        room.getWidth(), room.getDepth()))
                .thenComparingDouble(cell -> Math.pow(cell[0] + 0.5 - centreX, 2)
                        + Math.pow(cell[1] + 0.5 - centreZ, 2)));
        for (int[] cell : candidates) {
            Integer feetY = groundByCell.get(maskKey(cell[0], cell[1]));
            if (feetY != null && feetY == preferredFeetY) {
                return new ExtractionAnchorSource(cell[0], cell[1], feetY);
            }
        }
        for (int[] cell : candidates) {
            Integer feetY = groundByCell.get(maskKey(cell[0], cell[1]));
            if (feetY != null) return new ExtractionAnchorSource(cell[0], cell[1], feetY);
        }
        int[] fallback = candidates.getFirst();
        return new ExtractionAnchorSource(fallback[0], fallback[1], 67);
    }

    private static ExtractionAnchorSource widestHorizontalRunAnchor(
            DungeonRoom room, java.util.Set<Long> mask) {
        java.util.Map<Integer, java.util.Set<Integer>> rowCells = new java.util.HashMap<>();
        for (long key : mask) {
            int x = (int) (key >> 32);
            int z = (int) key;
            rowCells.computeIfAbsent(z, ignored -> new java.util.HashSet<>()).add(x);
        }
        int[] widest = null;
        int widestZ = 0;
        for (var entry : rowCells.entrySet()) {
            java.util.List<Integer> xs = entry.getValue().stream().sorted().toList();
            if (xs.isEmpty()) continue;
            int start = xs.getFirst();
            int previous = start;
            for (int index = 1; index <= xs.size(); index++) {
                if (index < xs.size() && xs.get(index) == previous + 1) {
                    previous = xs.get(index);
                    continue;
                }
                int[] run = {start, previous};
                if (widest == null || run[1] - run[0] > widest[1] - widest[0]) {
                    widest = run;
                    widestZ = entry.getKey();
                }
                if (index < xs.size()) start = previous = xs.get(index);
            }
        }
        if (widest == null) return null;
        int x = widest[0] + Math.max(0, (widest[1] - widest[0]) / 2);
        int z = widestZ;
        Integer feetY = extractionGroundY(room, x, z);
        return feetY == null ? new ExtractionAnchorSource(x, z, 67)
                : new ExtractionAnchorSource(x, z, feetY);
    }

    private static java.util.Set<Long> largestMaskComponent(java.util.Set<Long> mask) {
        java.util.Set<Long> remaining = new java.util.HashSet<>(mask);
        java.util.Set<Long> largest = java.util.Set.of();
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!remaining.isEmpty()) {
            long seed = remaining.iterator().next();
            java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
            java.util.Set<Long> component = new java.util.HashSet<>();
            queue.add(seed);
            remaining.remove(seed);
            while (!queue.isEmpty()) {
                long current = queue.removeFirst();
                component.add(current);
                int x = (int) (current >> 32);
                int z = (int) current;
                for (int[] direction : directions) {
                    long neighbour = maskKey(x + direction[0], z + direction[1]);
                    if (remaining.remove(neighbour)) queue.addLast(neighbour);
                }
            }
            if (component.size() > largest.size()) largest = component;
        }
        return largest;
    }

    private static long maskKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int interiorClearance(int x, int z, java.util.Set<Long> mask,
                                         int width, int depth) {
        int clearance = Integer.MAX_VALUE;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            int distance = 0;
            int nextX = x;
            int nextZ = z;
            while (distance < Math.max(width, depth)) {
                nextX += direction[0];
                nextZ += direction[1];
                if (!mask.contains((((long) nextX) << 32) | (nextZ & 0xFFFFFFFFL))) break;
                distance++;
            }
            clearance = Math.min(clearance, distance);
        }
        return clearance;
    }

    private static Integer extractionGroundY(RoomPlacement placement, int localX, int localZ) {
        DungeonTemplate template = DungeonTemplate.get();
        int worldX = placement.originX() + localX;
        int worldZ = placement.originZ() + localZ;
        for (int y = 72; y >= 60; y--) {
            var floor = template.blockAt(worldX, y, worldZ);
            if (!floor.solid() || floor == net.minestom.server.instance.block.Block.BARRIER) continue;
            if (template.blockAt(worldX, y + 1, worldZ) == net.minestom.server.instance.block.Block.AIR
                    && template.blockAt(worldX, y + 2, worldZ) == net.minestom.server.instance.block.Block.AIR) {
                return y + 1;
            }
        }
        return null;
    }

    private static Integer extractionGroundY(DungeonRoom room, int sourceX, int sourceZ) {
        int absoluteX = room.getMinX() + sourceX;
        int absoluteZ = room.getMinZ() + sourceZ;
        DungeonTemplate template = DungeonTemplate.get();
        for (int y = 72; y >= 60; y--) {
            var floor = template.blockAt(absoluteX, y, absoluteZ);
            if (!floor.solid() || floor == net.minestom.server.instance.block.Block.BARRIER) continue;
            if (template.blockAt(absoluteX, y + 1, absoluteZ) == net.minestom.server.instance.block.Block.AIR
                    && template.blockAt(absoluteX, y + 2, absoluteZ) == net.minestom.server.instance.block.Block.AIR) {
                return y + 1;
            }
        }
        return null;
    }

    record ExtractionAnchorSource(int x, int z, int feetY) {
    }

    static List<List<RavengardDungeonConfig.DungeonObject>> clusterMarkers(
            List<RavengardDungeonConfig.DungeonObject> markers, double radius) {
        var remaining = new java.util.LinkedHashSet<>(markers);
        var clusters = new ArrayList<List<RavengardDungeonConfig.DungeonObject>>();
        double radiusSquared = radius * radius;
        while (!remaining.isEmpty()) {
            var queue = new java.util.ArrayDeque<RavengardDungeonConfig.DungeonObject>();
            var cluster = new ArrayList<RavengardDungeonConfig.DungeonObject>();
            var start = remaining.iterator().next();
            remaining.remove(start);
            queue.add(start);
            while (!queue.isEmpty()) {
                var current = queue.removeFirst();
                cluster.add(current);
                var nearby = remaining.stream()
                        .filter(candidate -> {
                            double dx = candidate.x() - current.x();
                            double dz = candidate.z() - current.z();
                            return dx * dx + dz * dz <= radiusSquared;
                        })
                        .toList();
                remaining.removeAll(nearby);
                queue.addAll(nearby);
            }
            clusters.add(cluster);
        }
        return clusters;
    }

    private static RavengardDungeonConfig.DungeonObject clusterCentre(
            List<RavengardDungeonConfig.DungeonObject> cluster) {
        double x = 0, y = 0, z = 0;
        for (var object : cluster) {
            x += object.x();
            y += object.y();
            z += object.z();
        }
        var first = cluster.getFirst();
        return new RavengardDungeonConfig.DungeonObject("entity_spawn", first.type(),
                x / cluster.size(), y / cluster.size(), z / cluster.size(), first.yaw(), first.pitch());
    }

    static SpawnResolution resolveSpawnPlans(List<MobSpawnPlan> plans, BlockLookup blocks) {
        var resolved = new ArrayList<MobSpawnPlan>(plans.size());
        int relocated = 0;
        int unresolved = 0;
        for (MobSpawnPlan plan : plans) {
            RavengardDungeonConfig.DungeonObject marker = resolveSpawnMarker(plan.marker(), blocks);
            if (marker == null) {
                unresolved++;
                org.tinylog.Logger.warn("No standable ground near Ravengard encounter marker type={} pos=({}, {}, {})",
                        plan.mobType(), plan.marker().x(), plan.marker().y(), plan.marker().z());
                continue;
            }
            if (marker.x() != plan.marker().x() || marker.z() != plan.marker().z()) relocated++;
            resolved.add(new MobSpawnPlan(plan.mobType(), marker, plan.encounterId()));
        }
        return new SpawnResolution(resolved, relocated, unresolved);
    }

    static RavengardDungeonConfig.DungeonObject resolveSpawnMarker(
            RavengardDungeonConfig.DungeonObject marker, BlockLookup blocks) {
        int originX = (int) Math.floor(marker.x());
        int originZ = (int) Math.floor(marker.z());
        var offsets = new ArrayList<int[]>();
        for (int dx = -ENCOUNTER_GROUND_SEARCH_RADIUS; dx <= ENCOUNTER_GROUND_SEARCH_RADIUS; dx++) {
            for (int dz = -ENCOUNTER_GROUND_SEARCH_RADIUS; dz <= ENCOUNTER_GROUND_SEARCH_RADIUS; dz++) {
                if (dx * dx + dz * dz <= ENCOUNTER_GROUND_SEARCH_RADIUS * ENCOUNTER_GROUND_SEARCH_RADIUS) {
                    offsets.add(new int[]{dx, dz});
                }
            }
        }
        offsets.sort(java.util.Comparator.comparingInt((int[] offset) ->
                        offset[0] * offset[0] + offset[1] * offset[1])
                .thenComparingInt(offset -> Math.abs(offset[0]) + Math.abs(offset[1]))
                .thenComparingInt(offset -> offset[0])
                .thenComparingInt(offset -> offset[1]));
        for (int[] offset : offsets) {
            Integer groundY = groundAt(blocks, originX + offset[0], originZ + offset[1], marker.y());
            if (groundY == null) continue;
            double x = offset[0] == 0 ? marker.x() : originX + offset[0] + 0.5;
            double z = offset[1] == 0 ? marker.z() : originZ + offset[1] + 0.5;
            return new RavengardDungeonConfig.DungeonObject(marker.category(), marker.type(),
                    x, groundY, z, marker.yaw(), marker.pitch());
        }
        return null;
    }

    static Integer groundAt(BlockLookup blocks, int blockX, int blockZ, double referenceY) {
        int maximumY = (int) Math.floor(referenceY) + ENCOUNTER_GROUND_SEARCH_UP;
        int minimumY = (int) Math.floor(referenceY) - ENCOUNTER_GROUND_SEARCH_DOWN;
        for (int y = maximumY; y >= minimumY; y--) {
            var floor = blocks.blockAt(blockX, y, blockZ);
            if (floor == null || !floor.solid()) continue;
            var feet = blocks.blockAt(blockX, y + 1, blockZ);
            var head = blocks.blockAt(blockX, y + 2, blockZ);
            if (feet != null && head != null && !feet.solid() && !head.solid()) return y + 1;
        }
        return null;
    }

    @FunctionalInterface
    interface BlockLookup {
        net.minestom.server.instance.block.Block blockAt(int x, int y, int z);
    }

    record SpawnResolution(List<MobSpawnPlan> plans, int relocated, int unresolved) {
        SpawnResolution {
            plans = List.copyOf(plans);
        }
    }

    public static DungeonInstance get(UUID gameId) {
        return INSTANCES.get(gameId);
    }

    public static List<DungeonInstance> all() {
        return new ArrayList<>(INSTANCES.values());
    }

    public static int remainingSlots() {
        return Math.max(0, MAX_INSTANCES - INSTANCES.size());
    }

    public static DungeonInstance findByIdPrefix(String prefix) {
        for (DungeonInstance instance : INSTANCES.values()) {
            if (instance.getGameId().toString().equals(prefix)
                    || instance.getGameId().toString().startsWith(prefix)) {
                return instance;
            }
        }
        return null;
    }

    public static void forfeitRun(RavengardDungeonPlayer player) {
        DungeonInstance instance = findByPlayer(player.getUuid());
        if (instance != null) {
            instance.getGame().forfeitRun(player);
            return;
        }
        var game = net.swofty.type.ravengarddungeon.TypeRavengardDungeonLoader.getGame();
        if (game != null) {
            game.forfeitRun(player);
        }
    }

    public static DungeonInstance findByPlayer(UUID playerUuid) {
        for (DungeonInstance instance : INSTANCES.values()) {
            if (instance.getPlayers().contains(playerUuid)) {
                return instance;
            }
        }
        return null;
    }

    public static DungeonInstance findActiveByPlayer(UUID playerUuid) {
        return findActiveByPlayer(playerUuid, INSTANCES.values(),
                (instance, uuid) -> instance.getGame().getPlayer(uuid).isPresent());
    }

    static <T> T findActiveByPlayer(UUID playerUuid, Iterable<T> instances,
                                    BiPredicate<T, UUID> hasActivePlayer) {
        for (T instance : instances) {
            if (hasActivePlayer.test(instance, playerUuid)) return instance;
        }
        return null;
    }

    static <T> List<T> reconcilePlayerMemberships(UUID playerUuid, T target,
                                                   Iterable<T> instances,
                                                   Function<T, Set<UUID>> memberships) {
        List<T> emptied = new ArrayList<>();
        for (T instance : instances) {
            Set<UUID> players = memberships.apply(instance);
            if (instance != target && players.remove(playerUuid) && players.isEmpty()) {
                emptied.add(instance);
            }
        }
        memberships.apply(target).add(playerUuid);
        return emptied;
    }

    static <T> List<T> reconcileActiveGameMemberships(UUID playerUuid, T target,
                                                       Iterable<T> instances,
                                                       BiPredicate<T, UUID> detachPlayer) {
        List<T> detached = new ArrayList<>();
        for (T instance : instances) {
            if (instance != target && detachPlayer.test(instance, playerUuid)) {
                detached.add(instance);
            }
        }
        return detached;
    }

    static <T> boolean removeOwnedMembership(UUID playerUuid, T owner,
                                              Iterable<T> instances,
                                              Function<T, Set<UUID>> memberships) {
        for (T instance : instances) {
            if (instance == owner) return memberships.apply(instance).remove(playerUuid);
        }
        return false;
    }

    static <K, T> boolean reserveRegisteredMembership(K key, T target, UUID playerUuid,
                                                       Map<K, T> registered,
                                                       Predicate<T> retiring,
                                                       Function<T, Set<UUID>> memberships) {
        if (registered.get(key) != target || retiring.test(target)) return false;
        memberships.apply(target).add(playerUuid);
        return true;
    }

    public static boolean tryReserve(DungeonInstance instance, UUID playerUuid) {
        synchronized (DungeonInstanceRegistry.class) {
            if (INSTANCES.get(instance.getGameId()) != instance || instance.retiring) return false;
            boolean reused = instance.getPlayers().contains(playerUuid);
            if (!reused && !instance.getMode().equals("ADMIN") && !instance.isAcceptingJoins()) {
                return false;
            }
            if (!reserveRegisteredMembership(instance.getGameId(), instance, playerUuid,
                    INSTANCES, candidate -> candidate.retiring, DungeonInstance::getPlayers)) {
                return false;
            }
            long now = System.currentTimeMillis();
            for (DungeonInstance emptied : reconcilePlayerMemberships(
                    playerUuid, instance, INSTANCES.values(), DungeonInstance::getPlayers)) {
                emptied.emptySince = now;
            }
            instance.emptySince = 0;
            return true;
        }
    }

    public static boolean releaseReservation(DungeonInstance instance, UUID playerUuid) {
        synchronized (DungeonInstanceRegistry.class) {
            boolean removed = removeOwnedMembership(
                    playerUuid, instance, INSTANCES.values(), DungeonInstance::getPlayers);
            if (removed && instance.getPlayers().isEmpty()) {
                instance.emptySince = System.currentTimeMillis();
            }
            return removed;
        }
    }

    static <T> boolean drainBeforeUnregister(List<T> livePlayers,
                                             Consumer<T> routePlayer,
                                             Runnable unregister) {
        if (!livePlayers.isEmpty()) {
            livePlayers.forEach(routePlayer);
            return false;
        }
        unregister.run();
        return true;
    }

    private static final Set<UUID> PENDING_TELEPORTS = ConcurrentHashMap.newKeySet();
    private static final int PLAY_READY_TIMEOUT_TICKS = 400;

    public static void sendPlayerIn(HypixelPlayer player, DungeonInstance instance) {
        if (!PENDING_TELEPORTS.add(player.getUuid())) {
            player.sendMessage("<7>Your dungeon is still being prepared, hold on...");
            return;
        }
        boolean reserved = tryReserve(instance, player.getUuid());
        if (!reserved) {
            PENDING_TELEPORTS.remove(player.getUuid());
            player.sendMessage("<c>This dungeon is no longer accepting players.");
            player.sendTo(net.swofty.commons.ServerType.RAVENGARD_LOBBY);
            return;
        }
        player.sendMessage("<7>Preparing your dungeon (seed <f>{}<7>)...", instance.getSeed());
        boolean aerial = instance.getMode().equals("ADMIN");
        instance.whenReady().whenComplete((ignored, error) -> player.scheduler().scheduleNextTick(() -> {
            if (error != null) {
                instance.markPlayerLeft(player.getUuid());
                PENDING_TELEPORTS.remove(player.getUuid());
                player.sendMessage("<c>Dungeon preparation failed: {}", error.getMessage());
                player.sendTo(net.swofty.commons.ServerType.RAVENGARD_LOBBY);
                return;
            }
            joinPlayerWhenPlayReady(player, instance, aerial, 0);
        }));
    }

    private static void joinPlayerWhenPlayReady(HypixelPlayer player, DungeonInstance instance,
                                                boolean aerial, int waitedTicks) {
        if (!player.isOnline() || INSTANCES.get(instance.getGameId()) != instance || instance.retiring) {
            instance.markPlayerLeft(player.getUuid());
            PENDING_TELEPORTS.remove(player.getUuid());
            return;
        }
        var connection = player.getPlayerConnection();
        boolean playReady = connection.getClientState() == net.minestom.server.network.ConnectionState.PLAY
                && connection.getServerState() == net.minestom.server.network.ConnectionState.PLAY;
        if (!playReady) {
            if (waitedTicks >= PLAY_READY_TIMEOUT_TICKS) {
                instance.markPlayerLeft(player.getUuid());
                PENDING_TELEPORTS.remove(player.getUuid());
                org.tinylog.Logger.warn("Timed out waiting for PLAY state before moving {} into dungeon {}: client={} server={}",
                        player.getUsername(), instance.getGameId(), connection.getClientState(), connection.getServerState());
                return;
            }
            player.scheduler().scheduleNextTick(() ->
                    joinPlayerWhenPlayReady(player, instance, aerial, waitedTicks + 1));
            return;
        }

        try {
            net.swofty.type.game.game.Game.JoinResult result =
                    instance.getGame().join((RavengardDungeonPlayer) player);
            if (!(result instanceof net.swofty.type.game.game.Game.JoinResult.Success)) {
                instance.markPlayerLeft(player.getUuid());
                String reason = result instanceof net.swofty.type.game.game.Game.JoinResult.Denied denied
                        ? denied.reason() : "Dungeon join was redirected";
                player.sendMessage("<c>Could not enter the dungeon: {}", reason);
                player.sendTo(net.swofty.commons.ServerType.RAVENGARD_LOBBY);
                return;
            }
            synchronized (DungeonInstanceRegistry.class) {
                reconcileActiveGameMemberships(player.getUuid(), instance, INSTANCES.values(),
                        (candidate, uuid) -> candidate.getGame().detachForTransfer(uuid));
                instance.reconcilePlayerJoined(player.getUuid());
            }
            if (aerial) {
                var start = instance.getGenerated().dungeon().getStartRoom();
                Integer startRoof = start.room().getRoofY();
                double hoverY = (startRoof != null ? startRoof : 97) + 40;
                player.setGameMode(net.minestom.server.entity.GameMode.CREATIVE);
                player.teleport(new net.minestom.server.coordinate.Pos(
                        start.originX() + start.getFootprintWidth() / 2.0, hoverY,
                        start.originZ() + start.getFootprintDepth() / 2.0, 0, 90));
            }
            player.sendMessage("<a>Entered dungeon <f>{}<a> ({} rooms, mode {}).",
                    instance.getGameId().toString().substring(0, 8),
                    instance.getGenerated().dungeon().getRoomCount(),
                    instance.getMode());
            scheduleChunkRefresh(player, instance);
        } finally {
            PENDING_TELEPORTS.remove(player.getUuid());
        }
    }

    public static boolean canInteract(net.minestom.server.entity.Player player) {
        if (!(player instanceof RavengardDungeonPlayer dungeonPlayer)) return false;
        DungeonInstance instance = findByPlayer(player.getUuid());
        if (instance != null) return instance.getGame().canInteract(dungeonPlayer);
        var game = net.swofty.type.ravengarddungeon.TypeRavengardDungeonLoader.getGame();
        return game != null && game.canInteract(dungeonPlayer);
    }

    public static boolean disconnectPlayer(RavengardDungeonPlayer player) {
        PENDING_TELEPORTS.remove(player.getUuid());
        boolean handled = false;
        for (DungeonInstance instance : INSTANCES.values()) {
            boolean member = instance.getPlayers().remove(player.getUuid());
            if (!member && !instance.getGame().isParticipant(player.getUuid())) continue;
            instance.getGame().disconnect(player);
            if (instance.getPlayers().isEmpty()) {
                instance.emptySince = System.currentTimeMillis();
            }
            handled = true;
        }
        return handled;
    }

    public static void restoreRun(RavengardDungeonPlayer player) {
        for (DungeonInstance instance : INSTANCES.values()) {
            if (instance.getGame().restoreRun(player)) {
                player.sendMessage("<e>Your run continues.");
                return;
            }
        }
    }

    public static boolean markPlayerLeft(UUID playerUuid) {
        boolean removed = false;
        for (DungeonInstance instance : INSTANCES.values()) {
            if (instance.getPlayers().remove(playerUuid)) {
                removed = true;
                if (instance.getPlayers().isEmpty()) {
                    instance.emptySince = System.currentTimeMillis();
                }
            }
        }
        return removed;
    }

    public static boolean markPlayerLeft(UUID playerUuid, RavengardDungeonGame owner) {
        DungeonInstance instance = INSTANCES.values().stream()
                .filter(candidate -> candidate.getGame() == owner)
                .findFirst()
                .orElse(null);
        if (instance == null) return false;
        boolean removed = removeOwnedMembership(
                playerUuid, instance, INSTANCES.values(), DungeonInstance::getPlayers);
        if (removed && instance.getPlayers().isEmpty()) {
            instance.emptySince = System.currentTimeMillis();
        }
        return removed;
    }

    public static void onPlayerExtracted(UUID playerUuid) {
        markPlayerLeft(playerUuid);
    }

    /** Chunk sections that reach the client while a resource reload is in flight
     * are silently dropped from its renderer and a client-side rebuild cannot
     * recover them, so the world is re-sent once the player has settled in. */
    private static void scheduleChunkRefresh(net.minestom.server.entity.Player player,
                                             DungeonInstance instance) {
        player.scheduler().buildTask(() -> {
            if (player.getInstance() != instance.getInstance()) {
                return;
            }
            int viewDistance = net.minestom.server.MinecraftServer.getChunkViewDistance() + 2;
            int playerChunkX = player.getPosition().chunkX();
            int playerChunkZ = player.getPosition().chunkZ();
            List<CompletableFuture<?>> loads = new ArrayList<>();
            for (int chunkX = playerChunkX - viewDistance; chunkX <= playerChunkX + viewDistance; chunkX++) {
                for (int chunkZ = playerChunkZ - viewDistance; chunkZ <= playerChunkZ + viewDistance; chunkZ++) {
                    loads.add(instance.getInstance().loadChunk(chunkX, chunkZ));
                }
            }
            CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).thenRun(() -> {
                if (player.getInstance() != instance.getInstance()) return;
                int sent = 0;
                for (var chunk : instance.getInstance().getChunks()) {
                    if (Math.abs(chunk.getChunkX() - playerChunkX) <= viewDistance
                            && Math.abs(chunk.getChunkZ() - playerChunkZ) <= viewDistance) {
                        chunk.sendChunk(player);
                        sent++;
                    }
                }
                org.tinylog.Logger.info("Refreshed {} loaded chunks for {}", sent, player.getUsername());
            });
        }).delay(net.minestom.server.timer.TaskSchedule.tick(40)).schedule();
    }

    public static double[] boundsCenter(DungeonInstance instance) {
        double[] bounds = bounds(instance.generated);
        return new double[]{bounds[0], bounds[1]};
    }

    private static double[] bounds(RavengardDungeonGenerator.GeneratedDungeon generated) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (var placement : generated.dungeon().getPlacements()) {
            minX = Math.min(minX, placement.originX());
            maxX = Math.max(maxX, placement.originX() + placement.getFootprintWidth());
            minZ = Math.min(minZ, placement.originZ());
            maxZ = Math.max(maxZ, placement.originZ() + placement.getFootprintDepth());
        }
        double centerX = (minX + maxX) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        double radius = Math.max(maxX - minX, maxZ - minZ) / 2.0 + 8.0;
        return new double[]{centerX, centerZ, radius};
    }

    public static void startExpiryTask() {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            activateNearbyEncounters();
            for (DungeonInstance instance : INSTANCES.values()) {
                if (!instance.beginRetirementIfExpired()) continue;
                List<net.minestom.server.entity.Player> livePlayers =
                        List.copyOf(instance.getInstance().getPlayers());
                drainBeforeUnregister(livePlayers,
                        player -> routeRetiringPlayer(instance, player),
                        () -> unregisterDrainedInstance(instance));
            }
        }).repeat(net.minestom.server.timer.TaskSchedule.seconds(1)).schedule();
    }

    private static void routeRetiringPlayer(DungeonInstance instance,
                                             net.minestom.server.entity.Player player) {
        instance.retainLivePlayer(player.getUuid());
        if (!instance.retirementRoutes.add(player.getUuid())) return;
        org.tinylog.Logger.warn(
                "Deferring expired dungeon cleanup while player {} remains in instance {}; routing to lobby",
                player.getUsername(), instance.getGameId());
        if (player instanceof HypixelPlayer hypixelPlayer) {
            hypixelPlayer.sendMessage("<e>This dungeon expired and is being cleaned up.");
            hypixelPlayer.asProxyPlayer()
                    .transferToWithIndication(net.swofty.commons.ServerType.RAVENGARD_LOBBY)
                    .exceptionally(error -> {
                        instance.retirementRoutes.remove(player.getUuid());
                        return null;
                    });
        }
    }

    private static void unregisterDrainedInstance(DungeonInstance instance) {
        synchronized (DungeonInstanceRegistry.class) {
            if (!instance.getInstance().getPlayers().isEmpty()) return;
            if (INSTANCES.get(instance.getGameId()) != instance) return;
            instance.getGame().dispose();
            if (!instance.getInstance().getPlayers().isEmpty()) return;
            removeEncounters(instance.getInstance());
            net.swofty.type.ravengardgeneric.entity.mob.RavengardMob
                    .removeInstance(instance.getInstance());
            net.swofty.type.ravengarddungeon.interactables.InteractableRegistry
                    .removeInstance(instance.getInstance());
            MinecraftServer.getInstanceManager().unregisterInstance(instance.getInstance());
            MinecraftServer.getInstanceManager().unregisterInstance(instance.getContainer());
            INSTANCES.remove(instance.getGameId(), instance);
        }
    }
}
