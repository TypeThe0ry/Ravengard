package net.swofty.type.ravengardgeneric.hud;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import net.minestom.server.MinecraftServer;

import net.swofty.type.ravengardgeneric.classes.RavengardAbility;
import net.swofty.type.ravengardgeneric.classes.RavengardKit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RavengardHudComposerTest {
    @BeforeAll
    static void initializeMinestom() {
        MinecraftServer.init();
    }

    @AfterAll
    static void stopMinestom() {
        MinecraftServer.stopCleanly();
    }

    @Test
    void dungeonMinimapTilesCarryThePlayerWorldPosition() {
        RavengardHudState state = new RavengardHudState();
        state.setDungeon(true);
        state.setWorldX(12.25);
        state.setWorldZ(-34.5);

        int expected = (int) Math.round((12.25 + 512.0) * 4.0) << 12;
        expected |= (int) Math.round((-34.5 + 512.0) * 4.0);

        var tiles = RavengardHudComposer.compose(state).get(RavengardHudLayer.MINIMAP_TILES);
        assertNotNull(tiles);
        assertEquals(expected, tiles.color().value());
        assertEquals(expected, RavengardHudComposer.mapTint(state));
    }

    @Test
    void withoutTheMinimapItsLayersAreBlankAndTheRestOfTheHudStays() {
        RavengardHudState state = new RavengardHudState();
        state.setDungeon(true);
        state.setDate("10/03/26");

        var withMinimap = RavengardHudComposer.compose(state, true);
        var withoutMinimap = RavengardHudComposer.compose(state, false);

        for (RavengardHudLayer layer : new RavengardHudLayer[]{RavengardHudLayer.PLAYER_MARKER,
                RavengardHudLayer.MINIMAP_FRAME, RavengardHudLayer.MINIMAP_TILES, RavengardHudLayer.MINIMAP_BACKGROUND}) {
            assertEquals(net.kyori.adventure.text.Component.empty(), withoutMinimap.get(layer), layer.name());
        }
        assertEquals(withMinimap.get(RavengardHudLayer.STATS), withoutMinimap.get(RavengardHudLayer.STATS));
        assertEquals(withMinimap.get(RavengardHudLayer.KEYBIND_ICONS), withoutMinimap.get(RavengardHudLayer.KEYBIND_ICONS));
    }

    @Test
    void abilityItemCarriesTheAbilityUsedByItsTrigger() {
        for (RavengardAbility ability : RavengardAbility.values()) {
            var item = RavengardKit.ability(ability, RavengardKit.SLOT_ABILITY_ONE);
            assertEquals(ability, RavengardKit.abilityFrom(item));
        }
    }
}
