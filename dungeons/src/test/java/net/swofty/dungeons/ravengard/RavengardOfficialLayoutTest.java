package net.swofty.dungeons.ravengard;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavengardOfficialLayoutTest {
    @Test
    void generationKeepsOfficialRoomAndDisplayCoordinatesTogether() {
        RavengardRoomCatalog catalog = RavengardRoomCatalog.load(
                Path.of("..", "configuration", "ravengard", "dungeon_rooms.json"));

        for (long seed : new long[]{0L, 1L, 42L, Long.MAX_VALUE}) {
            RavengardDungeon dungeon = RavengardDungeon.generate(catalog, seed, catalog.getRooms().size());

            assertTrue(dungeon.getSwaps().isEmpty());
            assertEquals(catalog.getRooms().size() + (catalog.getCitadel() == null ? 0 : 1),
                    dungeon.getPlacements().size());
            int offset = catalog.getCitadel() == null ? 0 : 1;
            for (int index = 0; index < catalog.getRooms().size(); index++) {
                var room = catalog.getRooms().get(index);
                var placement = dungeon.getPlacements().get(index + offset);
                assertSame(room, placement.room());
                assertEquals(Rotation.NONE, placement.rotation());
                assertEquals(room.getMinX(), placement.originX());
                assertEquals(room.getMinZ(), placement.originZ());
            }
        }
    }
}
