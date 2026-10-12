package net.swofty.type.ravengardgeneric.shop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavengardShopStockTest {
    private static final List<RavengardShop.PoolEntry> POOL = List.of(
            new RavengardShop.PoolEntry("APPLE", 6), new RavengardShop.PoolEntry("BANDAGE", 24),
            new RavengardShop.PoolEntry("HEALTH_POTION", 30));

    @Test
    void aPermanentShopKeepsItsOrderAndNeverSellsOut() {
        RavengardShop shop = new RavengardShop("test_permanent", "常驻", "alchemist",
                List.of(4, 5, 6), POOL, true, 0, 0);
        var shelf = RavengardShopStock.shelf(shop);

        assertEquals(List.of("APPLE", "BANDAGE", "HEALTH_POTION"),
                shelf.entries().stream().map(RavengardShopStock.Entry::item).toList());
        for (int purchase = 0; purchase < 500; purchase++) {
            assertTrue(shelf.at(4).take());
        }
        assertTrue(shelf.at(4).inStock());
        assertEquals(0L, RavengardShopStock.cycle(shop));
    }

    @Test
    void aShopCanRefreshFasterAndStockMoreOfEachEntry() {
        RavengardShop shop = new RavengardShop("test_fast", "快速", "alchemist",
                List.of(4, 5, 6), POOL, false, 10, 3);

        assertEquals(10 * 60_000L, RavengardShopStock.cycleMillis(shop));
        var shelf = RavengardShopStock.shelf(shop);
        for (int purchase = 0; purchase < 3; purchase++) assertTrue(shelf.at(4).take());
        assertEquals(false, shelf.at(4).take());
    }

    @Test
    void otherShopsKeepTheDefaultThirtyMinutes() {
        RavengardShop shop = new RavengardShop("test_default", "默认", "alchemist", List.of(4), POOL);
        assertEquals(RavengardShopStock.CYCLE_MILLIS, RavengardShopStock.cycleMillis(shop));
    }
}
