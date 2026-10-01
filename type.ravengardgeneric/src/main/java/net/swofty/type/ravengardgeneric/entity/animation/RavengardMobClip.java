package net.swofty.type.ravengardgeneric.entity.animation;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RavengardMobClip {
    public static final File MOBS_DIR = new File("./configuration/ravengard/mobs");

    private static final Gson GSON = new Gson();
    private static final Map<String, RavengardMobClip> CACHE = new ConcurrentHashMap<>();

    private String name;
    private double health;
    private double walkSpeed;
    private List<Part> parts;
    private Map<String, Animation> animations;

    public static RavengardMobClip load(String name) {
        return CACHE.computeIfAbsent(name, key -> {
            try (InputStream stream = new FileInputStream(new File(MOBS_DIR, key + ".json"))) {
                RavengardMobClip clip = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8),
                        RavengardMobClip.class);
                clip.normalizeFacing();
                return clip;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to load mob clip " + key, exception);
            }
        });
    }

    private static final List<String> ROOT_PARTS = List.of("body", "bottom", "eye", "torso", "top", "chest");
    private static final double TURNING_CAPTURE_DEGREES = 60;

    /**
     * Captures store every transform in world space, so each animation carries whichever way the
     * captured mob happened to face, and a different one per animation. Everything is turned back
     * here so that a clip always faces +Z; the rig then only has to add its own yaw.
     *
     * The facing of an animation is the yaw of its root part. A one-shot keeps the yaw it started
     * with, so wind-ups and spins survive; a loop uses its average, or follows the root frame by
     * frame when the captured mob was plainly turning while it was recorded.
     */
    void normalizeFacing() {
        List<Part> rig = parts();
        if (rig.isEmpty()) return;
        int root = rootPart();
        float[][] capturedBase = new float[rig.size()][];
        for (int index = 0; index < rig.size(); index++) {
            float[] rotation = rig.get(index).r;
            capturedBase[index] = rotation == null || rotation.length < 4
                    ? new float[]{0, 0, 0, 1} : rotation.clone();
        }
        double baseYaw = yaw(capturedBase[root]);
        for (Part part : rig) {
            part.t = turned(part.t, -baseYaw);
            part.r = turnedRotation(part.r, -baseYaw);
        }
        for (Animation animation : animations().values()) {
            normalize(animation, root, capturedBase, baseYaw);
        }
    }

    int rootPart() {
        for (String candidate : ROOT_PARTS) {
            int index = indexOf(candidate);
            if (index >= 0) return index;
        }
        return 0;
    }

    private int indexOf(String partName) {
        List<Part> rig = parts();
        for (int index = 0; index < rig.size(); index++) {
            if (partName.equals(rig.get(index).name)) return index;
        }
        return -1;
    }

    private void normalize(Animation animation, int root, float[][] capturedBase, double baseYaw) {
        List<Map<String, Keyframe>> frames = animation.frames();
        if (frames.isEmpty()) return;
        double[] rootYaw = new double[frames.size()];
        boolean keyed = false;
        double last = 0;
        int firstKey = -1;
        for (int frame = 0; frame < frames.size(); frame++) {
            Keyframe key = frames.get(frame).get(String.valueOf(root));
            if (key != null && key.leftRotation() != null) {
                last = yaw(key.r);
                if (!keyed) firstKey = frame;
                keyed = true;
            }
            rootYaw[frame] = last;
        }
        if (!keyed) {
            // the root never moved while this was captured: the parts that did tell how far the
            // mob had turned since the base pose was taken
            double facing = baseYaw + turnSinceBase(frames, capturedBase);
            for (Map<String, Keyframe> frame : frames) turn(frame, -facing);
            return;
        }
        for (int frame = 0; frame < firstKey; frame++) rootYaw[frame] = rootYaw[firstKey];

        if (animation.loop() && spread(rootYaw) > TURNING_CAPTURE_DEGREES) {
            followRoot(frames, rootYaw);
            return;
        }
        double facing = animation.loop() ? mean(rootYaw) : rootYaw[0];
        for (Map<String, Keyframe> frame : frames) turn(frame, -facing);
    }

    /** Re-keys every animated part on every frame relative to the root's yaw in that frame. */
    private void followRoot(List<Map<String, Keyframe>> frames, double[] rootYaw) {
        Map<String, float[]> translation = new java.util.HashMap<>();
        Map<String, float[]> rotation = new java.util.HashMap<>();
        for (int frame = 0; frame < frames.size(); frame++) {
            Map<String, Keyframe> keys = frames.get(frame);
            for (Map.Entry<String, Keyframe> entry : keys.entrySet()) {
                Keyframe key = entry.getValue();
                if (key.translation() != null) translation.put(entry.getKey(), key.t.clone());
                if (key.leftRotation() != null) rotation.put(entry.getKey(), key.r.clone());
            }
            java.util.Set<String> animated = new java.util.HashSet<>(translation.keySet());
            animated.addAll(rotation.keySet());
            for (String part : animated) {
                Keyframe key = keys.computeIfAbsent(part, ignored -> new Keyframe());
                float[] heldTranslation = translation.get(part);
                float[] heldRotation = rotation.get(part);
                key.t = heldTranslation == null ? null : turned(heldTranslation.clone(), -rootYaw[frame]);
                key.r = heldRotation == null ? null : turnedRotation(heldRotation.clone(), -rootYaw[frame]);
            }
        }
    }

    private static double turnSinceBase(List<Map<String, Keyframe>> frames, float[][] capturedBase) {
        List<Double> turns = new ArrayList<>();
        for (int part = 0; part < capturedBase.length; part++) {
            for (Map<String, Keyframe> frame : frames) {
                Keyframe key = frame.get(String.valueOf(part));
                if (key == null || key.leftRotation() == null) continue;
                float[] relative = multiply(key.r, conjugate(capturedBase[part]));
                turns.add(wrap(Math.toDegrees(2 * Math.atan2(relative[1], relative[3]))));
                break;
            }
        }
        if (turns.isEmpty()) return 0;
        turns.sort(Double::compare);
        return turns.get(turns.size() / 2);
    }

    private static void turn(Map<String, Keyframe> frame, double degrees) {
        for (Keyframe key : frame.values()) {
            key.t = turned(key.t, degrees);
            key.r = turnedRotation(key.r, degrees);
        }
    }

    private static float[] turned(float[] vector, double degrees) {
        if (vector == null || vector.length < 3) return vector;
        double theta = Math.toRadians(degrees);
        double sin = Math.sin(theta), cos = Math.cos(theta);
        float x = vector[0], z = vector[2];
        vector[0] = (float) (x * cos + z * sin);
        vector[2] = (float) (-x * sin + z * cos);
        return vector;
    }

    private static float[] turnedRotation(float[] rotation, double degrees) {
        if (rotation == null || rotation.length < 4) return rotation;
        double half = Math.toRadians(degrees) / 2;
        float[] result = multiply(new float[]{0, (float) Math.sin(half), 0, (float) Math.cos(half)}, rotation);
        System.arraycopy(result, 0, rotation, 0, 4);
        return rotation;
    }

    private static float[] multiply(float[] a, float[] b) {
        return new float[]{
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }

    private static float[] conjugate(float[] rotation) {
        return new float[]{-rotation[0], -rotation[1], -rotation[2], rotation[3]};
    }

    /** The direction a rotation turns +Z towards, measured the way the rig measures yaw before the sign flip. */
    static double yaw(float[] rotation) {
        float x = rotation[0], y = rotation[1], z = rotation[2], w = rotation[3];
        return Math.toDegrees(Math.atan2(2 * (x * z + w * y), 1 - 2 * (x * x + y * y)));
    }

    private static double wrap(double degrees) {
        return ((degrees + 180) % 360 + 360) % 360 - 180;
    }

    private static double mean(double[] degrees) {
        double sin = 0, cos = 0;
        for (double value : degrees) {
            sin += Math.sin(Math.toRadians(value));
            cos += Math.cos(Math.toRadians(value));
        }
        return Math.toDegrees(Math.atan2(sin, cos));
    }

    private static double spread(double[] degrees) {
        double centre = mean(degrees);
        double low = 0, high = 0;
        for (double value : degrees) {
            double offset = wrap(value - centre);
            low = Math.min(low, offset);
            high = Math.max(high, offset);
        }
        return high - low;
    }

    public static List<String> available() {
        List<String> names = new ArrayList<>();
        File[] files = MOBS_DIR.listFiles((dir, file) -> file.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                names.add(file.getName().substring(0, file.getName().length() - 5));
            }
        }
        names.sort(String::compareTo);
        return names;
    }

    public String name() {
        return name;
    }

    public double health() {
        return health;
    }

    public double walkSpeed() {
        return walkSpeed;
    }

    public List<Part> parts() {
        return parts == null ? List.of() : parts;
    }

    public Map<String, Animation> animations() {
        return animations == null ? Map.of() : animations;
    }

    public Animation animation(String key) {
        return animations().get(key);
    }

    public List<String> attackAnimations() {
        List<String> names = new ArrayList<>();
        for (String key : animations().keySet()) {
            if (key.startsWith("attack")) names.add(key);
        }
        names.sort(String::compareTo);
        return names;
    }

    public static final class Part {
        private String model;
        private String name;
        private double[] offset;
        private float[] t;
        private float[] s;
        private float[] r;
        private float[] rr;
        private int context;
        private float viewRange;

        public String model() {
            return model;
        }

        public String name() {
            return name;
        }

        public double[] offset() {
            return offset == null || offset.length < 3 ? new double[]{0, 0, 0} : offset;
        }

        public float[] translation() {
            return t == null || t.length < 3 ? null : t;
        }

        public float[] scale() {
            return s == null || s.length < 3 ? new float[]{1, 1, 1} : s;
        }

        public float[] leftRotation() {
            return r == null || r.length < 4 ? null : r;
        }

        public float[] rightRotation() {
            return rr == null || rr.length < 4 ? new float[]{0, 0, 0, 1} : rr;
        }

        public int context() {
            return context;
        }

        public float viewRange() {
            return viewRange <= 0 ? 4096f : viewRange;
        }
    }

    public static final class Animation {
        private boolean loop;
        private List<Map<String, Keyframe>> frames;

        public boolean loop() {
            return loop;
        }

        public List<Map<String, Keyframe>> frames() {
            return frames == null ? List.of() : frames;
        }
    }

    public static final class Keyframe {
        private float[] t;
        private float[] r;
        private float[] s;
        private Integer d;

        public float[] translation() {
            return t == null || t.length < 3 ? null : t;
        }

        public float[] leftRotation() {
            return r == null || r.length < 4 ? null : r;
        }

        public float[] scale() {
            return s == null || s.length < 3 ? null : s;
        }

        public Integer duration() {
            return d;
        }
    }
}
