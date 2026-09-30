package net.swofty.type.ravengarddungeon.interactables;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractableAimTest {
    private static final Pos EYE = new Pos(0.5, 66.62, 0.5);
    private static final Vec SOUTH = new Vec(0, 0, 1);

    @Test
    void aimedBoxInFrontIsHitAtItsNearFace() {
        assertEquals(2.5, InteractableRegistry.rayBoxDistance(EYE, SOUTH, 0, 65, 3, 1, 67, 4), 1.0e-9);
    }

    @Test
    void boxesBehindOrBesideTheAimAreIgnored() {
        assertTrue(InteractableRegistry.rayBoxDistance(EYE, SOUTH, 0, 65, -4, 1, 67, -3) < 0);
        assertTrue(InteractableRegistry.rayBoxDistance(EYE, SOUTH, 4, 65, 3, 5, 67, 4) < 0);
    }

    @Test
    void standingInsideTheBoxCountsAsZeroDistance() {
        assertEquals(0, InteractableRegistry.rayBoxDistance(EYE, SOUTH, 0, 65, 0, 1, 68, 1), 1.0e-9);
    }
}
