package net.swofty.type.ravengarddungeon.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.coordinate.Pos;
import net.swofty.type.ravengardgeneric.gui.RavengardFont;
import net.swofty.type.ravengardgeneric.hud.RavengardHudComposer;
import net.swofty.type.ravengardgeneric.hud.RavengardHudState;

import java.util.List;
import java.util.UUID;

/**
 * Marks the run's extraction portals with the pack's own portal icon. On the minimap the packed
 * colour is the icon's offset from the player in half blocks around 256; on the tab map it is
 * the world position on the 512px canvas centred at the origin.
 */
public final class DungeonMinimapIcons {
    private static final String PORTAL = "";
    private static final double MINIMAP_REACH = 127.0;
    private static final double TAB_MAP_ORIGIN = 256.0;

    private DungeonMinimapIcons() {
    }

    public static void register() {
        RavengardHudComposer.dungeonMinimapIcons = DungeonMinimapIcons::minimap;
        RavengardHudComposer.dungeonTabMapIcons = DungeonMinimapIcons::tabMap;
    }

    static Component minimap(RavengardHudState state) {
        return minimap(portals(state.getPlayerUuid()), state.getWorldX(), state.getWorldZ());
    }

    static Component minimap(List<Pos> portals, double playerX, double playerZ) {
        Component icons = Component.empty();
        for (Pos portal : portals) {
            double dx = portal.x() - playerX;
            double dz = portal.z() - playerZ;
            if (Math.abs(dx) > MINIMAP_REACH || Math.abs(dz) > MINIMAP_REACH) continue;
            icons = icons.append(icon((dx + 128.0) * 2.0, (dz + 128.0) * 2.0));
        }
        return icons;
    }

    static Component tabMap(RavengardHudState state) {
        return tabMap(portals(state.getPlayerUuid()));
    }

    static Component tabMap(List<Pos> portals) {
        Component icons = Component.empty();
        for (Pos portal : portals) {
            icons = icons.append(icon(portal.x() + TAB_MAP_ORIGIN, portal.z() + TAB_MAP_ORIGIN));
        }
        return icons;
    }

    static int packed(double x, double z) {
        return (clamp9((int) Math.round(x)) << 15) | (clamp9((int) Math.round(z)) << 6);
    }

    private static Component icon(double x, double z) {
        return Component.text(PORTAL)
                .font(RavengardFont.RAVENGARD)
                .color(TextColor.color(packed(x, z)))
                .shadowColor(ShadowColor.shadowColor(0));
    }

    private static List<Pos> portals(UUID player) {
        if (player == null) return List.of();
        for (DungeonInstanceRegistry.DungeonInstance candidate : DungeonInstanceRegistry.all()) {
            if (candidate.getPlayers().contains(player)) {
                return candidate.getGame().getOpenExtractionPoints();
            }
        }
        return List.of();
    }

    private static int clamp9(int value) {
        return Math.clamp(value, 0, 0x1FF);
    }
}
