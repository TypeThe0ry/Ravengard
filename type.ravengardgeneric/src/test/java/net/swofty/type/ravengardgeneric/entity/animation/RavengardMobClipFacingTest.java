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
