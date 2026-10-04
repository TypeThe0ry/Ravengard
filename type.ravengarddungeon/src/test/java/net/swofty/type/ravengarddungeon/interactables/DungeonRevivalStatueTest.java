package net.swofty.type.ravengarddungeon.interactables;

import com.google.gson.Gson;
import net.minestom.server.coordinate.Pos;
import net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonRevivalStatueTest {
    @Test
    void theOfficialMapPlacesTenDistinctStatues() throws Exception {
        RavengardDungeonConfig config = new Gson().fromJson(Files.readString(
                Path.of("..", "configuration", "ravengard", "dungeon_1.json")), RavengardDungeonConfig.class);

        List<Pos> markers = DungeonRevivalStatue.markers(config.objects());

        assertEquals(10, markers.size());
        assertTrue(markers.contains(new Pos(-91.5, 66.5, -20.5, 0f, 0f)));
        assertTrue(markers.contains(new Pos(79.5, 66.5, 162.5, 270f, 0f)));
    }

    @Test
    void revivedMercenariesStandOnTheFloorInFrontOfTheStatue() {
        Pos facingSouth = DungeonRevivalStatue.revivePoint(new Pos(163.5, 66.5, -108.5, 0f, 0f));
        assertEquals(163.5, facingSouth.x(), 1e-9);
        assertEquals(65.0, facingSouth.y(), 1e-9);
        assertEquals(-106.7, facingSouth.z(), 1e-9);
        assertEquals(0f, facingSouth.yaw());
        Pos facingNorth = DungeonRevivalStatue.revivePoint(new Pos(-144.5, 66.5, 93.5, 180f, 0f));
        assertEquals(-144.5, facingNorth.x(), 1e-9);
        assertEquals(91.7, facingNorth.z(), 1e-9);
        Pos facingEast = DungeonRevivalStatue.revivePoint(new Pos(65.5, 66.5, -143.5, 270f, 0f));
        assertEquals(67.3, facingEast.x(), 1e-9);
        assertEquals(-143.5, facingEast.z(), 1e-9);
    }
}
