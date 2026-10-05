package net.swofty.type.ravengardgeneric.entity.animation;

import net.minestom.server.coordinate.Vec;

import java.util.List;

/**
 * A walk for rigs captured without one: legs and arms swing about the hip and shoulder, opposite
 * sides in turn. The swing axis is the rig's own left-right line (from its left leg to its right
 * leg), so it holds whichever way the captured model faces.
 */
final class RavengardWalkCycle {
    static final double SWING_DEGREES = 28.0;
    static final int TICKS_PER_STRIDE = 16;
    private static final double PIVOT_ABOVE_ORIGIN = 0.3;

    enum Limb { LEFT_LEG, RIGHT_LEG, LEFT_ARM, RIGHT_ARM, NONE }

    record Pose(Vec translation, float[] leftRotation) {
    }

    private final Limb[] limbs;
    private final Vec axis;

    RavengardWalkCycle(List<String> partNames, List<Vec> translations) {
        limbs = new Limb[partNames.size()];
        Vec left = null, right = null;
        for (int i = 0; i < limbs.length; i++) {
            limbs[i] = limbOf(partNames.get(i));
            if (limbs[i] == Limb.LEFT_LEG) left = translations.get(i);
            if (limbs[i] == Limb.RIGHT_LEG) right = translations.get(i);
        }
        Vec lateral = left == null || right == null ? new Vec(1, 0, 0) : left.sub(right).withY(0);
        axis = lateral.lengthSquared() < 1e-6 ? new Vec(1, 0, 0) : lateral.normalize();
    }

    boolean animates(int part) {
        return part < limbs.length && limbs[part] != Limb.NONE;
    }

    /** The limb's pose at this point of the stride, or its rest pose at stride zero. */
    Pose pose(int part, Vec translation, float[] rotation, int tick) {
        double phase = Math.sin(2 * Math.PI * tick / TICKS_PER_STRIDE);
        double sign = switch (limbs[part]) {
            case LEFT_LEG, RIGHT_ARM -> 1;
            case RIGHT_LEG, LEFT_ARM -> -1;
            case NONE -> 0;
        };
        double angle = Math.toRadians(SWING_DEGREES) * phase * sign;
        if (angle == 0) return new Pose(translation, rotation);
        float sin = (float) Math.sin(angle / 2), cos = (float) Math.cos(angle / 2);
        float[] swing = {(float) axis.x() * sin, (float) axis.y() * sin, (float) axis.z() * sin, cos};
        Vec pivot = translation.add(0, PIVOT_ABOVE_ORIGIN, 0);
        Vec moved = pivot.add(rotate(swing, translation.sub(pivot)));
        return new Pose(moved, multiply(swing, rotation));
    }

    static Limb limbOf(String name) {
        String lower = name.toLowerCase();
        boolean leg = lower.contains("leg");
        boolean arm = lower.contains("arm") && !lower.contains("armor");
        if (!leg && !arm) return Limb.NONE;
        boolean left = lower.contains("left") || lower.endsWith("_l") || lower.endsWith("left");
        boolean right = lower.contains("right") || lower.endsWith("_r");
        if (left == right) return Limb.NONE;
        return leg ? (left ? Limb.LEFT_LEG : Limb.RIGHT_LEG) : (left ? Limb.LEFT_ARM : Limb.RIGHT_ARM);
    }

    static float[] multiply(float[] a, float[] b) {
        return new float[]{
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }

    static Vec rotate(float[] q, Vec v) {
        float[] p = {(float) v.x(), (float) v.y(), (float) v.z(), 0};
        float[] conjugate = {-q[0], -q[1], -q[2], q[3]};
        float[] r = multiply(multiply(q, p), conjugate);
        return new Vec(r[0], r[1], r[2]);
    }
}
