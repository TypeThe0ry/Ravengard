package net.swofty.type.ravengardgeneric.entity.animation;

import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavengardWalkCycleTest {
    private static final float[] IDENTITY = {0, 0, 0, 1};

    @Test
    void limbsAreFoundByTheirModelNames() {
        assertEquals(RavengardWalkCycle.Limb.LEFT_LEG, RavengardWalkCycle.limbOf("left_leg"));
        assertEquals(RavengardWalkCycle.Limb.RIGHT_ARM, RavengardWalkCycle.limbOf("rightarm"));
        assertEquals(RavengardWalkCycle.Limb.LEFT_ARM, RavengardWalkCycle.limbOf("arm_l"));
        assertEquals(RavengardWalkCycle.Limb.RIGHT_LEG, RavengardWalkCycle.limbOf("leg_r"));
        assertEquals(RavengardWalkCycle.Limb.NONE, RavengardWalkCycle.limbOf("sword_in_hand"));
        assertEquals(RavengardWalkCycle.Limb.NONE, RavengardWalkCycle.limbOf("body"));
    }

    @Test
    void legsSwingOppositeWaysAboutTheHipAndRestAtStrideZero() {
        Vec leftLeg = new Vec(0.12, -0.69, 0);
        Vec rightLeg = new Vec(-0.12, -0.69, 0);
        var cycle = new RavengardWalkCycle(List.of("left_leg", "right_leg", "body"),
                List.of(leftLeg, rightLeg, Vec.ZERO));
        assertFalse(cycle.animates(2));
        assertTrue(cycle.animates(0));

        var rest = cycle.pose(0, leftLeg, IDENTITY, 0);
        assertEquals(leftLeg, rest.translation());
        assertArrayEquals(IDENTITY, rest.leftRotation(), 1e-6f);

        int quarter = RavengardWalkCycle.TICKS_PER_STRIDE / 4;
        var left = cycle.pose(0, leftLeg, IDENTITY, quarter);
        var right = cycle.pose(1, rightLeg, IDENTITY, quarter);
        assertEquals(-left.translation().z(), right.translation().z(), 1e-4, "opposite swings");
        assertTrue(Math.abs(left.translation().z()) > 0.1, "the foot moves forward or back");
        assertEquals(leftLeg.x(), left.translation().x(), 1e-6, "the swing stays in the walking plane");
        Vec hip = leftLeg.add(0, 0.3, 0);
        assertEquals(0.3, left.translation().distance(hip), 1e-4, "the leg turns about the hip");
    }
}
