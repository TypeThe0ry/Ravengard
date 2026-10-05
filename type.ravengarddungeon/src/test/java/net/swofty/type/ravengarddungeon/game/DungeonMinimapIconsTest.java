package net.swofty.type.ravengarddungeon.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.minestom.server.coordinate.Pos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DungeonMinimapIconsTest {
    @Test
    void minimapPortalsArePackedAsHalfBlockOffsetsFromThePlayerAndOnlyWhenInReach() {
        Component icons = DungeonMinimapIcons.minimap(
                List.of(new Pos(110, 65, 95), new Pos(400, 65, 95)), List.of(), 100, 100);

        assertEquals(1, icons.children().size());
        TextComponent icon = (TextComponent) icons.children().getFirst();
        assertEquals("", icon.content());
        assertEquals(((10 + 128) * 2 << 15) | ((-5 + 128) * 2 << 6), icon.color().value());
    }

    @Test
    void tabMapPortalsArePackedAsWorldPositionsOnTheCanvas() {
        Component icons = DungeonMinimapIcons.tabMap(List.of(new Pos(43.5, 65, -219.5)), List.of());

        TextComponent icon = (TextComponent) icons.children().getFirst();
        assertEquals(DungeonMinimapIcons.packed(43.5 + 256, -219.5 + 256), icon.color().value());
        assertEquals((300 << 15) | (37 << 6), icon.color().value());
    }

    @Test
    void teammatesShowWithTheTeammateMarkerFacingTheirWay() {
        Component icons = DungeonMinimapIcons.minimap(List.of(),
                List.of(new Pos(104, 65, 100, 90f, 0f)), 100, 100);

        TextComponent icon = (TextComponent) icons.children().getFirst();
        assertEquals("", icon.content());
        assertEquals(((4 + 128) * 2 << 15) | (128 * 2 << 6) | 16, icon.color().value());
    }
}
