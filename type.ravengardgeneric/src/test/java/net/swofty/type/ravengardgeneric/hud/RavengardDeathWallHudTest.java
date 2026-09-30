package net.swofty.type.ravengardgeneric.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.swofty.type.ravengardgeneric.gui.RavengardFont;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavengardDeathWallHudTest {
    @Test
    void tintPacksHalfBlockCentresAroundTheShaderOrigin() {
        assertEquals((2048 << 12) | 2048, RavengardHudComposer.deathWallTint(0, 0));
        assertEquals((2049 << 12) | 2047, RavengardHudComposer.deathWallTint(0.5, -0.5));
        assertEquals((0 << 12) | 0xFFF, RavengardHudComposer.deathWallTint(-5000, 5000));
    }

    @Test
    void minimapLineOffsetsTheWallGlyphByItsSideAndTintsItRelativeToThePlayer() {
        RavengardHudState state = new RavengardHudState();
        state.setDungeon(true);
        state.setWorldX(10);
        state.setWorldZ(-20);
        state.setDeathWall(new RavengardDeathWallView(30, 40, 50));
        state.setDeathWallWarning(new RavengardDeathWallView(35, 45, 20));

        TextComponent line = (TextComponent) RavengardHudComposer.minimapDeathWall(state);

        assertEquals(RavengardFont.pixelSpace(100) + RavengardHudGlyphs.DEATHWALL, line.content());
        assertEquals(RavengardHudComposer.deathWallTint(20, 60), line.color().value());
        TextComponent warning = (TextComponent) line.children().getFirst();
        assertEquals(RavengardFont.pixelSpace(40 - 100 - 5) + RavengardHudGlyphs.DEATHWALL_WARNING, warning.content());
        assertEquals(RavengardHudComposer.deathWallTint(25, 65), warning.color().value());
    }

    @Test
    void dungeonComposeDrawsTheWallOnlyWhileItIsActive() {
        RavengardHudState state = new RavengardHudState();
        state.setDungeon(true);
        Component idle = RavengardHudComposer.compose(state).get(RavengardHudLayer.SPACER_10);
        assertTrue(idle == null || !idle.toString().contains(RavengardHudGlyphs.DEATHWALL));

        state.setDeathWall(new RavengardDeathWallView(0, 0, 100));
        Component active = RavengardHudComposer.compose(state).get(RavengardHudLayer.SPACER_10);
        assertNotNull(active);
        assertTrue(((TextComponent) active).content().endsWith(RavengardHudGlyphs.DEATHWALL));
    }

    @Test
    void fullMapLineLeadsTheGlyphWithASpaceAsWideAsTheWall() {
        TextComponent line = (TextComponent) RavengardHudComposer.fullMapDeathWall(
                new RavengardDeathWallView(-100, 60, 80), RavengardHudGlyphs.DEATHWALL);
        assertEquals(RavengardFont.pixelSpace(161) + RavengardHudGlyphs.DEATHWALL, line.content());
        assertEquals(RavengardHudComposer.deathWallTint(-100, 60), line.color().value());
    }

    @Test
    void fullMapSkipsAWallTooWideToFitOnOneTabHeaderLine() {
        assertEquals(null, RavengardHudComposer.fullMapDeathWall(
                new RavengardDeathWallView(0, 0, 262.5), RavengardHudGlyphs.DEATHWALL));
        assertNotNull(RavengardHudComposer.fullMapDeathWall(
                new RavengardDeathWallView(0, 0, 207.39), RavengardHudGlyphs.DEATHWALL_WARNING));
    }

    @Test
    void bossBarNameRidesTheSidebarLineThePackLiftsAboveTheBar() {
        RavengardHudState state = new RavengardHudState();
        state.setDungeon(true);
        state.setBossBarName("Death Wall 1/5 | Moves in 00:16");

        TextComponent line = (TextComponent) RavengardHudComposer.compose(state).get(RavengardHudLayer.SPACER_2);

        assertEquals("Death Wall 1/5 | Moves in 00:16", line.content());
        assertEquals(0xFEFD0E, line.color().value());
    }
}
