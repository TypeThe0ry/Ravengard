package net.swofty.type.ravengarddungeon.interactables;

import org.junit.jupiter.api.Test;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.InstanceContainer;
import net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig;
import net.swofty.type.ravengarddungeon.game.RavengardDungeonGame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonExtractionModelTest {
    @org.junit.jupiter.api.BeforeAll
    static void initializeMinestom() {
        MinecraftServer.init();
    }

    @org.junit.jupiter.api.AfterAll
    static void stopMinestom() {
        MinecraftServer.stopCleanly();
    }

    @Test
    void officialExitPortalModelUsesTheWorldDisplayTransform() {
        assertEquals("hypixel_ravengard:item/gameplay/portals/exit_portal", DungeonExtraction.MODEL);
        assertEquals(1.228264169, DungeonExtraction.MODEL_FLOOR_OFFSET, 1.0e-9);
        assertTrue(DungeonExtraction.MODEL_FLOOR_OFFSET > 0.0);
        assertEquals(0.040813045, DungeonExtraction.MODEL_X_OFFSET, 1.0e-9);
        assertEquals(0.018987701, DungeonExtraction.MODEL_Z_OFFSET, 1.0e-9);
        assertEquals(2.0f, DungeonExtraction.INTERACTION_WIDTH);
        assertEquals(2.8f, DungeonExtraction.INTERACTION_HEIGHT);
        assertEquals(3.0, DungeonExtraction.CHANNEL_VERTICAL_DISTANCE, 1.0e-9);
    }

    @Test
    void aPortalThatCanBeClickedCanAlsoBeChannelledFromThere() {
        assertTrue(DungeonExtraction.CHANNEL_HORIZONTAL_DISTANCE
                >= InteractableRegistry.AIM_REACH + DungeonExtraction.INTERACTION_WIDTH / 2.0);
    }

    @Test
    void modelOffsetRotatesWithCyanRoomOrientation() {
        double[] offset = DungeonExtraction.rotatedModelOffset(90.0f);
        assertEquals(-DungeonExtraction.MODEL_Z_OFFSET, offset[0], 1.0e-9);
        assertEquals(DungeonExtraction.MODEL_X_OFFSET, offset[1], 1.0e-9);
    }

    @Test
    void modelPosePreservesAnchorOrientation() {
        Pos pose = DungeonExtraction.modelPose(new Pos(10.0, 65.0, -4.0, 90.0f, 17.0f));
        assertEquals(90.0f, pose.yaw());
        assertEquals(0.0f, pose.pitch());
        assertEquals(10.0 - DungeonExtraction.MODEL_Z_OFFSET, pose.x(), 1.0e-9);
        assertEquals(-4.0 + DungeonExtraction.MODEL_X_OFFSET, pose.z(), 1.0e-9);
        assertEquals(65.0 + DungeonExtraction.MODEL_FLOOR_OFFSET, pose.y(), 1.0e-9);
    }

    @Test
    void modelPoseUsesEveryCardinalYaw() {
        for (float yaw : new float[]{0.0f, 90.0f, 180.0f, 270.0f}) {
            Pos anchor = new Pos(10.0, 65.0, -4.0, yaw, 17.0f);
            Pos pose = DungeonExtraction.modelPose(anchor);
            double[] offset = DungeonExtraction.rotatedModelOffset(yaw);
            assertEquals(normalizeYaw(yaw), normalizeYaw(pose.yaw()));
            assertEquals(0.0f, pose.pitch());
            assertEquals(anchor.x() + offset[0], pose.x(), 1.0e-9);
            assertEquals(anchor.z() + offset[1], pose.z(), 1.0e-9);
            assertEquals(anchor.y() + DungeonExtraction.MODEL_FLOOR_OFFSET, pose.y(), 1.0e-9);
        }
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0f;
        return normalized < 0.0f ? normalized + 360.0f : normalized;
    }

    @Test
    void visibleExitPortalDisplayResolvesToItsExtractionInteractable() {
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        var config = new RavengardDungeonConfig("extraction-display-test", "Extraction display test",
                "unused.polar", null, java.util.List.of());
        var game = new RavengardDungeonGame(instance, config);
        DungeonExtraction extraction = DungeonExtraction.spawn(instance, game,
                new Pos(2.5, 65, -3.5, 90, 0));
        var display = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY)
                .findFirst().orElseThrow();
        assertTrue(extraction.matchesVisibleTarget(display));
        org.junit.jupiter.api.Assertions.assertSame(extraction, InteractableRegistry.byInteraction(display));
        extraction.remove();
        game.dispose();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }
}
