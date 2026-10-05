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
    private static final String TEAMMATE = "";
    private static final double MINIMAP_REACH = 127.0;
    private static final double TAB_MAP_ORIGIN = 256.0;

    private DungeonMinimapIcons() {
    }

    public static void register() {
        RavengardHudComposer.dungeonMinimapIcons = DungeonMinimapIcons::minimap;
        RavengardHudComposer.dungeonTabMapIcons = DungeonMinimapIcons::tabMap;
    }

    static Component minimap(RavengardHudState state) {
        RavengardDungeonGame game = gameOf(state.getPlayerUuid());
        if (game == null) return Component.empty();
        return minimap(game.getOpenExtractionPoints(), teammates(game, state.getPlayerUuid()),
                state.getWorldX(), state.getWorldZ());
    }

    static Component minimap(List<Pos> portals, List<Pos> teammates, double playerX, double playerZ) {
        Component icons = Component.empty();
        for (Pos portal : portals) {
            Component icon = minimapIcon(PORTAL, portal, playerX, playerZ, 0);
            if (icon != null) icons = icons.append(icon);
        }
        for (Pos teammate : teammates) {
            Component icon = minimapIcon(TEAMMATE, teammate, playerX, playerZ, rotation(teammate.yaw()));
            if (icon != null) icons = icons.append(icon);
        }
        return icons;
    }

    private static Component minimapIcon(String glyph, Pos at, double playerX, double playerZ, int rotation) {
        double dx = at.x() - playerX;
        double dz = at.z() - playerZ;
        if (Math.abs(dx) > MINIMAP_REACH || Math.abs(dz) > MINIMAP_REACH) return null;
        return icon(glyph, (dx + 128.0) * 2.0, (dz + 128.0) * 2.0, rotation);
    }

    static Component tabMap(RavengardHudState state) {
        RavengardDungeonGame game = gameOf(state.getPlayerUuid());
        if (game == null) return Component.empty();
        return tabMap(game.getOpenExtractionPoints(), teammates(game, state.getPlayerUuid()));
    }

    static Component tabMap(List<Pos> portals, List<Pos> teammates) {
        Component icons = Component.empty();
        for (Pos portal : portals) {
            icons = icons.append(icon(PORTAL, portal.x() + TAB_MAP_ORIGIN, portal.z() + TAB_MAP_ORIGIN, 0));
        }
        for (Pos teammate : teammates) {
            icons = icons.append(icon(TEAMMATE, teammate.x() + TAB_MAP_ORIGIN, teammate.z() + TAB_MAP_ORIGIN,
                    rotation(teammate.yaw())));
        }
        return icons;
    }

    static int packed(double x, double z) {
        return (clamp9((int) Math.round(x)) << 15) | (clamp9((int) Math.round(z)) << 6);
    }

    /** The marker's facing in the low six bits, sixty-four steps a turn, as the player marker uses. */
    static int rotation(float yaw) {
        float turned = ((yaw % 360f) + 360f) % 360f;
        return Math.round(turned / (360f / 64f)) & 0x3F;
    }

    private static Component icon(String glyph, double x, double z, int rotation) {
        return Component.text(glyph)
                .font(RavengardFont.RAVENGARD)
                .color(TextColor.color(packed(x, z) | rotation))
                .shadowColor(ShadowColor.shadowColor(0));
    }

    private static RavengardDungeonGame gameOf(UUID player) {
        if (player == null) return null;
        for (DungeonInstanceRegistry.DungeonInstance candidate : DungeonInstanceRegistry.all()) {
            if (candidate.getPlayers().contains(player)) return candidate.getGame();
        }
        return null;
    }

    /** Living teammates other than the player, where they stand and face. */
    static List<Pos> teammates(RavengardDungeonGame game, UUID player) {
        var self = game.getPlayers().stream().filter(candidate -> candidate.getUuid().equals(player)).findFirst().orElse(null);
        if (self == null) return List.of();
        return game.getPlayers().stream()
                .filter(other -> !other.getUuid().equals(player) && !other.isDungeonDead()
                        && other.getDungeonTeam() == self.getDungeonTeam())
                .map(other -> other.getPosition())
                .toList();
    }

    private static int clamp9(int value) {
        return Math.clamp(value, 0, 0x1FF);
    }
}
