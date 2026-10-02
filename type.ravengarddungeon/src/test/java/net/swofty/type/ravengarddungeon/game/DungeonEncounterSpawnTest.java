package net.swofty.type.ravengarddungeon.game;

import com.google.gson.Gson;
import net.minestom.server.MinecraftServer;
import net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minestom.server.instance.block.Block;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonEncounterSpawnTest {
    @BeforeAll
    static void initializeMinestom() {
        MinecraftServer.init();
    }

    @AfterAll
    static void stopMinestom() {
        MinecraftServer.stopCleanly();
    }

    @Test
    void encounterPopulationScalesWithCapturedMarkerDensity() {
        assertEquals(0, DungeonInstanceRegistry.encounterMobCount(0));
        assertEquals(1, DungeonInstanceRegistry.encounterMobCount(1));
        assertEquals(2, DungeonInstanceRegistry.encounterMobCount(5));
        assertEquals(2, DungeonInstanceRegistry.encounterMobCount(6));
        assertEquals(5, DungeonInstanceRegistry.encounterMobCount(14));
        assertEquals(5, DungeonInstanceRegistry.encounterMobCount(30));
        assertEquals(1, DungeonInstanceRegistry.encounterMobCount(
                List.of(marker("eye_beam", 0, 0))));
        assertEquals(2, DungeonInstanceRegistry.encounterMobCount(
                java.util.stream.IntStream.range(0, 4)
                        .mapToObj(index -> marker("fire_spit", index * 0.1, 0)).toList()));
        assertEquals(5, DungeonInstanceRegistry.encounterMobCount(
                java.util.stream.IntStream.range(0, 14)
                        .mapToObj(index -> marker("splinter", index * 0.1, 0)).toList()));
    }

    @Test
    void splinterEncountersUseSeveralExistingCombatClips() {
        var cluster = List.of(marker("splinter", 0, 0));
        Set<String> available = Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight", "demon_grunt");
        var used = new HashSet<String>();
        Random random = new Random(9917);

        for (int index = 0; index < 80; index++) {
            used.add(DungeonInstanceRegistry.encounterMobType(cluster, available, random, index));
        }

        assertTrue(used.contains("zombie_knight"));
        assertTrue(used.contains("demon_grunt"));
        assertTrue(used.size() >= 4);
    }

    @Test
    void markerSemanticsSelectOnlyClipsThatActuallyExist() {
        Set<String> available = Set.of("oculus", "demon_imp", "demon_grunt", "zombie_knight");
        assertEquals("oculus", DungeonInstanceRegistry.encounterMobType(
                List.of(marker("eye_beam", 0, 0)), available, new Random(1), 0));
        assertEquals("demon_imp", DungeonInstanceRegistry.encounterMobType(
                List.of(marker("fire_spit", 0, 0)), available, new Random(2), 0));
        assertTrue(Set.of("demon_grunt", "zombie_knight").contains(
                DungeonInstanceRegistry.encounterMobType(
                        List.of(marker("dark_tornado", 0, 0)), available, new Random(3), 0)));
    }

    @Test
    void denseClusterSelectsDistinctCapturedPositions() {
        var cluster = new ArrayList<RavengardDungeonConfig.DungeonObject>();
        cluster.add(marker("splinter", 0, 0));
        cluster.add(marker("splinter", 0.5, 0));
        cluster.add(marker("splinter", 5, 0));
        cluster.add(marker("splinter", 0, 5));
        cluster.add(marker("splinter", 5, 5));

        var selected = DungeonInstanceRegistry.encounterSpawnMarkers(cluster, 3);

        assertEquals(3, selected.size());
        assertEquals(3, new HashSet<>(selected).size());
        assertTrue(selected.stream().allMatch(cluster::contains));
        double centreDistance = selected.stream()
                .mapToDouble(marker -> Math.hypot(marker.x() - 2.1, marker.z() - 2.0))
                .max().orElseThrow();
        assertTrue(centreDistance >= 3.4);
    }

    @Test
    void oneEncounterUsesDistinctAvailableTypesBeforeRepeating() {
        var cluster = List.of(marker("splinter", 0, 0));
        var selected = DungeonInstanceRegistry.encounterMobTypes(cluster,
                Set.of("skeleton_archer", "skeleton_knight", "skeleton_warrior"),
                new Random(44), 5);

        assertEquals(5, selected.size());
        assertEquals(3, new HashSet<>(selected.subList(0, 3)).size());
    }

    @Test
    void capturedMarkerFamiliesExerciseAllSevenCombatEncounterTypes() {
        Set<String> available = Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight",
                "demon_grunt", "demon_imp", "oculus");
        Set<String> selected = new HashSet<>();
        selected.addAll(DungeonInstanceRegistry.encounterMobTypes(
                List.of(marker("splinter", 0, 0)), available, new Random(1), 10));
        selected.addAll(DungeonInstanceRegistry.encounterMobTypes(
                List.of(marker("eye_beam", 0, 0)), available, new Random(2), 2));
        selected.addAll(DungeonInstanceRegistry.encounterMobTypes(
                List.of(marker("fire_spit", 0, 0)), available, new Random(3), 3));

        assertEquals(available, selected);
    }

    @Test
    void nonCombatAndDecorativeClipsNeverEnterEncounterPools() {
        var selected = DungeonInstanceRegistry.encounterMobTypes(
                List.of(marker("fire_spit", 0, 0)),
                Set.of("training_dummy", "commander", "guard_archer", "demon_imp",
                        "demon_grunt", "zombie_knight"),
                new Random(2), 10);

        assertTrue(selected.stream().allMatch(Set.of("demon_imp", "demon_grunt", "zombie_knight")::contains));
        assertTrue(selected.contains("demon_imp"));
        assertTrue(selected.stream().noneMatch(Set.of(
                "training_dummy", "commander", "guard_archer")::contains));
    }

    @Test
    void officialMarkersRestoreOuterMapEncountersAndExactDuplicatesAreRemoved() {
        var generated = List.of(
                marker("splinter", 0, 0),
                new RavengardDungeonConfig.DungeonObject("door", "door_1", 5, 65, 5, 0, 0));
        var official = List.of(
                marker("splinter", 0, 0),
                marker("eye_beam", 220, -190),
                marker("dark_tornado", -230, 210));

        var merged = DungeonInstanceRegistry.mergeEncounterMarkers(generated, official);

        assertEquals(1, merged.generatedMarkers());
        assertEquals(3, merged.officialMarkers());
        assertEquals(1, merged.duplicateMarkers());
        assertEquals(3, merged.mergedMarkers());
        var markers = merged.objects().stream()
                .filter(object -> object.category().equals("entity_spawn")).toList();
        assertEquals(3, markers.size());
        assertTrue(markers.stream().anyMatch(marker -> marker.x() > 200));
        assertTrue(markers.stream().anyMatch(marker -> marker.x() < -200));
    }

    @Test
    void officialDungeonDefinesEncounterMarkersAcrossTheFullCity() throws Exception {
        var config = new Gson().fromJson(Files.readString(Path.of("..", "configuration", "ravengard",
                "dungeon_1.json")), RavengardDungeonConfig.class);
        var markers = config.objects().stream()
                .filter(object -> object.category().equals("entity_spawn")).toList();

        assertEquals(300, markers.size());
        assertTrue(markers.stream().mapToDouble(RavengardDungeonConfig.DungeonObject::x).min().orElseThrow() < -220);
        assertTrue(markers.stream().mapToDouble(RavengardDungeonConfig.DungeonObject::x).max().orElseThrow() > 220);
        assertTrue(markers.stream().mapToDouble(RavengardDungeonConfig.DungeonObject::z).min().orElseThrow() < -220);
        assertTrue(markers.stream().mapToDouble(RavengardDungeonConfig.DungeonObject::z).max().orElseThrow() > 220);
    }

    @Test
    void officialEncounterPopulationIsDistributedAndNoLongerOneMobPerSevenMarkerGroup() throws Exception {
        var config = new Gson().fromJson(Files.readString(Path.of("..", "configuration", "ravengard",
                "dungeon_1.json")), RavengardDungeonConfig.class);
        var markers = config.objects().stream()
                .filter(object -> object.category().equals("entity_spawn")).toList();
        var clusters = DungeonInstanceRegistry.clusterMarkers(markers, 6.0);

        int planned = clusters.stream().mapToInt(DungeonInstanceRegistry::encounterMobCount).sum();
        assertEquals(54, clusters.size());
        assertEquals(130, planned);
        assertTrue(clusters.stream().filter(cluster -> cluster.size() == 7)
                .allMatch(cluster -> DungeonInstanceRegistry.encounterMobCount(cluster) == 3));
        assertTrue(clusters.stream().anyMatch(cluster -> cluster.stream().anyMatch(marker -> marker.x() < -220)));
        assertTrue(clusters.stream().anyMatch(cluster -> cluster.stream().anyMatch(marker -> marker.x() > 220)));
        assertTrue(clusters.stream().anyMatch(cluster -> cluster.stream().anyMatch(marker -> marker.z() < -220)));
        assertTrue(clusters.stream().anyMatch(cluster -> cluster.stream().anyMatch(marker -> marker.z() > 220)));
    }

    @Test
    void eliteAppearancesRequireTheirCapturedBaseCombatClip() {
        var appearances = DungeonInstanceRegistry.combatAppearances(Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "demon_imp",
                "silver_mimic", "bottle_mimic_short_yellow", "training_dummy"));

        assertTrue(appearances.contains("skeleton_archer"));
        assertTrue(appearances.contains("skeleton_archer_elite"));
        assertTrue(appearances.contains("skeleton_knight_elite"));
        assertTrue(appearances.contains("skeleton_warrior_elite"));
        assertTrue(appearances.contains("demon_imp_elite"));
        assertTrue(appearances.stream().noneMatch(type -> type.contains("mimic")));
        assertTrue(appearances.stream().noneMatch(type -> type.contains("training_dummy")));
        assertTrue(appearances.stream().noneMatch(type -> type.startsWith("oculus")));
    }

    @Test
    void encountersOnlyUseMobsWithAnOfficialCapture() {
        Set<String> available = DungeonInstanceRegistry.combatAppearances(Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight",
                "demon_grunt", "demon_imp", "oculus", "silver_mimic", "bottle_mimic_short_yellow"));
        Set<String> captured = Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight",
                "demon_grunt", "demon_imp", "oculus");
        Set<String> used = new HashSet<>();

        for (String marker : List.of("splinter", "mini_eye", "fire_spit", "dark_tornado")) {
            for (long seed = 0; seed < 500; seed++) {
                used.addAll(DungeonInstanceRegistry.encounterMobTypes(
                        List.of(marker(marker, seed, 0)), available, new Random(seed), 5));
            }
        }

        assertEquals(available, used);
        for (String type : used) {
            assertTrue(captured.contains(type.replaceFirst("_elite$", "")), type);
        }
    }

    @Test
    void officialSpawnPlanPopulatesAllQuadrantsAndOuterCity() throws Exception {
        var config = new Gson().fromJson(Files.readString(Path.of("..", "configuration", "ravengard",
                "dungeon_1.json")), RavengardDungeonConfig.class);
        var markers = config.objects().stream()
                .filter(object -> object.category().equals("entity_spawn")).toList();
        var clusters = DungeonInstanceRegistry.clusterMarkers(markers, 6.0);
        Set<String> appearances = DungeonInstanceRegistry.combatAppearances(Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight",
                "demon_grunt", "demon_imp", "oculus", "silver_mimic",
                "bottle_mimic_short_yellow"));

        var plans = DungeonInstanceRegistry.planEncounterMobs(clusters, appearances, 919191L);
        Map<String, Integer> distribution = DungeonInstanceRegistry.encounterDistribution(plans);

        assertEquals(130, plans.size());
        assertTrue(distribution.getOrDefault("WN", 0) >= 20, distribution.toString());
        assertTrue(distribution.getOrDefault("WS", 0) >= 25, distribution.toString());
        assertTrue(distribution.getOrDefault("EN", 0) >= 30, distribution.toString());
        assertTrue(distribution.getOrDefault("ES", 0) >= 30, distribution.toString());
        assertTrue(distribution.getOrDefault("outer200", 0) >= 50, distribution.toString());
        assertTrue(plans.stream().allMatch(plan -> markers.contains(plan.marker())));
    }

    @Test
    void officialSpawnPlanUsesBroadVisualPoolAcrossSeeds() throws Exception {
        var config = new Gson().fromJson(Files.readString(Path.of("..", "configuration", "ravengard",
                "dungeon_1.json")), RavengardDungeonConfig.class);
        var clusters = DungeonInstanceRegistry.clusterMarkers(config.objects().stream()
                .filter(object -> object.category().equals("entity_spawn")).toList(), 6.0);
        Set<String> appearances = DungeonInstanceRegistry.combatAppearances(Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight",
                "demon_grunt", "demon_imp", "oculus", "silver_mimic",
                "bottle_mimic_short_yellow"));
        var used = new HashSet<String>();

        for (long seed = 0; seed < 12; seed++) {
            var plans = DungeonInstanceRegistry.planEncounterMobs(clusters, appearances, seed);
            assertTrue(plans.stream().map(DungeonInstanceRegistry.MobSpawnPlan::mobType).distinct().count() >= 10);
            used.addAll(plans.stream().map(DungeonInstanceRegistry.MobSpawnPlan::mobType).toList());
        }

        assertEquals(14, appearances.size());
        assertEquals(appearances, used);
        assertTrue(used.containsAll(Set.of("oculus", "oculus_elite", "demon_imp", "demon_grunt",
                "skeleton_archer", "skeleton_knight",
                "skeleton_warrior", "skeleton_warrior_elite", "zombie_knight")));
        assertTrue(appearances.containsAll(used));
    }

    @Test
    void spawnMarkerKeepsOfficialCoordinatesWhenTheyAreStandable() {
        var marker = marker("splinter", 12.25, -8.75);
        var resolved = DungeonInstanceRegistry.resolveSpawnMarker(marker,
                (x, y, z) -> x == 12 && z == -9 && y == 64 ? Block.STONE : Block.AIR);

        assertEquals(12.25, resolved.x());
        assertEquals(-8.75, resolved.z());
        assertEquals(65, resolved.y());
    }

    @Test
    void blockedOfficialMarkerMovesOnlyToNearbyStandableGround() {
        var marker = marker("splinter", 10.2, 20.2);
        var resolved = DungeonInstanceRegistry.resolveSpawnMarker(marker, (x, y, z) -> {
            if (x == 10 && z == 20) return Block.STONE;
            return x == 9 && z == 20 && y == 63 ? Block.STONE : Block.AIR;
        });

        assertEquals(9.5, resolved.x());
        assertEquals(20.2, resolved.z());
        assertEquals(64, resolved.y());
        assertTrue(Math.hypot(resolved.x() - marker.x(), resolved.z() - marker.z()) < 2);
    }

    @Test
    void aMarkerCapturedInsideBarsLandsBesideThemNotOnTop() {
        var marker = new RavengardDungeonConfig.DungeonObject("entity_spawn", "splinter", 12.71, 65.53, 171.83, 0, 0);
        var resolved = DungeonInstanceRegistry.resolveSpawnMarker(marker, (x, y, z) -> {
            if (y <= 64) return Block.STONE;
            return x == 12 && z == 171 && y <= 67 ? Block.IRON_BARS : Block.AIR;
        });

        assertEquals(65, resolved.y());
        assertEquals(1, Math.abs(Math.floor(resolved.x()) - 12) + Math.abs(Math.floor(resolved.z()) - 171));
    }

    @Test
    void aMarkerUnderAnOverhangStaysOnTheFloorItWasCapturedOn() {
        var resolved = DungeonInstanceRegistry.resolveSpawnMarker(marker("splinter", 4.5, 4.5),
                (x, y, z) -> y <= 64 || y == 67 ? Block.STONE : Block.AIR);

        assertEquals(4.5, resolved.x());
        assertEquals(65, resolved.y());
    }

    @Test
    void decorationsWithoutCollisionAreNeitherGroundNorObstacle() {
        var marker = new RavengardDungeonConfig.DungeonObject("entity_spawn", "splinter", 47.51, 64.53, 107.51, 0, 0);
        var resolved = DungeonInstanceRegistry.resolveSpawnMarker(marker, (x, y, z) -> {
            if (y <= 63) return Block.STONE;
            return y == 65 ? Block.DEAD_BUBBLE_CORAL_WALL_FAN : Block.AIR;
        });

        assertEquals(47.51, resolved.x());
        assertEquals(64, resolved.y());
    }

    @Test
    void invisibleBarrierFurnitureIsNotGround() {
        var marker = new RavengardDungeonConfig.DungeonObject("entity_spawn", "splinter", 221.85, 65.5, 133.27, 0, 0);
        var resolved = DungeonInstanceRegistry.resolveSpawnMarker(marker, (x, y, z) -> {
            if (y <= 62) return Block.STONE;
            return x == 221 && y <= 64 ? Block.BARRIER : Block.AIR;
        });

        assertEquals(63, resolved.y());
        assertEquals(1, Math.abs(Math.floor(resolved.x()) - 221));
    }

    @Test
    void narrowBlocksAreNotGround() {
        var resolved = DungeonInstanceRegistry.resolveSpawnMarker(
                new RavengardDungeonConfig.DungeonObject("entity_spawn", "eye_beam", 8.5, 69.3, 8.5, 0, 0),
                (x, y, z) -> {
                    if (y <= 64) return Block.STONE;
                    return x == 8 && z == 8 && y <= 66 ? Block.OAK_FENCE : Block.AIR;
                });

        assertEquals(65, resolved.y());
        assertTrue(Math.floor(resolved.x()) != 8 || Math.floor(resolved.z()) != 8);
    }

    @Test
    void railingsConnectedOnEverySideAreStillNotGround() {
        Block railing = Block.IRON_BARS.withProperties(Map.of(
                "north", "true", "east", "true", "south", "true", "west", "true"));
        var resolved = DungeonInstanceRegistry.resolveSpawnMarker(
                new RavengardDungeonConfig.DungeonObject("entity_spawn", "eye_beam", 8.5, 69.3, 8.5, 0, 0),
                (x, y, z) -> {
                    if (y <= 64) return Block.STONE;
                    return x == 8 && z == 8 && y <= 66 ? railing : Block.AIR;
                });

        assertEquals(65, resolved.y());
        assertTrue(Math.floor(resolved.x()) != 8 || Math.floor(resolved.z()) != 8);
    }

    @Test
    void aMarkerNeverDropsThroughTheFloorIntoARoomBelow() {
        var marker = new RavengardDungeonConfig.DungeonObject("entity_spawn", "splinter", 3.5, 64.9, 3.5, 0, 0);
        var resolved = DungeonInstanceRegistry.resolveSpawnMarker(marker,
                (x, y, z) -> y == 64 || y <= 61 ? Block.STONE : Block.AIR);

        assertEquals(3.5, resolved.x());
        assertEquals(65, resolved.y());
    }

    @Test
    void aMarkerSunkIntoTheFloorRisesOntoIt() {
        var marker = new RavengardDungeonConfig.DungeonObject("entity_spawn", "splinter", 3.5, 64.6, 3.5, 0, 0);
        var resolved = DungeonInstanceRegistry.resolveSpawnMarker(marker,
                (x, y, z) -> y <= 64 ? Block.STONE : Block.AIR);

        assertEquals(3.5, resolved.x());
        assertEquals(65, resolved.y());
    }

    @Test
    void unresolvedMarkerIsSkippedInsteadOfFallingBackToDungeonCentre() {
        var plan = new DungeonInstanceRegistry.MobSpawnPlan(
                "skeleton_archer", marker("splinter", 230, -225));

        var resolution = DungeonInstanceRegistry.resolveSpawnPlans(
                List.of(plan), (x, y, z) -> Block.AIR);

        assertTrue(resolution.plans().isEmpty());
        assertEquals(0, resolution.relocated());
        assertEquals(1, resolution.unresolved());
    }

    @Test
    void groundedOfficialPlanRetainsAllActivationRegions() throws Exception {
        var config = new Gson().fromJson(Files.readString(Path.of("..", "configuration", "ravengard",
                "dungeon_1.json")), RavengardDungeonConfig.class);
        var clusters = DungeonInstanceRegistry.clusterMarkers(config.objects().stream()
                .filter(object -> object.category().equals("entity_spawn")).toList(), 6.0);
        Set<String> appearances = DungeonInstanceRegistry.combatAppearances(Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight",
                "demon_grunt", "demon_imp", "oculus", "silver_mimic",
                "bottle_mimic_short_yellow"));
        var planned = DungeonInstanceRegistry.planEncounterMobs(clusters, appearances, 919191L);
        var floors = planned.stream().collect(java.util.stream.Collectors.toMap(
                plan -> (int) Math.floor(plan.marker().x()) + ":" + (int) Math.floor(plan.marker().z()),
                plan -> (int) Math.floor(plan.marker().y()) - 1, Math::min));

        var grounded = DungeonInstanceRegistry.resolveSpawnPlans(planned, (x, y, z) ->
                floors.containsKey(x + ":" + z) && y <= floors.get(x + ":" + z) ? Block.STONE : Block.AIR);
        Map<String, Integer> distribution = DungeonInstanceRegistry.encounterDistribution(grounded.plans());

        assertEquals(planned.size(), grounded.plans().size());
        assertEquals(0, grounded.unresolved());
        for (String region : Set.of("WN", "WS", "EN", "ES", "outer200")) {
            assertTrue(distribution.getOrDefault(region, 0) > 0, distribution.toString());
        }
        for (DungeonInstanceRegistry.MobSpawnPlan plan : grounded.plans()) {
            assertEquals(1, DungeonInstanceRegistry.plansWithinActivationRadius(
                    List.of(plan), List.of(new net.minestom.server.coordinate.Pos(
                            plan.marker().x(), plan.marker().y(), plan.marker().z())), Set.of()).size());
        }
    }

    @Test
    void instanceBlockApiFindsGroundAfterLoadingMarkerChunks() {
        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(63, 64, Block.STONE));
        var plan = new DungeonInstanceRegistry.MobSpawnPlan(
                "skeleton_archer", marker("splinter", 220.25, -219.75));

        var resolution = DungeonInstanceRegistry.resolveSpawnPlans(instance, List.of(plan));

        assertEquals(1, resolution.plans().size());
        assertEquals(64, resolution.plans().getFirst().marker().y());
        assertEquals(220.25, resolution.plans().getFirst().marker().x());
        assertEquals(-219.75, resolution.plans().getFirst().marker().z());
        assertTrue(instance.getChunk(13, -14) != null);
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void officialPolarProvidesGroundedActivatablePlansInEveryRegion() throws Exception {
        var config = new Gson().fromJson(Files.readString(Path.of("..", "configuration", "ravengard",
                "dungeon_1.json")), RavengardDungeonConfig.class);
        var clusters = DungeonInstanceRegistry.clusterMarkers(config.objects().stream()
                .filter(object -> object.category().equals("entity_spawn")).toList(), 6.0);
        Set<String> appearances = DungeonInstanceRegistry.combatAppearances(Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight",
                "demon_grunt", "demon_imp", "oculus", "silver_mimic",
                "bottle_mimic_short_yellow"));
        var planned = DungeonInstanceRegistry.planEncounterMobs(clusters, appearances, 919191L);
        var manager = MinecraftServer.getInstanceManager();
        var container = manager.createInstanceContainer();
        var instance = net.swofty.type.generic.world.HypixelWorldLoader.loadFrom(
                Path.of("..", "configuration", "ravengard", "dungeon_1.polar"), container, manager);
        try {
            var grounded = DungeonInstanceRegistry.resolveSpawnPlans(instance, planned);
            Map<String, Integer> distribution = DungeonInstanceRegistry.encounterDistribution(grounded.plans());

            assertEquals(planned.size(), grounded.plans().size());
            assertEquals(0, grounded.unresolved());
            for (String region : Set.of("WN", "WS", "EN", "ES", "outer200")) {
                assertTrue(distribution.getOrDefault(region, 0) > 0, distribution.toString());
            }
            assertTrue(grounded.plans().stream().allMatch(plan ->
                    DungeonInstanceRegistry.plansWithinActivationRadius(List.of(plan),
                            List.of(new net.minestom.server.coordinate.Pos(
                                    plan.marker().x(), plan.marker().y(), plan.marker().z())),
                            Set.of()).size() == 1));
        } finally {
            manager.unregisterInstance(instance);
            manager.unregisterInstance(container);
        }
    }

    @Test
    void clusteringIsStableForTheSameMarkerOrder() throws Exception {
        var config = new Gson().fromJson(Files.readString(Path.of("..", "configuration", "ravengard",
                "dungeon_1.json")), RavengardDungeonConfig.class);
        var markers = config.objects().stream()
                .filter(object -> object.category().equals("entity_spawn")).toList();

        var first = DungeonInstanceRegistry.clusterMarkers(markers, 6.0);
        var second = DungeonInstanceRegistry.clusterMarkers(markers, 6.0);

        assertEquals(first, second);
        assertEquals(
                DungeonInstanceRegistry.planEncounterMobs(first, Set.of("skeleton_archer"), 42),
                DungeonInstanceRegistry.planEncounterMobs(second, Set.of("skeleton_archer"), 42));
    }


    @Test
    void proximityActivationSpawnsOnlyEncountersNearPlayers() {
        var plans = List.of(
                new DungeonInstanceRegistry.MobSpawnPlan("skeleton_archer", marker("splinter", 0, 0)),
                new DungeonInstanceRegistry.MobSpawnPlan("skeleton_knight", marker("splinter", 80, 0)),
                new DungeonInstanceRegistry.MobSpawnPlan("zombie_knight", marker("splinter", 111, 0)),
                new DungeonInstanceRegistry.MobSpawnPlan("demon_imp", marker("fire_spit", 113, 0)),
                new DungeonInstanceRegistry.MobSpawnPlan("demon_grunt", marker("splinter", 200, 200)));
        Set<Integer> activated = new HashSet<>();

        var nearby = DungeonInstanceRegistry.plansWithinActivationRadius(plans,
                List.of(new net.minestom.server.coordinate.Pos(0, 65, 0)), activated);

        assertEquals(List.of(plans.get(0)), nearby);
        activated.add(0);
        assertTrue(DungeonInstanceRegistry.plansWithinActivationRadius(
                plans, List.of(new net.minestom.server.coordinate.Pos(0, 65, 0)), activated).isEmpty());
        assertTrue(DungeonInstanceRegistry.plansWithinActivationRadius(
                plans, List.of(new net.minestom.server.coordinate.Pos(-200, 65, -200)), activated).isEmpty());
    }

    @Test
    void officialSpawnDiagnosticsSeparateInitialEncountersFromDormantOuterPopulation() throws Exception {
        var config = new Gson().fromJson(Files.readString(Path.of("..", "configuration", "ravengard",
                "dungeon_1.json")), RavengardDungeonConfig.class);
        var clusters = DungeonInstanceRegistry.clusterMarkers(config.objects().stream()
                .filter(object -> object.category().equals("entity_spawn")).toList(), 6.0);
        Set<String> appearances = DungeonInstanceRegistry.combatAppearances(Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight",
                "demon_grunt", "demon_imp", "oculus", "silver_mimic",
                "bottle_mimic_short_yellow"));
        var plans = DungeonInstanceRegistry.planEncounterMobs(clusters, appearances, 919191L);

        var diagnostics = DungeonInstanceRegistry.encounterActivationDiagnostics(
                plans, RavengardDungeonGame.SPAWN_POINTS);

        assertEquals(130, diagnostics.planned());
        assertEquals(12, diagnostics.initialNearby());
        assertTrue(diagnostics.opening() >= 12 && diagnostics.opening() <= 30, diagnostics.toString());
        assertTrue(diagnostics.openingTypes() >= 8, diagnostics.toString());
        assertEquals(diagnostics.planned() - diagnostics.opening(), diagnostics.dormant());
    }

    @Test
    void openingAndMovementActivateControlledDiverseClustersAcrossTheCity() throws Exception {
        var config = new Gson().fromJson(Files.readString(Path.of("..", "configuration", "ravengard",
                "dungeon_1.json")), RavengardDungeonConfig.class);
        var clusters = DungeonInstanceRegistry.clusterMarkers(config.objects().stream()
                .filter(object -> object.category().equals("entity_spawn")).toList(), 6.0);
        Set<String> appearances = DungeonInstanceRegistry.combatAppearances(Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight",
                "demon_grunt", "demon_imp", "oculus", "silver_mimic",
                "bottle_mimic_short_yellow"));
        var plans = DungeonInstanceRegistry.planEncounterMobs(clusters, appearances, 919191L);
        var opening = DungeonInstanceRegistry.openingEncounterIndexes(
                plans, RavengardDungeonGame.SPAWN_POINTS, 3);
        Set<Integer> activated = new HashSet<>(opening);

        assertTrue(opening.size() < 40, opening.toString());
        assertTrue(opening.stream().map(plans::get).map(DungeonInstanceRegistry.MobSpawnPlan::mobType)
                .distinct().count() >= 8);
        var spawnCoverage = DungeonInstanceRegistry.encounterSpawnCoverage(
                plans, opening, RavengardDungeonGame.SPAWN_POINTS);
        spawnCoverage.forEach(coverage -> {
            assertTrue(coverage.mobs() >= 6, coverage.toString());
            assertTrue(coverage.types() >= 3, coverage.toString());
        });
        var openingRegions = DungeonInstanceRegistry.encounterDistribution(
                opening.stream().map(plans::get).toList());
        for (String region : Set.of("WN", "WS", "EN", "ES")) {
            assertTrue(openingRegions.getOrDefault(region, 0) >= 3, openingRegions.toString());
        }

        var route = List.of(
                new net.minestom.server.coordinate.Pos(-188, 65, 134),
                new net.minestom.server.coordinate.Pos(188, 65, -134),
                new net.minestom.server.coordinate.Pos(188, 65, 134));
        for (var position : route) {
            int before = activated.size();
            var cluster = DungeonInstanceRegistry.nearestUnactivatedEncounterCluster(
                    plans, position, activated);
            assertFalse(cluster.isEmpty(), position.toString());
            assertEquals(1, cluster.stream().map(index -> plans.get(index).encounterId()).distinct().count());
            cluster.forEach(index ->
                    assertTrue(DungeonInstanceRegistry.activatePlan(activated, index, () -> true)));
            assertTrue(activated.size() - before <= 5);
        }
        assertTrue(activated.size() < plans.size() / 2);
    }

    @Test
    void movingThroughEveryActivationRegionSelectsEachPlanOnlyOnce() {
        var plans = List.of(
                new DungeonInstanceRegistry.MobSpawnPlan("skeleton_archer", marker("splinter", -100, -100)),
                new DungeonInstanceRegistry.MobSpawnPlan("skeleton_knight", marker("splinter", -100, 100)),
                new DungeonInstanceRegistry.MobSpawnPlan("zombie_knight", marker("splinter", 100, -100)),
                new DungeonInstanceRegistry.MobSpawnPlan("demon_grunt", marker("splinter", 100, 100)),
                new DungeonInstanceRegistry.MobSpawnPlan("oculus", marker("eye_beam", 230, 0)));
        Set<Integer> activated = new HashSet<>();

        for (int index = 0; index < plans.size(); index++) {
            var marker = plans.get(index).marker();
            var nearby = DungeonInstanceRegistry.planIndexesWithinActivationRadius(plans,
                    List.of(new net.minestom.server.coordinate.Pos(marker.x(), marker.y(), marker.z())),
                    activated);

            assertEquals(List.of(index), nearby);
            assertTrue(DungeonInstanceRegistry.activatePlan(activated, index, () -> true));
        }

        assertEquals(Set.of(0, 1, 2, 3, 4), activated);
        assertEquals(Map.of("WN", 1, "WS", 1, "EN", 1, "ES", 2, "outer200", 1),
                DungeonInstanceRegistry.encounterDistribution(plans));
        for (DungeonInstanceRegistry.MobSpawnPlan plan : plans) {
            assertTrue(DungeonInstanceRegistry.planIndexesWithinActivationRadius(plans,
                    List.of(new net.minestom.server.coordinate.Pos(
                            plan.marker().x(), plan.marker().y(), plan.marker().z())), activated).isEmpty());
        }
    }

    @Test
    void nearestActivationSelectsOneWholeClusterInsteadOfEveryMobInRadius() {
        var plans = List.of(
                new DungeonInstanceRegistry.MobSpawnPlan("skeleton_archer", marker("splinter", 30, 0), 7),
                new DungeonInstanceRegistry.MobSpawnPlan("skeleton_knight", marker("splinter", 32, 0), 7),
                new DungeonInstanceRegistry.MobSpawnPlan("zombie_knight", marker("splinter", 48, 0), 8),
                new DungeonInstanceRegistry.MobSpawnPlan("demon_grunt", marker("splinter", 50, 0), 8),
                new DungeonInstanceRegistry.MobSpawnPlan("oculus", marker("eye_beam", 70, 0), 9));
        Set<Integer> activated = new HashSet<>();
        var player = new net.minestom.server.coordinate.Pos(0, 65, 0);

        assertEquals(List.of(0, 1),
                DungeonInstanceRegistry.nearestUnactivatedEncounterCluster(plans, player, activated));
        activated.addAll(List.of(0, 1));
        assertEquals(List.of(2, 3),
                DungeonInstanceRegistry.nearestUnactivatedEncounterCluster(plans, player, activated));
        activated.addAll(List.of(2, 3));
        assertEquals(List.of(4),
                DungeonInstanceRegistry.nearestUnactivatedEncounterCluster(plans, player, activated));
    }

    @Test
    void nearbyMovementSelectsTwoWholeClustersWithoutActivatingTheRadius() {
        var plans = List.of(
                new DungeonInstanceRegistry.MobSpawnPlan("skeleton_archer", marker("splinter", 30, 0), 7),
                new DungeonInstanceRegistry.MobSpawnPlan("skeleton_knight", marker("splinter", 32, 0), 7),
                new DungeonInstanceRegistry.MobSpawnPlan("zombie_knight", marker("splinter", 48, 0), 8),
                new DungeonInstanceRegistry.MobSpawnPlan("demon_grunt", marker("splinter", 50, 0), 8),
                new DungeonInstanceRegistry.MobSpawnPlan("oculus", marker("eye_beam", 70, 0), 9));

        var selected = DungeonInstanceRegistry.nearestUnactivatedEncounterClusters(
                plans, new net.minestom.server.coordinate.Pos(0, 65, 0), Set.of(), 2);

        assertEquals(List.of(0, 1, 2, 3), selected);
        assertEquals(2, selected.stream().map(index -> plans.get(index).encounterId()).distinct().count());
    }

    @Test
    void failedNearestClusterDoesNotBlockTheNextNearbyCluster() {
        var plans = List.of(
                new DungeonInstanceRegistry.MobSpawnPlan("skeleton_archer", marker("splinter", 20, 0), 7),
                new DungeonInstanceRegistry.MobSpawnPlan("skeleton_knight", marker("splinter", 35, 0), 8));
        Set<Integer> activated = new HashSet<>();
        var selected = DungeonInstanceRegistry.nearestUnactivatedEncounterClusters(
                plans, new net.minestom.server.coordinate.Pos(0, 65, 0), activated, 2);

        assertFalse(DungeonInstanceRegistry.activatePlan(activated, selected.getFirst(), () -> false));
        assertTrue(DungeonInstanceRegistry.activatePlan(activated, selected.getLast(), () -> true));

        assertEquals(Set.of(1), activated);
        assertEquals(List.of(0), DungeonInstanceRegistry.nearestUnactivatedEncounterCluster(
                plans, new net.minestom.server.coordinate.Pos(0, 65, 0), activated));
    }

    @Test
    void encounterMovementUsesTravelledPathInsteadOfNetDisplacement() {
        var origin = new net.minestom.server.coordinate.Pos(-188.5, 66, -134.5);
        var corner = origin.add(6, 0, 0);
        var current = corner.add(0, 0, 6);
        double movement = DungeonInstanceRegistry.accumulateEncounterMovement(0, origin, corner);
        movement = DungeonInstanceRegistry.accumulateEncounterMovement(movement, corner, current);

        assertFalse(DungeonInstanceRegistry.movedForEncounterActivation(
                origin, origin.add(9.9, 0, 0)));
        assertTrue(DungeonInstanceRegistry.movedForEncounterActivation(
                origin, origin.add(10, 0, 0)));
        assertTrue(DungeonInstanceRegistry.movedForEncounterActivation(movement));
        assertTrue(origin.distanceSquared(current) < 10 * 10);
    }

    @Test
    void valueEqualPlansRetainIndependentActivationIndexes() {
        var first = new DungeonInstanceRegistry.MobSpawnPlan(
                "skeleton_archer", marker("splinter", 25, 25));
        var second = new DungeonInstanceRegistry.MobSpawnPlan(
                "skeleton_archer", marker("splinter", 25, 25));
        var plans = List.of(first, second);
        var player = List.of(new net.minestom.server.coordinate.Pos(25, 65, 25));
        Set<Integer> activated = new HashSet<>();

        assertEquals(List.of(0, 1),
                DungeonInstanceRegistry.planIndexesWithinActivationRadius(plans, player, activated));
        assertTrue(DungeonInstanceRegistry.activatePlan(activated, 0, () -> true));
        assertEquals(List.of(1),
                DungeonInstanceRegistry.planIndexesWithinActivationRadius(plans, player, activated));
        assertTrue(DungeonInstanceRegistry.activatePlan(activated, 1, () -> true));
        assertTrue(DungeonInstanceRegistry.planIndexesWithinActivationRadius(plans, player, activated).isEmpty());
    }

    @Test
    void activationDiagnosticsReportActualSpawnedTypesAndRegions() {
        var plans = List.of(
                new DungeonInstanceRegistry.MobSpawnPlan("skeleton_archer", marker("splinter", -210, -210)),
                new DungeonInstanceRegistry.MobSpawnPlan("zombie_knight", marker("splinter", 215, 205)),
                new DungeonInstanceRegistry.MobSpawnPlan("demon_imp", marker("splinter", 8, -12)));

        assertEquals(Map.of(
                        "demon_imp", 1,
                        "zombie_knight", 1,
                        "skeleton_archer", 1),
                DungeonInstanceRegistry.encounterTypeDistribution(plans));
        var regions = DungeonInstanceRegistry.encounterDistribution(plans);
        assertEquals(1, regions.get("WN"));
        assertEquals(1, regions.get("ES"));
        assertEquals(1, regions.get("EN"));
        assertEquals(2, regions.get("outer200"));
    }

    @Test
    void failedEncounterSpawnRemainsEligibleForRetry() {
        Set<Integer> activated = new HashSet<>();

        assertTrue(!DungeonInstanceRegistry.activatePlan(activated, 4, () -> false));
        assertTrue(activated.isEmpty());
        assertTrue(DungeonInstanceRegistry.activatePlan(activated, 4, () -> true));
        assertEquals(Set.of(4), activated);
        assertTrue(!DungeonInstanceRegistry.activatePlan(activated, 4, () -> true));
    }

    @Test
    void officialEncounterTypeDistributionUsesEveryVerifiedAppearance() throws Exception {
        var config = new Gson().fromJson(Files.readString(Path.of("..", "configuration", "ravengard",
                "dungeon_1.json")), RavengardDungeonConfig.class);
        var clusters = DungeonInstanceRegistry.clusterMarkers(config.objects().stream()
                .filter(object -> object.category().equals("entity_spawn")).toList(), 6.0);
        Set<String> appearances = DungeonInstanceRegistry.combatAppearances(Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight",
                "demon_grunt", "demon_imp", "oculus", "silver_mimic",
                "bottle_mimic_short_yellow"));
        var plans = DungeonInstanceRegistry.planEncounterMobs(clusters, appearances, 919191L);
        var typeDistribution = DungeonInstanceRegistry.encounterTypeDistribution(plans);

        assertEquals(appearances, typeDistribution.keySet(), typeDistribution.toString());
        assertEquals(plans.size(), typeDistribution.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(14, typeDistribution.size(), typeDistribution.toString());
    }

    @Test
    void appearanceCoverageDoesNotChangeEncounterCountOrMarkerCoordinates() {
        var plans = new ArrayList<DungeonInstanceRegistry.MobSpawnPlan>();
        for (int index = 0; index < 30; index++) {
            plans.add(new DungeonInstanceRegistry.MobSpawnPlan(
                    index % 2 == 0 ? "skeleton_archer" : "skeleton_knight",
                    marker("splinter", -210 + index * 15, -180 + (index % 7) * 55)));
        }
        Set<String> available = Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight",
                "demon_grunt", "demon_imp", "oculus",
                "skeleton_archer_elite", "skeleton_knight_elite", "skeleton_warrior_elite",
                "zombie_knight_elite", "demon_grunt_elite", "demon_imp_elite", "oculus_elite");

        var balanced = DungeonInstanceRegistry.ensureAppearanceCoverage(plans, available, 81173L);

        assertEquals(plans.size(), balanced.size());
        assertEquals(available, balanced.stream()
                .map(DungeonInstanceRegistry.MobSpawnPlan::mobType)
                .collect(java.util.stream.Collectors.toSet()));
        for (int index = 0; index < plans.size(); index++) {
            assertEquals(plans.get(index).marker(), balanced.get(index).marker());
        }
    }

    @Test
    void fullCitySweepActivatesEveryOfficialSpawnPlanExactlyOnce() throws Exception {
        var config = new Gson().fromJson(Files.readString(Path.of("..", "configuration", "ravengard",
                "dungeon_1.json")), RavengardDungeonConfig.class);
        var clusters = DungeonInstanceRegistry.clusterMarkers(config.objects().stream()
                .filter(object -> object.category().equals("entity_spawn")).toList(), 6.0);
        Set<String> appearances = DungeonInstanceRegistry.combatAppearances(Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight",
                "demon_grunt", "demon_imp", "oculus", "silver_mimic",
                "bottle_mimic_short_yellow"));
        var plans = DungeonInstanceRegistry.planEncounterMobs(clusters, appearances, 919191L);
        Set<Integer> activated = new HashSet<>();

        for (int x = -270; x <= 270; x += 60) {
            for (int z = -270; z <= 270; z += 60) {
                var nearby = DungeonInstanceRegistry.planIndexesWithinActivationRadius(plans,
                        List.of(new net.minestom.server.coordinate.Pos(x, 65, z)), activated);
                for (int index : nearby) {
                    assertTrue(DungeonInstanceRegistry.activatePlan(activated, index, () -> true));
                }
            }
        }

        assertEquals(plans.size(), activated.size());
        assertEquals(130, activated.size());
        assertTrue(DungeonInstanceRegistry.planIndexesWithinActivationRadius(plans,
                plans.stream().map(plan -> new net.minestom.server.coordinate.Pos(
                        plan.marker().x(), plan.marker().y(), plan.marker().z())).toList(),
                activated).isEmpty());
    }

    @Test
    void everyOfficialActivationRegionContainsSeveralAppearances() throws Exception {
        var config = new Gson().fromJson(Files.readString(Path.of("..", "configuration", "ravengard",
                "dungeon_1.json")), RavengardDungeonConfig.class);
        var clusters = DungeonInstanceRegistry.clusterMarkers(config.objects().stream()
                .filter(object -> object.category().equals("entity_spawn")).toList(), 6.0);
        Set<String> appearances = DungeonInstanceRegistry.combatAppearances(Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight",
                "demon_grunt", "demon_imp", "oculus", "silver_mimic",
                "bottle_mimic_short_yellow"));
        var plans = DungeonInstanceRegistry.planEncounterMobs(clusters, appearances, 919191L);

        Map<String, Set<String>> regionalAppearances = new java.util.HashMap<>();
        for (var plan : plans) {
            String quadrant = (plan.marker().x() < 0 ? "W" : "E")
                    + (plan.marker().z() < 0 ? "N" : "S");
            regionalAppearances.computeIfAbsent(quadrant, ignored -> new HashSet<>()).add(plan.mobType());
            if (Math.abs(plan.marker().x()) >= 200 || Math.abs(plan.marker().z()) >= 200) {
                regionalAppearances.computeIfAbsent("outer200", ignored -> new HashSet<>()).add(plan.mobType());
            }
        }

        for (String region : Set.of("WN", "WS", "EN", "ES", "outer200")) {
            assertTrue(regionalAppearances.getOrDefault(region, Set.of()).size() >= 6,
                    region + "=" + regionalAppearances.getOrDefault(region, Set.of()));
        }
    }

    @Test
    void openingEncountersPrimeDiverseOuterMobsInEveryQuadrant() throws Exception {
        var config = new Gson().fromJson(Files.readString(Path.of("..", "configuration", "ravengard",
                "dungeon_1.json")), RavengardDungeonConfig.class);
        var clusters = DungeonInstanceRegistry.clusterMarkers(config.objects().stream()
                .filter(object -> object.category().equals("entity_spawn")).toList(), 6.0);
        Set<String> appearances = DungeonInstanceRegistry.combatAppearances(Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight",
                "demon_grunt", "demon_imp", "oculus", "silver_mimic",
                "bottle_mimic_short_yellow"));
        var plans = DungeonInstanceRegistry.planEncounterMobs(clusters, appearances, 919191L);

        var opening = DungeonInstanceRegistry.openingEncounterIndexes(plans, 3);
        var openingPlans = opening.stream().map(plans::get).toList();

        assertEquals(12, opening.size());
        assertEquals(12, new HashSet<>(opening).size());
        Map<String, Set<String>> typesByRegion = new java.util.HashMap<>();
        for (var plan : openingPlans) {
            String region = (plan.marker().x() < 0 ? "W" : "E")
                    + (plan.marker().z() < 0 ? "N" : "S");
            typesByRegion.computeIfAbsent(region, ignored -> new HashSet<>()).add(plan.mobType());
            assertTrue(Math.abs(plan.marker().x()) >= 100 || Math.abs(plan.marker().z()) >= 100,
                    plan.marker().toString());
        }
        for (String region : Set.of("WN", "WS", "EN", "ES")) {
            assertEquals(3, typesByRegion.getOrDefault(region, Set.of()).size(),
                    region + "=" + typesByRegion.getOrDefault(region, Set.of()));
        }
        assertTrue(openingPlans.stream().map(DungeonInstanceRegistry.MobSpawnPlan::mobType)
                .distinct().count() >= 6);
    }

    @Test
    void openingActivationLeavesTheRestForProximityAndRetriesFailures() {
        var plans = List.of(
                new DungeonInstanceRegistry.MobSpawnPlan("skeleton_archer", marker("splinter", -220, -210)),
                new DungeonInstanceRegistry.MobSpawnPlan("skeleton_knight", marker("splinter", -190, -180)),
                new DungeonInstanceRegistry.MobSpawnPlan("zombie_knight", marker("splinter", -160, -150)),
                new DungeonInstanceRegistry.MobSpawnPlan("demon_grunt", marker("splinter", 210, 210)));
        var opening = DungeonInstanceRegistry.openingEncounterIndexes(plans, 2);
        Set<Integer> activated = new HashSet<>();

        assertEquals(3, opening.size());
        int failed = opening.getFirst();
        assertFalse(DungeonInstanceRegistry.activatePlan(activated, failed, () -> false));
        for (int index : opening.subList(1, opening.size())) {
            assertTrue(DungeonInstanceRegistry.activatePlan(activated, index, () -> true));
        }
        assertFalse(activated.contains(failed));
        assertTrue(DungeonInstanceRegistry.activatePlan(activated, failed, () -> true));
        assertTrue(activated.size() < plans.size());
        assertFalse(DungeonInstanceRegistry.plansWithinActivationRadius(plans,
                List.of(new net.minestom.server.coordinate.Pos(0, 65, 0)), activated).contains(plans.getLast()));
    }

    @Test
    void firstPlayerObservationImmediatelySelectsNearbyEncounterMobsAcrossTheLocalArea() {
        var plans = java.util.stream.IntStream.range(0, 40)
                .mapToObj(index -> new DungeonInstanceRegistry.MobSpawnPlan(
                        index % 2 == 0 ? "skeleton_knight" : "zombie_knight",
                        marker("splinter", index * 2, index % 3)))
                .toList();
        var selected = DungeonInstanceRegistry.nearestUnactivatedPlans(
                plans, new net.minestom.server.coordinate.Pos(0, 65, 0), Set.of(), 24);

        assertEquals(24, selected.size());
        assertEquals(java.util.stream.IntStream.range(0, 24).boxed().toList(), selected);
        assertTrue(selected.stream().map(plans::get)
                .map(DungeonInstanceRegistry.MobSpawnPlan::mobType)
                .collect(java.util.stream.Collectors.toSet())
                .containsAll(Set.of("skeleton_knight", "zombie_knight")));
    }

    @Test
    void officialClipDirectoryOnlyExpandsVerifiedCombatAppearances() throws Exception {
        Set<String> officialClips;
        try (var files = Files.list(Path.of("..", "configuration", "ravengard", "mobs"))) {
            officialClips = files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .collect(java.util.stream.Collectors.toSet());
        }

        assertEquals(15, officialClips.size());
        assertTrue(officialClips.containsAll(Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight",
                "demon_grunt", "demon_imp", "oculus", "silver_mimic",
                "bottle_mimic_short_yellow")));
        var appearances = DungeonInstanceRegistry.combatAppearances(officialClips);
        assertEquals(14, appearances.size());
        assertTrue(appearances.stream().noneMatch(Set.of(
                "normal_mimic", "mini_mimic", "silver_mimic", "bottle_mimic_short_yellow",
                "demon_archer", "dire_wolf", "cursed_knight", "guardian",
                "iron_core", "wraith", "warden", "wraith_boss",
                "commander", "guard_archer", "guard_spear", "guard_warrior",
                "training_dummy")::contains));
    }

    @Test
    void generatedInstanceMergeRetainsOfficialEncounterPopulation() throws Exception {
        var config = new Gson().fromJson(Files.readString(Path.of("..", "configuration", "ravengard",
                "dungeon_1.json")), RavengardDungeonConfig.class);
        var generatedObjects = config.objects().stream()
                .filter(object -> object.category().equals("entity_spawn"))
                .limit(25)
                .toList();
        var merged = DungeonInstanceRegistry.mergeEncounterMarkers(generatedObjects, config.objects());
        var encounterMarkers = merged.objects().stream()
                .filter(object -> object.category().equals("entity_spawn"))
                .toList();
        var appearances = DungeonInstanceRegistry.combatAppearances(Set.of(
                "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight",
                "demon_grunt", "demon_imp", "oculus", "silver_mimic",
                "bottle_mimic_short_yellow"));
        var plans = DungeonInstanceRegistry.planEncounterMobs(
                DungeonInstanceRegistry.clusterMarkers(encounterMarkers, 6.0), appearances, 919191L);

        assertEquals(300, merged.mergedMarkers());
        assertEquals(130, plans.size());
        assertEquals(14, DungeonInstanceRegistry.encounterTypeDistribution(plans).size());
    }

    private static RavengardDungeonConfig.DungeonObject marker(String type, double x, double z) {
        return new RavengardDungeonConfig.DungeonObject("entity_spawn", type, x, 65, z, 0, 0);
    }
}
