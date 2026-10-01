package net.swofty.type.ravengardgeneric.entity.animation;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavengardMobClipFacingTest {
    private static final Path MOBS = Path.of("..", "configuration", "ravengard", "mobs");
    private static final List<String> COMBAT_CLIPS = List.of(
            "skeleton_archer", "skeleton_knight", "skeleton_warrior", "zombie_knight",
            "demon_grunt", "demon_imp", "oculus", "silver_mimic", "bottle_mimic_short_yellow");

    @Test
    void capturedWorldYawIsTurnedBackToTheForwardAxis() {
        RavengardMobClip clip = new Gson().fromJson("""
                {"name": "turned", "parts": [
                  {"name": "body", "model": "m", "t": [1, 0, 0], "r": [0, 0.70710677, 0, 0.70710677]},
                  {"name": "head", "model": "m", "t": [2, 1, 0], "r": [0, 0.70710677, 0, 0.70710677]}],
                 "animations": {"walk": {"loop": true, "frames": [
                  {"0": {"t": [0, 0, -1], "r": [0, 1, 0, 0]}, "1": {"t": [0, 1, -2], "r": [0, 1, 0, 0]}}]}}}
                """, RavengardMobClip.class);

        clip.normalizeFacing();

        assertVector(new float[]{0, 0, 1}, clip.parts().get(0).translation());
        assertVector(new float[]{0, 1, 2}, clip.parts().get(1).translation());
        assertEquals(0, RavengardMobClip.yaw(clip.parts().get(0).leftRotation()), 1.0e-3);
        var frame = clip.animation("walk").frames().getFirst();
        assertVector(new float[]{0, 0, 1}, frame.get("0").translation());
        assertVector(new float[]{0, 1, 2}, frame.get("1").translation());
        assertEquals(0, RavengardMobClip.yaw(frame.get("0").leftRotation()), 1.0e-3);
        assertEquals(0, RavengardMobClip.yaw(frame.get("1").leftRotation()), 1.0e-3);
    }

    @Test
    void aLoopCapturedWhileTheMobWasTurningDoesNotCarryTheTurn() {
        RavengardMobClip clip = new Gson().fromJson("""
                {"name": "turning", "parts": [
                  {"name": "body", "model": "m", "t": [0, 0, 0], "r": [0, 0, 0, 1]},
                  {"name": "head", "model": "m", "t": [0, 1, 0], "r": [0, 0, 0, 1]}],
                 "animations": {"idle": {"loop": true, "frames": [
                  {"0": {"r": [0, 0, 0, 1]}, "1": {"t": [0, 1, 0.5], "r": [0, 0, 0, 1]}},
                  {"0": {"r": [0, 0.08715574, 0, 0.9961947]}},
                  {"0": {"r": [0, 0.17364818, 0, 0.9848077]}, "1": {"t": [0.17101, 1, 0.469846], "r": [0, 0.17364818, 0, 0.9848077]}}]}}}
                """, RavengardMobClip.class);

        clip.normalizeFacing();

        for (var frame : clip.animation("idle").frames()) {
            assertEquals(0, RavengardMobClip.yaw(frame.get("0").leftRotation()), 1.0e-3);
        }
        var last = clip.animation("idle").frames().getLast();
        assertVector(new float[]{0, 1, 0.5f}, last.get("1").translation());
        assertEquals(0, RavengardMobClip.yaw(last.get("1").leftRotation()), 1.0e-3);
    }

    @Test
    void aPartKeyedOnlyLateInATurningLoopStaysAttachedAcrossTheLoopPoint() {
        // the root turns 20 degrees over the loop; the head is keyed on the last frame only, so
        // it holds that value through the next pass and must be expressed against each frame's root
        RavengardMobClip clip = new Gson().fromJson("""
                {"name": "late", "parts": [
                  {"name": "body", "model": "m", "t": [0, 0, 0], "r": [0, 0, 0, 1]},
                  {"name": "head", "model": "m", "t": [0, 1, 0.5], "r": [0, 0, 0, 1]}],
                 "animations": {"idle": {"loop": true, "frames": [
                  {"0": {"r": [0, 0, 0, 1]}},
                  {"0": {"r": [0, 0.08715574, 0, 0.9961947]}},
                  {"0": {"r": [0, 0.17364818, 0, 0.9848077]}, "1": {"t": [0.17101, 1, 0.469846], "r": [0, 0.17364818, 0, 0.9848077]}}]}}}
                """, RavengardMobClip.class);

        clip.normalizeFacing();

        var frames = clip.animation("idle").frames();
        // the last frame, where the head was captured, has it straight ahead of the body
        assertVector(new float[]{0, 1, 0.5f}, frames.get(2).get("1").translation());
        // and on the next pass it is still expressed against the frame it is shown in
        assertTrue(frames.get(0).containsKey("1"), "head is not re-keyed at the loop point");
        assertEquals(20, RavengardMobClip.yaw(frames.get(0).get("1").leftRotation()), 1.0e-2);
        assertEquals(10, RavengardMobClip.yaw(frames.get(1).get("1").leftRotation()), 1.0e-2);
    }

    @Test
    void aRootLyingOnTheGroundDoesNotTurnItsRollIntoFacing() {
        // forward points almost straight down, so its heading is noise: here the mob only rolls
        RavengardMobClip clip = new Gson().fromJson("""
                {"name": "lying", "parts": [
                  {"name": "body", "model": "m", "t": [0, 0, 0], "r": [0.6427876, 0, 0, 0.7660444]},
                  {"name": "head", "model": "m", "t": [0, 0.2, 1], "r": [0.6427876, 0, 0, 0.7660444]}],
                 "animations": {"idle": {"loop": true, "frames": [
                  {"0": {"r": [0.6427876, 0, 0, 0.7660444]}, "1": {"t": [0, 0.2, 1]}},
                  {"0": {"r": [0.6330222, 0.1116189, 0.1330222, 0.7544065]}}]}}}
                """, RavengardMobClip.class);

        clip.normalizeFacing();

        var frames = clip.animation("idle").frames();
        assertVector(new float[]{0, 0.2f, 1}, frames.get(0).get("1").translation());
        assertTrue(frames.get(1).get("1") == null, "the head was re-keyed although the mob never turned");
        float[] rolled = frames.get(1).get("0").leftRotation();
        assertEquals(0.6330222, rolled[0], 1.0e-4);
        assertEquals(0.1116189, rolled[1], 1.0e-4);
    }

    @Test
    void everyOfficialAnimationStartsFacingTheSameWayAsTheRestPose() throws Exception {
        for (String name : COMBAT_CLIPS) {
            RavengardMobClip clip = new Gson().fromJson(
                    Files.readString(MOBS.resolve(name + ".json")), RavengardMobClip.class);
            clip.normalizeFacing();
            String root = String.valueOf(clip.rootPart());

            float[] rest = clip.parts().get(clip.rootPart()).leftRotation();
            if (rest != null) assertEquals(0, RavengardMobClip.yaw(rest), 0.5, name);
            for (var entry : clip.animations().entrySet()) {
                RavengardMobClip.Animation animation = entry.getValue();
                String label = name + "/" + entry.getKey();
                Double first = null;
                Double last = null;
                for (var frame : animation.frames()) {
                    RavengardMobClip.Keyframe key = frame.get(root);
                    if (key == null || key.leftRotation() == null) continue;
                    // a root lying on the ground has no heading to compare
                    if (!RavengardMobClip.headingReadable(key.leftRotation())) continue;
                    last = RavengardMobClip.yaw(key.leftRotation());
                    if (first == null) first = last;
                    if (animation.loop()) assertTrue(Math.abs(last) <= 45, label + " yaw " + last);
                }
                if (first == null) continue;
                if (animation.loop()) {
                    assertEquals(first, last, 10.0, label + " does not close");
                } else {
                    assertEquals(0, first, 0.5, label);
                }
            }
        }
    }

    private static void assertVector(float[] expected, float[] actual) {
        for (int axis = 0; axis < 3; axis++) assertEquals(expected[axis], actual[axis], 1.0e-4);
    }
}
