package net.swofty.type.ravengarddungeon.game;

import com.google.gson.Gson;
import net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DungeonDoorMarkerMergeTest {
    @Test
    void officialDungeonMergesAllDoorMarkerTypesByTypeCoordinateAndYaw() throws IOException {
        RavengardDungeonConfig config = new Gson().fromJson(Files.readString(
                Path.of("..", "configuration", "ravengard", "dungeon_1.json")),
                RavengardDungeonConfig.class);

        var merge = DungeonInstanceRegistry.mergeDoorMarkers(List.of(), config.objects());

        assertEquals(1399, merge.officialDoorMarkers());
        assertEquals(0, merge.generatedDoorMarkers());
        assertEquals(188, merge.duplicateDoorMarkers());
        assertEquals(1211, merge.mergedDoorMarkers());
        assertEquals(821, count(merge.objects(), "door_1"));
        assertEquals(380, count(merge.objects(), "door_1_top"));
        assertEquals(10, count(merge.objects(), "iron_gate"));
    }

    @Test
    void generatedMarkersWinAndNonDoorObjectsRemainUntouched() {
        var generatedDoor = object("door", "door_1", 1.25, 66.5, -2.75, -90);
        var generatedChest = object("chest", "loot", 3, 64, 4, 0);
        var officialEquivalentYaw = object("door", "door_1", 1.25, 66.5, -2.75, 270);
        var officialDifferentYaw = object("door", "door_1", 1.25, 66.5, -2.75, 90);
        var officialTop = object("door", "door_1_top", 1.25, 66.5, -2.75, 270);

        var merge = DungeonInstanceRegistry.mergeDoorMarkers(
                List.of(generatedDoor, generatedChest),
                List.of(officialEquivalentYaw, officialDifferentYaw, officialTop));

        assertEquals(1, merge.generatedDoorMarkers());
        assertEquals(3, merge.officialDoorMarkers());
        assertEquals(1, merge.duplicateDoorMarkers());
        assertEquals(3, merge.mergedDoorMarkers());
        assertEquals(4, merge.objects().size());
        assertSame(generatedDoor, merge.objects().getFirst());
        assertSame(generatedChest, merge.objects().get(1));
    }

    @Test
    void generatedRoomDoorMarkersWithoutCategoryAreStillDeduplicated() {
        var generatedDoor = object(null, "door_1", 1.25, 66.5, -2.75, 90);
        var officialEquivalent = object("door", "door_1", 1.25, 66.5, -2.75, -270);

        var merge = DungeonInstanceRegistry.mergeDoorMarkers(
                List.of(generatedDoor), List.of(officialEquivalent));

        assertEquals(1, merge.generatedDoorMarkers());
        assertEquals(1, merge.officialDoorMarkers());
        assertEquals(1, merge.duplicateDoorMarkers());
        assertEquals(1, merge.mergedDoorMarkers());
        assertSame(generatedDoor, merge.objects().getFirst());
    }

    private static long count(List<RavengardDungeonConfig.DungeonObject> objects, String type) {
        return objects.stream().filter(object -> type.equals(object.type())).count();
    }

    private static RavengardDungeonConfig.DungeonObject object(
            String category, String type, double x, double y, double z, float yaw) {
        return new RavengardDungeonConfig.DungeonObject(category, type, x, y, z, yaw, 0);
    }
}
