package net.swofty.type.ravengardgeneric.shop;

import java.util.List;

/**
 * A shop's shelf. A permanent one never rotates and never sells out; the others roll a fresh shelf every
 * {@code refreshMinutes} (the default cycle when 0) with {@code stockPerEntry} of each entry on it.
 */
public record RavengardShop(String id, String title, String banner,
                            List<Integer> shelfSlots, List<PoolEntry> pool,
                            boolean permanent, int refreshMinutes, int stockPerEntry) {
    public RavengardShop(String id, String title, String banner, List<Integer> shelfSlots, List<PoolEntry> pool) {
        this(id, title, banner, shelfSlots, pool, false, 0, 0);
    }

    public record PoolEntry(String item, int price) {
    }
}
