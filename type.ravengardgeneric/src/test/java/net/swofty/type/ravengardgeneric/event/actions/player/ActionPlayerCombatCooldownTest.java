package net.swofty.type.ravengardgeneric.event.actions.player;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActionPlayerCombatCooldownTest {
    @Test
    void rapidAttacksAreDroppedThenScaledUntilCooldownCompletes() {
        UUID player = UUID.randomUUID();
        assertEquals(1.0, ActionPlayerCombat.attackStrength(player, 1_000_000_000L, 4.0), 0.0001);
        assertEquals(-1.0, ActionPlayerCombat.attackStrength(player, 1_050_000_000L, 4.0), 0.0001);
        assertEquals(0.5, ActionPlayerCombat.attackStrength(player, 1_125_000_000L, 4.0), 0.0001);
        assertEquals(1.0, ActionPlayerCombat.attackStrength(player, 1_400_000_000L, 4.0), 0.0001);
    }
}
