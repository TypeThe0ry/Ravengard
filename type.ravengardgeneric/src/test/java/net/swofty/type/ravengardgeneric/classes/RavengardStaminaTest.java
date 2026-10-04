package net.swofty.type.ravengardgeneric.classes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavengardStaminaTest {
    @Test
    void sprintingDrainsStaminaUntilTheRunnerIsExhausted() {
        var state = new RavengardStamina.State();
        long now = 0;
        for (int step = 0; step < 80; step++) {
            now += 100;
            RavengardStamina.step(state, true, now, 0.1);
        }
        assertEquals(4.0, state.stamina, 1e-6);
        assertFalse(state.exhausted);
        for (int step = 0; step < 5; step++) {
            now += 100;
            RavengardStamina.step(state, true, now, 0.1);
        }
        assertEquals(0.0, state.stamina, 1e-6);
        assertTrue(state.exhausted);
    }

    @Test
    void staminaRefillsAfterAPauseAndSprintingReturnsAtAThird() {
        var state = new RavengardStamina.State();
        state.stamina = 0;
        state.exhausted = true;
        state.lastSpentAt = 0;
        RavengardStamina.step(state, false, 500, 0.5);
        assertEquals(0.0, state.stamina, 1e-6);
        long now = 1000;
        while (state.stamina < RavengardStamina.RECOVERED_AT - 1e-9) {
            assertTrue(state.exhausted);
            now += 100;
            RavengardStamina.step(state, false, now, 0.1);
        }
        assertFalse(state.exhausted);
        for (int step = 0; step < 100; step++) RavengardStamina.step(state, false, now += 100, 0.1);
        assertEquals(RavengardStamina.MAX, state.stamina, 1e-6);
    }
}
