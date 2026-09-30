package net.swofty.type.ravengarddungeon.game;

import net.kyori.adventure.text.Component;
import net.swofty.type.ravengardgeneric.hud.RavengardHudComposer;
import net.swofty.type.ravengardgeneric.hud.RavengardHudState;

public final class DungeonTabMap {
    private DungeonTabMap() {
    }

    public static void register() {
        RavengardHudComposer.dungeonTabMap = DungeonTabMap::render;
    }

    private static Component render(RavengardHudState state) {
        // Generated room-art codepoints are not in the official pack.  Returning
        // null selects RavengardHud's captured official Crypt map fallback.
        return null;
    }
}
