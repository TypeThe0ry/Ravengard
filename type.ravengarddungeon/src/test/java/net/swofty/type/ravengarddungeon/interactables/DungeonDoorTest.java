package net.swofty.type.ravengarddungeon.interactables;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.entity.metadata.other.InteractionMeta;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig;
import net.swofty.dungeons.ravengard.RavengardRoomCatalog;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DungeonDoorTest {
    @BeforeAll
    static void initializeMinestom() {
        MinecraftServer.init();
    }

    @AfterAll
    static void stopMinestom() {
        MinecraftServer.stopCleanly();
    }

    @Test
    void officialDoorOrientationsResolveTheirWorldAxes() {
        assertEquals(false, DungeonDoor.isAxisAlongX(270));
        assertEquals(false, DungeonDoor.isAxisAlongX(-90));
        assertEquals(true, DungeonDoor.isAxisAlongX(180));
        assertEquals(true, DungeonDoor.isAxisAlongX(-180));
    }

    @Test
    void clickedDoorDoesNotCancelWhenCameraMovesDuringChannel() {
        InstanceContainer instance = instance();
        DungeonDoor.spawnAll(instance, List.of(
                marker("door_1", 3, 66.5, 5, 0),
                marker("door_1", 5, 66.5, 5, 180),
                marker("door_1_top", 4, 66.5, 5, 0)));
        var interaction = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.INTERACTION)
                .findFirst()
                .orElseThrow();
        var door = InteractableRegistry.byInteraction(interaction);
        assertNotNull(door);
        assertEquals(false, door.requiresLookingDuringChannel());
    }

    @Test
    void oppositeOfficialLeafYawsSwingAwayFromEachOther() {
        assertEquals(-1, DungeonDoor.swingSign(180, true, 3, 4, 2));
        assertEquals(1, DungeonDoor.swingSign(0, true, 5, 4, 2));
        assertEquals(-1, DungeonDoor.swingSign(270, false, 14, 15, 2));
        assertEquals(1, DungeonDoor.swingSign(90, false, 16, 15, 2));
    }

    @Test
    void malformedEqualYawPairStillUsesOppositeHinges() {
        assertEquals(-1, DungeonDoor.swingSign(90, true, 3, 4, 2));
        assertEquals(1, DungeonDoor.swingSign(90, true, 5, 4, 2));
        assertEquals(-1, DungeonDoor.swingSign(0, false, 14, 15, 2));
        assertEquals(1, DungeonDoor.swingSign(0, false, 16, 15, 2));
    }

    @Test
    void malformedMarkerYawsResolveFromTheDoorwayAxisAndHingeSide() {
        assertEquals(0, DungeonDoor.woodenLeafYaw(90, true, 3, 4, 2));
        assertEquals(180, DungeonDoor.woodenLeafYaw(90, true, 5, 4, 2));
        assertEquals(90, DungeonDoor.woodenLeafYaw(0, false, 14, 15, 2));
        assertEquals(270, DungeonDoor.woodenLeafYaw(0, false, 16, 15, 2));
    }

    @Test
    void officialWoodenLeafMarkersResolveToTheModelAxisAndHingeSide() {
        assertEquals(0, DungeonDoor.woodenLeafYaw(0, true, 3, 4, 2));
        assertEquals(180, DungeonDoor.woodenLeafYaw(180, true, 5, 4, 2));
        assertEquals(90, DungeonDoor.woodenLeafYaw(90, false, 14, 15, 2));
        assertEquals(270, DungeonDoor.woodenLeafYaw(-90, false, 16, 15, 2));
    }

    @Test
    void woodenDoubleDoorsRotateAroundTheirModelBakedHingesOnBothAxes() {
        Pos westClosed = new Pos(3, 66.5, 5, 180, 0);
        Pos eastClosed = new Pos(5, 66.5, 5, 0, 0);
        Pos westHinge = DungeonDoor.woodenHingePose(westClosed, true, 3, 4, 2);
        Pos eastHinge = DungeonDoor.woodenHingePose(eastClosed, true, 5, 4, 2);
        Pos westOpen = DungeonDoor.woodenLeafPoseAtFraction(
                westClosed, westHinge.x(), westHinge.z(), -1, 1.0);
        Pos eastOpen = DungeonDoor.woodenLeafPoseAtFraction(
                eastClosed, eastHinge.x(), eastHinge.z(), 1, 1.0);

        assertEquals(3.0, westHinge.x(), 0.000001);
        assertEquals(5.0, eastHinge.x(), 0.000001);
        assertEquals(0.0,
                westOpen.x() - westHinge.x(), 0.000001);
        assertEquals(0.0,
                westOpen.z() - westHinge.z(), 0.000001);
        assertEquals(0.0,
                eastOpen.x() - eastHinge.x(), 0.000001);
        assertEquals(0.0,
                eastOpen.z() - eastHinge.z(), 0.000001);
        assertEquals(90, canonicalYaw(westOpen.yaw()), 0.000001);
        assertEquals(90, canonicalYaw(eastOpen.yaw()), 0.000001);

        Pos northClosed = new Pos(8, 66.5, 13, 270, 0);
        Pos southClosed = new Pos(8, 66.5, 15, 90, 0);
        Pos northHinge = DungeonDoor.woodenHingePose(northClosed, false, 13, 14, 2);
        Pos southHinge = DungeonDoor.woodenHingePose(southClosed, false, 15, 14, 2);
        Pos northOpen = DungeonDoor.woodenLeafPoseAtFraction(
                northClosed, northHinge.x(), northHinge.z(), -1, 1.0);
        Pos southOpen = DungeonDoor.woodenLeafPoseAtFraction(
                southClosed, southHinge.x(), southHinge.z(), 1, 1.0);

        assertEquals(13.0, northHinge.z(), 0.000001);
        assertEquals(15.0, southHinge.z(), 0.000001);
        assertEquals(0.0,
                northOpen.x() - northHinge.x(), 0.000001);
        assertEquals(0.0,
                northOpen.z() - northHinge.z(), 0.000001);
        assertEquals(0.0,
                southOpen.x() - southHinge.x(), 0.000001);
        assertEquals(0.0,
                southOpen.z() - southHinge.z(), 0.000001);
        assertEquals(180, canonicalYaw(northOpen.yaw()), 0.000001);
        assertEquals(180, canonicalYaw(southOpen.yaw()), 0.000001);
    }

    @Test
    void activePackWoodAndIronPosesResolveAllCardinalYaws() {
        float[] yaws = {0, 90, 180, 270};
        for (int index = 0; index < yaws.length; index++) {
            var marker = marker("door_1", 12.5, 66.5, -4.5, yaws[index]);
            Pos leaf = DungeonDoor.woodenLeafPose(marker);
            Pos top = DungeonDoor.woodenTopPose(marker);
            Pos iron = DungeonDoor.ironGatePose(marker);

            assertEquals(12.5, leaf.x());
            assertEquals(66.5, leaf.y());
            assertEquals(-4.5, leaf.z());
            assertEquals(yaws[index], canonicalYaw(leaf.yaw()));
            assertEquals(canonicalYaw(yaws[index]), canonicalYaw(top.yaw()));
            assertEquals(yaws[index], canonicalYaw(iron.yaw()));
            assertEquals(marker.x(), iron.x());
            assertEquals(marker.z(), iron.z());
        }
    }

    @Test
    void overlappingRoomMarkersSelectLeafYawMatchingTheTopAndSide() {
        var wrongLeft = marker("door_1", 3, 66.5, 5, 90);
        var expectedLeft = marker("door_1", 3, 66.5, 5, 0);
        var expectedRight = marker("door_1", 5, 66.5, 5, 180);
        var wrongRight = marker("door_1", 5, 66.5, 5, 90);

        assertSame(expectedLeft, DungeonDoor.selectLeaf(List.of(wrongLeft, expectedLeft), 0));
        assertSame(expectedRight, DungeonDoor.selectLeaf(List.of(expectedRight, wrongRight), 180));
    }

    @Test
    void oppositeIronGateMarkersFormOneSynchronizedDoorAtTheirMidpoint() {
        InstanceContainer instance = instance();
        var summary = DungeonDoor.spawnAll(instance, List.of(
                marker("iron_gate", -0.96, 68.75, -9.5, 0),
                marker("iron_gate", 2.96, 68.75, -9.5, 180)
        ));

        var displays = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY)
                .toList();
        var interactions = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.INTERACTION)
                .toList();

        assertEquals(2, displays.size());
        assertEquals(1, interactions.size());
        assertEquals(2, summary.ironGateDisplays());
        assertEquals(1, summary.interactions());
        displays.forEach(display -> assertNotNull(InteractableRegistry.byInteraction(display)));
        assertEquals(1.0, interactions.getFirst().getPosition().x(), 0.001);
        assertEquals(67.25, interactions.getFirst().getPosition().y(), 0.001);
        assertEquals(-9.5, interactions.getFirst().getPosition().z(), 0.001);
        InteractionMeta meta = (InteractionMeta) interactions.getFirst().getEntityMeta();
        assertEquals(5.42f, meta.getWidth(), 0.001f);
        assertNotNull(InteractableRegistry.byInteraction(interactions.getFirst()));
        var displayXs = displays.stream().map(entity -> entity.getPosition().x()).sorted().toList();
        assertEquals(-0.996, displayXs.get(0), 0.001);
        assertEquals(2.996, displayXs.get(1), 0.001);
        assertEquals(3.992, displayXs.get(1) - displayXs.get(0), 0.001);

        InteractableRegistry.removeInstance(instance);
        unregister(instance);
    }

    @Test
    void nearDuplicateIronGateSelectionIsOrderIndependentAndPrefersTheCanonicalGrid() {
        var displaced = List.of(
                marker("iron_gate", 11.5, 68.75, 2.957375, -90),
                marker("iron_gate", 11.5, 68.75, -0.96875, 90));
        var canonical = List.of(
                marker("iron_gate", 11.5, 68.75, 2.964, -90),
                marker("iron_gate", 11.5, 68.75, -0.964, 90));

        List<Double> forward = spawnedIronGateAxisCoordinates(List.of(
                displaced.get(0), displaced.get(1), canonical.get(0), canonical.get(1)), false);
        List<Double> reverse = spawnedIronGateAxisCoordinates(List.of(
                canonical.get(1), canonical.get(0), displaced.get(1), displaced.get(0)), false);

        assertEquals(List.of(-1.0, 3.0), forward);
        assertEquals(forward, reverse);
    }

    @Test
    void pairedIronGateAnchorsExpandOutwardOnBothDoorAxes() {
        Pos west = DungeonDoor.ironGateLeafPose(
                marker("iron_gate", -0.964, 68.75, -9.5, 0), true, 1.0, 2);
        Pos east = DungeonDoor.ironGateLeafPose(
                marker("iron_gate", 2.964, 68.75, -9.5, 180), true, 1.0, 2);
        Pos north = DungeonDoor.ironGateLeafPose(
                marker("iron_gate", -9.5, 68.75, -0.964, 90), false, 1.0, 2);
        Pos south = DungeonDoor.ironGateLeafPose(
                marker("iron_gate", -9.5, 68.75, 2.964, -90), false, 1.0, 2);

        assertEquals(-1.0, west.x(), 0.000001);
        assertEquals(3.0, east.x(), 0.000001);
        assertEquals(-1.0, north.z(), 0.000001);
        assertEquals(3.0, south.z(), 0.000001);
        assertEquals(1.0, (west.x() + east.x()) / 2.0, 0.000001);
        assertEquals(1.0, (north.z() + south.z()) / 2.0, 0.000001);
        assertEquals(0, canonicalYaw(west.yaw()));
        assertEquals(180, canonicalYaw(east.yaw()));
        assertEquals(90, canonicalYaw(north.yaw()));
        assertEquals(270, canonicalYaw(south.yaw()));
    }

    @Test
    void centralIronGateTransformFillsFourBlockAperturesInEveryCardinalDirection() throws Exception {
        var modelBounds = rotatedModelBounds(Path.of("..", "configuration", "resourcepacks", "ravengard",
                "assets", "hypixel_ravengard", "models", "item", "gameplay", "doors",
                "iron_gate.json"));
        double[][] directions = {
                {-0.964, -9.5, 0, 2.964, -9.5, 180},
                {2.964, 11.5, 180, -0.964, 11.5, 0},
                {-9.5, -0.964, 90, -9.5, 2.964, 270},
                {11.5, 2.964, 270, 11.5, -0.964, 90}
        };

        for (double[] direction : directions) {
            InstanceContainer instance = instance();
            DungeonDoor.spawnAll(instance, List.of(
                    marker("iron_gate", direction[0], 68.75, direction[1], (float) direction[2]),
                    marker("iron_gate", direction[3], 68.75, direction[4], (float) direction[5])));
            var displays = instance.getEntities().stream()
                    .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY)
                    .sorted(java.util.Comparator.comparingDouble(entity ->
                            Math.abs(direction[0] - direction[3]) > Math.abs(direction[1] - direction[4])
                                    ? entity.getPosition().x() : entity.getPosition().z()))
                    .toList();

            assertEquals(2, displays.size());
            double[][] bounds = new double[2][];
            for (int index = 0; index < displays.size(); index++) {
                ItemDisplayMeta meta = (ItemDisplayMeta) displays.get(index).getEntityMeta();
                assertEquals(ItemDisplayMeta.DisplayContext.NONE, meta.getDisplayContext());
                assertEquals(new Vec(2.0, 1.6, 1.6), meta.getScale());
                assertEquals(new Vec(0.03125, -1.85, 0.0), meta.getTranslation());
                assertArrayEquals(new float[]{0f, 0f, 0f, 1f}, meta.getLeftRotation());
                bounds[index] = itemDisplayBounds(displays.get(index).getPosition(), modelBounds,
                        meta.getScale(), (Vec) meta.getTranslation(), meta.getLeftRotation());
            }
            boolean axisAlongX = Math.abs(direction[0] - direction[3])
                    > Math.abs(direction[1] - direction[4]);
            int minimum = axisAlongX ? 0 : 4;
            int maximum = axisAlongX ? 1 : 5;
            int thicknessMinimum = axisAlongX ? 4 : 0;
            int thicknessMaximum = axisAlongX ? 5 : 1;
            double apertureCenter = axisAlongX
                    ? (direction[0] + direction[3]) / 2.0
                    : (direction[1] + direction[4]) / 2.0;
            assertEquals(apertureCenter - 2.0, bounds[0][minimum], 0.000001);
            assertEquals(apertureCenter, bounds[0][maximum], 0.000001);
            assertEquals(apertureCenter, bounds[1][minimum], 0.000001);
            assertEquals(apertureCenter + 2.0, bounds[1][maximum], 0.000001);
            assertEquals(0.0, bounds[0][maximum] - bounds[1][minimum], 0.000001);
            assertEquals(65.5, bounds[0][2], 0.000001);
            assertEquals(69.25, bounds[0][3], 0.000001);
            assertEquals(0.25, bounds[0][thicknessMaximum] - bounds[0][thicknessMinimum], 0.000001);

            InteractableRegistry.removeInstance(instance);
            unregister(instance);
        }
    }

    @Test
    void centralIronGateCollisionCoversTheVisibleApertureFromFloorToLintel() throws Exception {
        InstanceContainer instance = instance();
        DungeonDoor.spawnAll(instance, List.of(
                marker("iron_gate", -0.964, 68.75, -9.5, 0),
                marker("iron_gate", 2.964, 68.75, -9.5, 180)
        ));
        DungeonDoor gate = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.INTERACTION)
                .map(InteractableRegistry::byInteraction)
                .map(DungeonDoor.class::cast)
                .findFirst()
                .orElseThrow();

        Map<Vec, Boolean> barriers = barriers(gate);
        assertEquals(16, barriers.size());
        for (int x = -1; x <= 2; x++) {
            for (int y = 65; y <= 68; y++) {
                Vec point = new Vec(x, y, -10);
                assertEquals(true, barriers.get(point));
                assertEquals(Block.BARRIER, instance.getBlock(point));
            }
        }
        assertEquals(false, barriers.containsKey(new Vec(-2, 66, -10)));
        assertEquals(false, barriers.containsKey(new Vec(3, 66, -10)));
        assertEquals(false, barriers.containsKey(new Vec(0, 64, -10)));
        assertEquals(false, barriers.containsKey(new Vec(0, 69, -10)));

        InteractableRegistry.removeInstance(instance);
        unregister(instance);
    }

    @Test
    void centralIronGateLiftChangesOnlyWorldYAndKeepsBothLeavesSynchronized() {
        Pos[][] directions = {
                {
                        DungeonDoor.ironGateLeafPose(marker("iron_gate", -0.964, 68.75, -9.5, 0)),
                        DungeonDoor.ironGateLeafPose(marker("iron_gate", 2.964, 68.75, -9.5, 180))
                },
                {
                        DungeonDoor.ironGateLeafPose(marker("iron_gate", -9.5, 68.75, -0.964, 90)),
                        DungeonDoor.ironGateLeafPose(marker("iron_gate", -9.5, 68.75, 2.964, 270))
                }
        };

        for (Pos[] direction : directions) {
            Pos firstClosed = direction[0];
            Pos secondClosed = direction[1];
            for (double fraction : new double[]{0.0, 0.5, 1.0}) {
                Pos first = DungeonDoor.ironGatePoseAtFraction(firstClosed, fraction);
                Pos second = DungeonDoor.ironGatePoseAtFraction(secondClosed, fraction);
                double lift = 3.75 * fraction;

                assertEquals(firstClosed.x(), first.x(), 0.000001);
                assertEquals(secondClosed.x(), second.x(), 0.000001);
                assertEquals(firstClosed.z(), first.z(), 0.000001);
                assertEquals(secondClosed.z(), second.z(), 0.000001);
                assertEquals(firstClosed.y() + lift, first.y(), 0.000001);
                assertEquals(secondClosed.y() + lift, second.y(), 0.000001);
                assertEquals(canonicalYaw(firstClosed.yaw()), canonicalYaw(first.yaw()), 0.000001);
                assertEquals(canonicalYaw(secondClosed.yaw()), canonicalYaw(second.yaw()), 0.000001);
                assertEquals(secondClosed.x() - firstClosed.x(), second.x() - first.x(), 0.000001);
                assertEquals(secondClosed.z() - firstClosed.z(), second.z() - first.z(), 0.000001);
                assertEquals(first.y(), second.y(), 0.000001);
            }
        }
    }


    @Test
    void officialWoodenModelsMeetAtLintelAndKeepTheirHingesFixed() throws Exception {
        Path models = Path.of("..", "configuration", "resourcepacks", "ravengard",
                "assets", "hypixel_ravengard", "models", "item", "gameplay", "doors");
        var leafBounds = rotatedModelBounds(models.resolve("door_1.json"));
        var topBounds = rotatedModelBounds(models.resolve("door_1_top.json"));
        Vec scale = new Vec(1, 1, 1);
        float[] rotation = {0, 0, 0, 1};
        for (float yaw : new float[]{0, 90, 180, 270}) {
            Pos closed = DungeonDoor.woodenLeafPose(marker("door_1", 0, 66.5, 0, yaw));
            Pos hinge = DungeonDoor.woodenHingePose(closed, yaw % 180 == 0, 0, 1, 2);
            assertEquals(closed, hinge);
            for (double fraction : new double[]{0, 0.25, 0.5, 0.75, 1}) {
                Pos open = DungeonDoor.woodenLeafPoseAtFraction(
                        closed, hinge.x(), hinge.z(), 1, fraction);
                assertEquals(closed.x(), open.x(), 0.000001);
                assertEquals(closed.z(), open.z(), 0.000001);
                double[] bounds = itemDisplayBounds(open, leafBounds, scale, Vec.ZERO, rotation);
                assertEquals(65.0, bounds[2], 0.000001);
                assertEquals(67.5, bounds[3], 0.000001);
            }
            Pos top = DungeonDoor.woodenTopPose(marker("door_1_top", 0, 66.5, 0, yaw));
            double[] bounds = itemDisplayBounds(top, topBounds, scale, Vec.ZERO, rotation);
            int axis = yaw % 180 == 0 ? 0 : 4;
            double minimum = yaw < 180 ? -1.5 : -0.5;
            assertEquals(minimum, bounds[axis], 0.000001);
            assertEquals(minimum + 2, bounds[axis + 1], 0.000001);
            assertEquals(67.5, bounds[2], 0.000001);
            assertEquals(68.0, bounds[3], 0.000001);
        }
        for (boolean alongX : new boolean[]{true, false}) {
            var top = marker("door_1_top", alongX ? 3.5 : 5, 66.5,
                    alongX ? 5 : 3.5, alongX ? 180 : 270);
            double[] bounds = itemDisplayBounds(DungeonDoor.woodenTopPose(top),
                    topBounds, scale, Vec.ZERO, rotation);
            int axis = alongX ? 0 : 4;
            assertEquals(3, bounds[axis], 0.000001);
            assertEquals(5, bounds[axis + 1], 0.000001);
        }
    }

    @Test
    void standaloneLintelUsesOfficialNoneTransform() {
        InstanceContainer instance = instance();
        var summary = DungeonDoor.spawnAll(instance, List.of(
                marker("door_1_top", 3.5, 66.5, 5, 180)));
        assertEquals(1, summary.topDisplays());
        var display = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY)
                .findFirst().orElseThrow();
        ItemDisplayMeta meta = (ItemDisplayMeta) display.getEntityMeta();
        assertEquals(ItemDisplayMeta.DisplayContext.NONE, meta.getDisplayContext());
        assertEquals(new Vec(1, 1, 1), meta.getScale());
        assertEquals(Vec.ZERO, meta.getTranslation());
        assertEquals(180, canonicalYaw(display.getPosition().yaw()));
        display.remove();
        unregister(instance);
    }

    @Test
    void spawnedWoodenPairUsesTheDoorwayAxisAndOppositeHinges() {
        InstanceContainer instance = instance();
        DungeonDoor.spawnAll(instance, List.of(
                marker("door_1", 3, 66.5, 5, 90),
                marker("door_1", 5, 66.5, 5, 90),
                marker("door_1_top", 3.5, 66.5, 5, 180)
        ));

        var yaws = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY)
                .filter(entity -> "hypixel_ravengard:item/gameplay/doors/door_1".equals(
                        ((ItemDisplayMeta) entity.getEntityMeta()).getItemStack()
                                .get(DataComponents.ITEM_MODEL)))
                .map(entity -> canonicalYaw(entity.getPosition().yaw()))
                .sorted()
                .toList();
        assertEquals(List.of(0f, 180f), yaws);
        var contexts = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY)
                .map(entity -> (ItemDisplayMeta) entity.getEntityMeta())
                .filter(meta -> "hypixel_ravengard:item/gameplay/doors/door_1".equals(
                        meta.getItemStack().get(DataComponents.ITEM_MODEL)))
                .map(ItemDisplayMeta::getDisplayContext)
                .distinct()
                .toList();
        assertEquals(List.of(ItemDisplayMeta.DisplayContext.NONE), contexts);
        var woodMeta = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY)
                .map(entity -> (ItemDisplayMeta) entity.getEntityMeta())
                .filter(meta -> "hypixel_ravengard:item/gameplay/doors/door_1".equals(
                        meta.getItemStack().get(DataComponents.ITEM_MODEL)))
                .toList();
        assertEquals(2, woodMeta.size());
        woodMeta.forEach(meta -> {
            assertEquals(new Vec(1.0, 1.0, 1.0), meta.getScale());
            assertEquals(Vec.ZERO, meta.getTranslation());
        });

        InteractableRegistry.removeInstance(instance);
        unregister(instance);
    }

    @Test
    void partialOfficialDoorwaysGetTheirMissingLeafOnTheOtherSideOfTheHeader() {
        InstanceContainer instance = instance();
        var summary = DungeonDoor.spawnAll(instance, List.of(
                marker("door_1", 93, 66.5, -20.5, 0),
                marker("door_1_top", 93.5, 66.5, -20.5, 180),
                marker("door_1", -137, 66.5, -180.5, 180),
                marker("door_1_top", -138.5, 66.5, -180.5, 180)
        ));

        var leaves = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY)
                .filter(entity -> "hypixel_ravengard:item/gameplay/doors/door_1".equals(
                        ((ItemDisplayMeta) entity.getEntityMeta()).getItemStack()
                                .get(DataComponents.ITEM_MODEL)))
                .toList();
        assertEquals(4, leaves.size());
        assertEquals(2, summary.completeDoorways());
        assertEquals(2, summary.restoredLeaves());
        Map<Double, Integer> expectedYaws = Map.of(93.0, 0, 95.0, 180, -139.0, 0, -137.0, 180);
        expectedYaws.forEach((x, yaw) -> assertEquals((float) yaw, canonicalYaw(leaves.stream()
                .filter(entity -> entity.getPosition().x() == x)
                .findFirst().orElseThrow().getPosition().yaw())));

        InteractableRegistry.removeInstance(instance);
        unregister(instance);
    }

    @Test
    void ironGatePairingRejectsDifferentAxesAndDistantMarkers() {
        var first = marker("iron_gate", 0, 68.75, 0, 0);
        var pair = marker("iron_gate", 3.92, 68.75, 0, 180);
        var differentAxis = marker("iron_gate", 0, 68.75, 3.92, 180);
        var distant = marker("iron_gate", 9, 68.75, 0, 180);

        var groups = DungeonDoor.pairIronGates(List.of(first, differentAxis, distant, pair));

        assertEquals(3, groups.size());
        assertEquals(List.of(first, pair), groups.getFirst());
        assertEquals(1, groups.get(1).size());
        assertEquals(1, groups.get(2).size());
    }

    @Test
    void officialWoodAndIronMarkersSpawnModelsAndInteractionChannels() {
        InstanceContainer instance = instance();
        DungeonDoor.spawnAll(instance, java.util.List.of(
                marker("door_1", 3, 66.5, 5, 0),
                marker("door_1", 5, 66.5, 5, 180),
                marker("door_1_top", 3.5, 66.5, 5, 180),
                marker("iron_gate", 10, 66.5, 10, 90)
        ));

        var models = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY)
                .map(entity -> ((ItemDisplayMeta) entity.getEntityMeta()).getItemStack()
                        .get(DataComponents.ITEM_MODEL))
                .toList();
        assertEquals(2, models.stream().filter("hypixel_ravengard:item/gameplay/doors/door_1"::equals).count());
        assertEquals(1, models.stream().filter("hypixel_ravengard:item/gameplay/doors/door_1_top"::equals).count());
        assertEquals(1, models.stream().filter("hypixel_ravengard:item/gameplay/doors/iron_gate"::equals).count());

        var interactions = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.INTERACTION)
                .toList();
        assertEquals(2, interactions.size());
        for (var interaction : interactions) {
            assertNotNull(InteractableRegistry.byInteraction(interaction));
            InteractionMeta meta = (InteractionMeta) interaction.getEntityMeta();
            assertEquals(3.2f, meta.getHeight());
        }

        InteractableRegistry.removeInstance(instance);
        unregister(instance);
    }

    @Test
    void officialRoomCatalogExposesDoorMarkersSeparatelyFromRoomObjects() {
        RavengardRoomCatalog catalog = RavengardRoomCatalog.load(
                Path.of("..", "configuration", "ravengard", "dungeon_rooms.json"));
        long doorMarkers = catalog.getRooms().stream()
                .flatMap(room -> room.getDoors().stream())
                .filter(marker -> marker.getType().equals("door_1")
                        || marker.getType().equals("door_1_top"))
                .count();
        long decorDoorMarkers = catalog.getRooms().stream()
                .flatMap(room -> room.getObjects().stream())
                .filter(marker -> marker.getType().equals("door_1")
                        || marker.getType().equals("door_1_top"))
                .count();

        assertEquals(true, doorMarkers > 600);
        assertEquals(true, decorDoorMarkers > 0);
    }

    @Test
    void overlappingDoorwaysKeepDistinctYawMarkersAtTheSameCoordinate() {
        InstanceContainer instance = instance();
        var summary = DungeonDoor.spawnAll(instance, List.of(
                marker("door_1", 3, 66.5, 5, 0),
                marker("door_1", 5, 66.5, 5, 180),
                marker("door_1_top", 3.5, 66.5, 5, 180),
                marker("door_1", 3, 66.5, 5, 90),
                marker("door_1", 3, 66.5, 7, 270),
                marker("door_1_top", 3, 66.5, 5.5, 270)
        ));
        var leaves = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY)
                .filter(entity -> "hypixel_ravengard:item/gameplay/doors/door_1".equals(
                        ((ItemDisplayMeta) entity.getEntityMeta()).getItemStack()
                                .get(DataComponents.ITEM_MODEL)))
                .toList();

        assertEquals(4, leaves.size());
        assertEquals(4, summary.uniqueLeafMarkers());
        assertEquals(4, summary.leafDisplays());
        assertEquals(2, summary.completeDoorways());
        assertEquals(0, summary.orphanLeaves());
        assertEquals(2, leaves.stream()
                .filter(entity -> entity.getPosition().x() == 3.0
                        && entity.getPosition().z() == 5.0)
                .map(entity -> canonicalYaw(entity.getPosition().yaw()))
                .distinct()
                .count());
        InteractableRegistry.removeInstance(instance);
        unregister(instance);
    }

    @Test
    void alternateYawAtAnOccupiedLeafCoordinateDoesNotCreateASecondDoor() {
        InstanceContainer instance = instance();
        var summary = DungeonDoor.spawnAll(instance, List.of(
                marker("door_1", 3, 66.5, 5, 0),
                marker("door_1", 3, 66.5, 5, 90),
                marker("door_1", 5, 66.5, 5, 180),
                marker("door_1", 5, 66.5, 5, 90),
                marker("door_1_top", 3.5, 66.5, 5, 180)
        ));

        assertEquals(1, summary.interactions());
        assertEquals(2, summary.leafDisplays());
        assertEquals(0, summary.orphanLeaves());
        InteractableRegistry.removeInstance(instance);
        unregister(instance);
    }

    @Test
    void roomDoorMarkersWithoutCategoryStillPairWithoutOrphanLeaves() {
        InstanceContainer instance = instance();
        var left = new RavengardDungeonConfig.DungeonObject(null, "door_1", 3, 66.5, 5, 0, 0);
        var right = new RavengardDungeonConfig.DungeonObject(null, "door_1", 5, 66.5, 5, 180, 0);
        var top = new RavengardDungeonConfig.DungeonObject(null, "door_1_top", 3.5, 66.5, 5, 180, 0);

        var summary = DungeonDoor.spawnAll(instance, List.of(left, right, top));

        assertEquals(1, summary.completeDoorways());
        assertEquals(0, summary.restoredLeaves());
        assertEquals(0, summary.orphanLeaves());
        assertEquals(2, summary.leafDisplays());
        assertEquals(1, summary.topDisplays());

        InteractableRegistry.removeInstance(instance);
        unregister(instance);
    }

    @Test
    void generatedLeavesWithoutTopSynthesizeTheMissingDoorHeader() {
        InstanceContainer instance = instance();
        var left = new RavengardDungeonConfig.DungeonObject(null, "door_1", -64, 66.5, 222.5, 90, 0);
        var right = new RavengardDungeonConfig.DungeonObject(null, "door_1", -62, 66.5, 222.5, 90, 0);

        var summary = DungeonDoor.spawnAll(instance, List.of(left, right));

        assertEquals(1, summary.completeDoorways());
        assertEquals(0, summary.restoredLeaves());
        assertEquals(0, summary.orphanLeaves());
        assertEquals(2, summary.leafDisplays());
        assertEquals(1, summary.topDisplays());

        InteractableRegistry.removeInstance(instance);
        unregister(instance);
    }

    @Test
    void officialDungeonSpawnsEveryDistinctDoorMarkerAndOwnedBarrier() throws Exception {
        RavengardDungeonConfig config = new Gson().fromJson(Files.readString(
                Path.of("..", "configuration", "ravengard", "dungeon_1.json")),
                RavengardDungeonConfig.class);
        InstanceContainer instance = instance();

        var summary = DungeonDoor.spawnAll(instance, config.objects());

        assertEquals(1399, summary.inputMarkers());
        assertEquals(821, summary.uniqueLeafMarkers());
        assertEquals(380, summary.uniqueTopMarkers());
        assertEquals(380, summary.completeDoorways());
        assertEquals(2, summary.restoredLeaves());
        assertEquals(2, summary.orphanLeaves());
        assertEquals(762, summary.leafDisplays());
        assertEquals(380, summary.topDisplays());
        assertEquals(8, summary.ironGateDisplays());
        assertEquals(1150, summary.displays());
        assertEquals(386, summary.interactions());
        assertEquals(6144, summary.barrierReferences());
        assertEquals(true, summary.uniqueBarrierCells() > 0);
        assertEquals(summary.uniqueBarrierCells(), DungeonDoor.barrierCellCount(instance));

        InteractableRegistry.removeInstance(instance);
        assertEquals(0, DungeonDoor.barrierCellCount(instance));
        unregister(instance);
    }

    @Test
    void singleLeafDoorwaysInTheOfficialMapBecomeFullDoorsThatOpen() throws Exception {
        RavengardDungeonConfig config = new Gson().fromJson(Files.readString(
                Path.of("..", "configuration", "ravengard", "dungeon_1.json")),
                RavengardDungeonConfig.class);
        var manager = MinecraftServer.getInstanceManager();
        InstanceContainer container = manager.createInstanceContainer();
        var instance = net.swofty.type.generic.world.HypixelWorldLoader.loadFrom(
                Path.of("..", "configuration", "ravengard", "dungeon_1.polar"), container, manager);
        try {
            singleLeafDoorwaysBecomeFullDoorsThatOpen(instance, config);
        } finally {
            InteractableRegistry.removeInstance(instance);
            manager.unregisterInstance(instance);
            manager.unregisterInstance(container);
        }
    }

    private static void singleLeafDoorwaysBecomeFullDoorsThatOpen(
            net.minestom.server.instance.Instance instance, RavengardDungeonConfig config) throws Exception {
        var summary = DungeonDoor.spawnAll(instance, config.objects());

        assertEquals(382, summary.completeDoorways());
        assertEquals(4, summary.restoredLeaves());
        assertEquals(0, summary.orphanLeaves());
        assertEquals(764, summary.leafDisplays());
        assertEquals(382, summary.topDisplays());
        for (Vec aperture : List.of(new Vec(62, 66, -124), new Vec(233, 66, 105),
                new Vec(94, 66, -21), new Vec(-139, 66, -181))) {
            assertEquals(true, DungeonDoor.isBarrierManaged(instance, aperture), aperture.toString());
            assertEquals(Block.BARRIER, instance.getBlock(aperture), aperture.toString());
        }

        DungeonDoor door = (DungeonDoor) InteractableRegistry.byInteraction(instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.INTERACTION)
                .filter(entity -> entity.getPosition().distance(new Pos(62.5, 65, -124)) < 0.1)
                .findFirst().orElseThrow());
        assertEquals(true, barriers(door).containsKey(new Vec(62, 66, -124)));
        assertEquals(true, barriers(door).containsKey(new Vec(62, 66, -125)));
        invokeToggle(door, true);
        assertEquals(Block.AIR, instance.getBlock(62, 66, -124));
        assertEquals(Block.AIR, instance.getBlock(62, 66, -125));
    }

    @Test
    void sharedBarrierStaysClosedUntilEveryDoorReleasesIt() {
        InstanceContainer instance = instance();
        Vec point = new Vec(2, 65, 2);

        assertEquals(true, DungeonDoor.retainBarrier(instance, point));
        assertEquals(true, DungeonDoor.retainBarrier(instance, point));
        assertEquals(Block.BARRIER, instance.getBlock(point));

        DungeonDoor.releaseBarrier(instance, point);
        assertEquals(Block.BARRIER, instance.getBlock(point));

        DungeonDoor.releaseBarrier(instance, point);
        assertEquals(Block.AIR, instance.getBlock(point));
        unregister(instance);
    }

    @Test
    void openingOneOverlappingDoorReleasesTheWholePhysicalAperture() throws Exception {
        InstanceContainer instance = instance();
        DungeonDoor.spawnAll(instance, List.of(
                marker("door_1", 5, 66.5, 3, 270),
                marker("door_1", 5, 66.5, 5, 90),
                marker("door_1_top", 5, 66.5, 3.5, 90),
                marker("door_1", 5, 66.5, 4, 270),
                marker("door_1", 5, 66.5, 6, 90),
                marker("door_1_top", 5, 66.5, 4.5, 90)
        ));
        List<DungeonDoor> doors = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.INTERACTION)
                .map(InteractableRegistry::byInteraction)
                .map(DungeonDoor.class::cast)
                .toList();
        assertEquals(2, doors.size());

        Set<Vec> aperture = new HashSet<>();
        for (DungeonDoor door : doors) aperture.addAll(barriers(door).keySet());
        assertEquals(true, aperture.stream().allMatch(point -> instance.getBlock(point) == Block.BARRIER));

        doors.getFirst().open(null);

        assertEquals(true, aperture.stream().allMatch(point -> instance.getBlock(point) == Block.AIR));
        assertEquals(true, doors.stream().allMatch(DungeonDoor::isOpened));

        InteractableRegistry.removeInstance(instance);
        unregister(instance);
    }

    @Test
    void openingOverlappingDoorWhileNeighbourIsAnimatingStillReleasesAllBarriers() throws Exception {
        InstanceContainer instance = instance();
        DungeonDoor.spawnAll(instance, List.of(
                marker("door_1", 5, 66.5, 3, 270),
                marker("door_1", 5, 66.5, 5, 90),
                marker("door_1_top", 5, 66.5, 3.5, 90),
                marker("door_1", 5, 66.5, 4, 270),
                marker("door_1", 5, 66.5, 6, 90),
                marker("door_1_top", 5, 66.5, 4.5, 90)
        ));
        List<DungeonDoor> doors = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.INTERACTION)
                .map(InteractableRegistry::byInteraction)
                .map(DungeonDoor.class::cast)
                .toList();
        assertEquals(2, doors.size());

        var swinging = DungeonDoor.class.getDeclaredField("swinging");
        swinging.setAccessible(true);
        swinging.setBoolean(doors.get(1), true);
        doors.getFirst().open(null);

        assertEquals(true, doors.stream().allMatch(DungeonDoor::isOpened));
        assertEquals(true, doors.stream().flatMap(door -> {
            try {
                return barriers(door).entrySet().stream();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }).allMatch(entry -> !entry.getValue()));

        InteractableRegistry.removeInstance(instance);
        unregister(instance);
    }

    @Test
    void doorOpenCloseOpenCycleReleasesAndReacquiresItsOwnBarriers() throws Exception {
        InstanceContainer instance = instance();
        DungeonDoor.spawnAll(instance, List.of(
                marker("door_1", 3, 66.5, 5, 0),
                marker("door_1", 5, 66.5, 5, 180),
                marker("door_1_top", 3.5, 66.5, 5, 180)
        ));
        DungeonDoor door = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.INTERACTION)
                .map(InteractableRegistry::byInteraction)
                .map(DungeonDoor.class::cast)
                .findFirst()
                .orElseThrow();
        Map<Vec, Boolean> barriers = barriers(door);
        assertEquals(false, barriers.isEmpty());
        assertEquals(3.6, door.getInteraction().getBoundingBox().width(), 0.001);
        assertEquals(false, door.getInteraction().hasEntityCollision());
        assertEquals(3.2, door.getInteraction().getBoundingBox().height(), 0.001);
        assertEquals(true, barriers.values().stream().allMatch(Boolean::booleanValue));
        assertEquals(true, barriers.keySet().stream().allMatch(point -> instance.getBlock(point) == Block.BARRIER));

        invokeToggle(door, true);
        assertEquals(true, barriers.values().stream().noneMatch(Boolean::booleanValue));
        assertEquals(true, barriers.keySet().stream().allMatch(point -> instance.getBlock(point) == Block.AIR));

        invokeToggle(door, false);
        assertEquals(true, barriers.values().stream().allMatch(Boolean::booleanValue));
        assertEquals(true, barriers.keySet().stream().allMatch(point -> instance.getBlock(point) == Block.BARRIER));

        invokeToggle(door, true);
        assertEquals(true, barriers.values().stream().noneMatch(Boolean::booleanValue));
        assertEquals(true, barriers.keySet().stream().allMatch(point -> instance.getBlock(point) == Block.AIR));

        InteractableRegistry.removeInstance(instance);
        unregister(instance);
    }

    @Test
    void openingClearsMapBarrierAndPreservesLaterWorldChanges() {
        InstanceContainer instance = instance();
        Vec originalBarrier = new Vec(3, 65, 3);
        Vec replacedCell = new Vec(4, 65, 4);
        instance.setBlock(originalBarrier, Block.BARRIER);

        assertEquals(true, DungeonDoor.retainBarrier(instance, originalBarrier));
        DungeonDoor.releaseBarrier(instance, originalBarrier);
        assertEquals(Block.AIR, instance.getBlock(originalBarrier));

        assertEquals(true, DungeonDoor.retainBarrier(instance, replacedCell));
        instance.setBlock(replacedCell, Block.GOLD_BLOCK);
        DungeonDoor.releaseBarrier(instance, replacedCell);
        assertEquals(Block.GOLD_BLOCK, instance.getBlock(replacedCell));
        unregister(instance);
    }

    @Test
    void barrierKeysDoNotAliasSignedDungeonCoordinates() {
        assertNotEquals(DungeonDoor.barrierKey(new Vec(-1, 65, 0)),
                DungeonDoor.barrierKey(new Vec(0, 65, 0)));
        assertNotEquals(DungeonDoor.barrierKey(new Vec(0, -1, 0)),
                DungeonDoor.barrierKey(new Vec(0, 65, 0)));
        assertNotEquals(DungeonDoor.barrierKey(new Vec(0, 65, -1)),
                DungeonDoor.barrierKey(new Vec(0, 65, 0)));
    }


    private static List<Double> spawnedIronGateAxisCoordinates(
            List<RavengardDungeonConfig.DungeonObject> markers, boolean axisAlongX) {
        InstanceContainer instance = instance();
        DungeonDoor.spawnAll(instance, markers);
        List<Double> coordinates = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY)
                .map(entity -> axisAlongX ? entity.getPosition().x() : entity.getPosition().z())
                .sorted()
                .toList();
        InteractableRegistry.removeInstance(instance);
        unregister(instance);
        return coordinates;
    }

    private static InstanceContainer instance() {
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(64, 66, Block.AIR));
        instance.loadChunk(0, 0).join();
        return instance;
    }

    private static void unregister(InstanceContainer instance) {
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    private record ModelBounds(double minX, double maxX, double minY, double maxY,
                               double minZ, double maxZ) {
    }

    private static ModelBounds rotatedModelBounds(Path path) throws Exception {
        var model = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (var element : model.getAsJsonArray("elements")) {
            var object = element.getAsJsonObject();
            var from = object.getAsJsonArray("from");
            var to = object.getAsJsonArray("to");
            double angle = object.has("rotation")
                    ? Math.toRadians(object.getAsJsonObject("rotation").get("angle").getAsDouble()) : 0.0;
            String axis = object.has("rotation")
                    ? object.getAsJsonObject("rotation").get("axis").getAsString() : "";
            double[] origin = {0, 0, 0};
            if (object.has("rotation")) {
                var jsonOrigin = object.getAsJsonObject("rotation").getAsJsonArray("origin");
                for (int index = 0; index < 3; index++) origin[index] = jsonOrigin.get(index).getAsDouble();
            }
            for (double x : new double[]{from.get(0).getAsDouble(), to.get(0).getAsDouble()}) {
                for (double y : new double[]{from.get(1).getAsDouble(), to.get(1).getAsDouble()}) {
                    for (double z : new double[]{from.get(2).getAsDouble(), to.get(2).getAsDouble()}) {
                        double[] point = rotateElement(x, y, z, origin, axis, angle);
                        minX = Math.min(minX, point[0]); maxX = Math.max(maxX, point[0]);
                        minY = Math.min(minY, point[1]); maxY = Math.max(maxY, point[1]);
                        minZ = Math.min(minZ, point[2]); maxZ = Math.max(maxZ, point[2]);
                    }
                }
            }
        }
        return new ModelBounds(minX / 16.0, maxX / 16.0, minY / 16.0, maxY / 16.0,
                minZ / 16.0, maxZ / 16.0);
    }

    private static double[] rotateElement(double x, double y, double z, double[] origin,
                                          String axis, double angle) {
        double px = x - origin[0], py = y - origin[1], pz = z - origin[2];
        double cosine = Math.cos(angle), sine = Math.sin(angle);
        return switch (axis) {
            case "x" -> new double[]{x, py * cosine - pz * sine + origin[1],
                    py * sine + pz * cosine + origin[2]};
            case "y" -> new double[]{px * cosine + pz * sine + origin[0], y,
                    -px * sine + pz * cosine + origin[2]};
            case "z" -> new double[]{px * cosine - py * sine + origin[0],
                    px * sine + py * cosine + origin[1], z};
            default -> new double[]{x, y, z};
        };
    }

    private static double[] itemDisplayBounds(Pos marker, ModelBounds bounds, Vec scale,
                                              Vec translation, float[] leftRotation) {
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        double radians = Math.toRadians(marker.yaw());
        double cosine = Math.cos(radians), sine = Math.sin(radians);
        for (double x : new double[]{bounds.minX(), bounds.maxX()}) {
            for (double y : new double[]{bounds.minY(), bounds.maxY()}) {
                for (double z : new double[]{bounds.minZ(), bounds.maxZ()}) {
                    double[] rotated = rotateQuaternion(
                            scale.x() * (x - 0.5),
                            scale.y() * (y - 0.5),
                            scale.z() * (z - 0.5), leftRotation);
                    // Minecraft's ItemDisplayRenderer applies a fixed Y+180 rotation
                    // to item displays before rendering the item model.
                    rotated[0] = -rotated[0];
                    rotated[2] = -rotated[2];
                    double localX = translation.x() + rotated[0];
                    double localY = translation.y() + rotated[1];
                    double localZ = translation.z() + rotated[2];
                    double worldX = marker.x() + localX * cosine - localZ * sine;
                    double worldZ = marker.z() + localX * sine + localZ * cosine;
                    double worldY = marker.y() + localY;
                    min[0] = Math.min(min[0], worldX); max[0] = Math.max(max[0], worldX);
                    min[1] = Math.min(min[1], worldY); max[1] = Math.max(max[1], worldY);
                    min[2] = Math.min(min[2], worldZ); max[2] = Math.max(max[2], worldZ);
                }
            }
        }
        return new double[]{min[0], max[0], min[1], max[1], min[2], max[2]};
    }

    private static double[] rotateQuaternion(double x, double y, double z, float[] rotation) {
        double qx = rotation[0], qy = rotation[1], qz = rotation[2], qw = rotation[3];
        double ix = qw * x + qy * z - qz * y;
        double iy = qw * y + qz * x - qx * z;
        double iz = qw * z + qx * y - qy * x;
        double iw = -qx * x - qy * y - qz * z;
        return new double[]{
                ix * qw + iw * -qx + iy * -qz - iz * -qy,
                iy * qw + iw * -qy + iz * -qx - ix * -qz,
                iz * qw + iw * -qz + ix * -qy - iy * -qx
        };
    }

    private static RavengardDungeonConfig.DungeonObject marker(
            String type, double x, double y, double z, float yaw) {
        return new RavengardDungeonConfig.DungeonObject("door", type, x, y, z, yaw, 0);
    }

    private static float canonicalYaw(float yaw) {
        return ((yaw % 360f) + 360f) % 360f;
    }

    @SuppressWarnings("unchecked")
    private static Map<Vec, Boolean> barriers(DungeonDoor door) throws Exception {
        Field field = DungeonDoor.class.getDeclaredField("closedBarriers");
        field.setAccessible(true);
        return (Map<Vec, Boolean>) field.get(door);
    }

    private static void invokeToggle(DungeonDoor door, boolean open) throws Exception {
        var method = DungeonDoor.class.getDeclaredMethod("toggleBarriers", boolean.class);
        method.setAccessible(true);
        method.invoke(door, open);
    }
}
