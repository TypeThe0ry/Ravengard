package net.swofty.type.ravengarddungeon.generator;

import net.swofty.dungeons.ravengard.RavengardDungeon.RoomPlacement;
import net.swofty.dungeons.ravengard.RavengardRoomCatalog;
import net.swofty.dungeons.ravengard.Rotation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RavengardObjectTransformTest {
    @Test
    void capturedEncounterMarkerFollowsEveryRoomRotationWithoutCollapsingToRoomCentre() {
        var catalog = RavengardRoomCatalog.load(Path.of("..", "configuration", "ravengard",
                "dungeon_rooms.json"));
        var room = catalog.getRooms().stream()
                .filter(candidate -> candidate.getId().equals("room_8"))
                .findFirst().orElseThrow();
        var marker = room.getObjects().stream()
                .filter(candidate -> candidate.getCategory().equals("entity_spawn"))
                .findFirst().orElseThrow();
        double x = marker.getX();
        double z = marker.getZ();

        assertPlaced(new RoomPlacement(room, Rotation.NONE, 100, -50), marker,
                100 + x, -50 + z, marker.getYaw());
        assertPlaced(new RoomPlacement(room, Rotation.CLOCKWISE_90, 100, -50), marker,
                100 + room.getDepth() - 1 - z, -50 + x, marker.getYaw() + 90);
        assertPlaced(new RoomPlacement(room, Rotation.CLOCKWISE_180, 100, -50), marker,
                100 + room.getWidth() - 1 - x, -50 + room.getDepth() - 1 - z,
                marker.getYaw() + 180);
        assertPlaced(new RoomPlacement(room, Rotation.CLOCKWISE_270, 100, -50), marker,
                100 + z, -50 + room.getWidth() - 1 - x, marker.getYaw() + 270);
    }

    private static void assertPlaced(RoomPlacement placement,
                                     net.swofty.dungeons.ravengard.RavengardRoomCatalog.RoomObject marker,
                                     double expectedX, double expectedZ, double expectedYaw) {
        var placed = RavengardDungeonGenerator.placeObject(placement, marker);
        assertEquals(expectedX, placed.x(), 1.0e-9);
        assertEquals(expectedZ, placed.z(), 1.0e-9);
        assertEquals(expectedYaw % 360.0, placed.yaw(), 1.0e-9);
        assertEquals(marker.getY(), placed.y(), 1.0e-9);
    }
}
