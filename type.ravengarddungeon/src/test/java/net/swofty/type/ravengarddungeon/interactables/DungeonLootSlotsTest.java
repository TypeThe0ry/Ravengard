package net.swofty.type.ravengarddungeon.interactables;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DungeonLootSlotsTest {
    private static final int[] CANDIDATES = {10, 11, 12, 13, 14, 15, 16, 20, 22, 24};

    @Test
    void everyRollLandsInItsOwnSlot() {
        Random random = new Random(42);
        for (int attempt = 0; attempt < 500; attempt++) {
            int count = 1 + random.nextInt(4);
            int[] slots = DungeonLoot.slots(random, CANDIDATES, count);
            assertEquals(count, slots.length);
            assertEquals(count, Arrays.stream(slots).distinct().count());
            for (int slot : slots) {
                assertEquals(1, Arrays.stream(CANDIDATES).filter(candidate -> candidate == slot).count());
            }
        }
    }

    @Test
    void moreRollsThanSlotsFillsEverySlotOnce() {
        int[] slots = DungeonLoot.slots(new Random(7), new int[]{29, 30, 31}, 5);
        assertEquals(3, slots.length);
        assertEquals(3, Arrays.stream(slots).distinct().count());
    }
}
