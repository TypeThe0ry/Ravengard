package net.swofty.type.ravengarddungeon.game;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.instance.WorldBorder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class RavengardDeathWall {
    static final long MATCH_DURATION_SECONDS = 30 * 60;
    static final long FIRST_WARNING_SECONDS = 5 * 60;
    static final long EXPLORATION_SECONDS = FIRST_WARNING_SECONDS;
    static final long WARNING_SECONDS = 30;
    static final int STAGE_COUNT = 5;
    static final double FINAL_RADIUS = 12.0;
    static final float OUTSIDE_DAMAGE = 2f;

    private static final Stage[] STAGES = {
            new Stage(1, 300, 360, 480, 600, 0.78),
            new Stage(2, 600, 660, 780, 900, 0.58),
            new Stage(3, 900, 960, 1080, 1200, 0.40),
            new Stage(4, 1200, 1260, 1380, 1500, 0.24),
            new Stage(5, 1500, 1560, 1740, 1800, 0.0)
    };

    private RavengardDeathWall() {
    }

    record Center(double x, double z) {
        Center interpolate(Center target, double progress) {
            double clamped = Math.clamp(progress, 0.0, 1.0);
            return new Center(x + (target.x - x) * clamped, z + (target.z - z) * clamped);
        }
    }

    record Stage(int number, long warningAt, long movingAt, long holdAt, long endsAt,
                 double targetRadiusFactor) {
    }

    record Timeline(Center initialCenter, double initialRadius, List<Center> stageCenters,
                    List<Double> stageRadii) {
        Timeline {
            stageCenters = List.copyOf(stageCenters);
            stageRadii = List.copyOf(stageRadii);
            if (stageCenters.size() != STAGE_COUNT || stageRadii.size() != STAGE_COUNT) {
                throw new IllegalArgumentException("Death Wall timeline requires " + STAGE_COUNT + " stages");
            }
        }

        Center centerBefore(int stage) {
            return stage <= 1 ? initialCenter : stageCenters.get(stage - 2);
        }

        double radiusBefore(int stage) {
            return stage <= 1 ? initialRadius : stageRadii.get(stage - 2);
        }

        Center targetCenter(int stage) {
            return stageCenters.get(stage - 1);
        }

        double targetRadius(int stage) {
            return stageRadii.get(stage - 1);
        }
    }

    static Timeline timeline(double mapCenterX, double mapCenterZ, double mapRadius,
                             List<Pos> candidates, long seed) {
        double initialRadius = Math.max(FINAL_RADIUS, mapRadius);
        Center initialCenter = new Center(mapCenterX, mapCenterZ);
        Center finalCenter = selectCenter(mapCenterX, mapCenterZ, initialRadius, candidates, seed);
        List<Center> centers = new ArrayList<>(STAGE_COUNT);
        List<Double> radii = new ArrayList<>(STAGE_COUNT);
        double shrinkDistance = initialRadius - FINAL_RADIUS;
        for (Stage stage : STAGES) {
            double radius = stage.number == STAGE_COUNT
                    ? FINAL_RADIUS
                    : FINAL_RADIUS + shrinkDistance * stage.targetRadiusFactor;
            double centerProgress = shrinkDistance == 0 ? 1.0
                    : (initialRadius - radius) / shrinkDistance;
            centers.add(initialCenter.interpolate(finalCenter, centerProgress));
            radii.add(radius);
        }
        return new Timeline(initialCenter, initialRadius, centers, radii);
    }

    static Timeline centeredTimeline(double centerX, double centerZ, double initialRadius) {
        double radius = Math.max(FINAL_RADIUS, initialRadius);
        return timeline(centerX, centerZ, radius, List.of(new Pos(centerX, 0, centerZ)), 0);
    }

    static Center selectCenter(double mapCenterX, double mapCenterZ, double mapRadius,
                               List<Pos> candidates, long seed) {
        List<Pos> safe = candidates == null ? List.of() : candidates.stream()
                .filter(candidate -> Math.abs(candidate.x() - mapCenterX) <= mapRadius - FINAL_RADIUS)
                .filter(candidate -> Math.abs(candidate.z() - mapCenterZ) <= mapRadius - FINAL_RADIUS)
                .sorted(Comparator.comparingDouble(Pos::x).thenComparingDouble(Pos::z))
                .toList();
        if (!safe.isEmpty()) {
            Pos selected = safe.get(Math.floorMod(mix(seed), safe.size()));
            return new Center(selected.x(), selected.z());
        }
        double available = Math.max(0, mapRadius - FINAL_RADIUS) * 0.72;
        long first = Integer.toUnsignedLong(mix(seed));
        long second = Integer.toUnsignedLong(mix(first));
        return new Center(mapCenterX + signedUnit(first) * available,
                mapCenterZ + signedUnit(second) * available);
    }

    private static int mix(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return (int) (mixed ^ (mixed >>> 31));
    }

    private static double signedUnit(long value) {
        return (Math.floorMod(value, 2_000_001L) - 1_000_000L) / 1_000_000.0;
    }

    static double coveringRadius(double mapCenterX, double mapCenterZ, double mapRadius, Center wallCenter) {
        double offset = Math.max(Math.abs(wallCenter.x() - mapCenterX),
                Math.abs(wallCenter.z() - mapCenterZ));
        return Math.max(FINAL_RADIUS, mapRadius) + offset;
    }

    static WorldBorder border(State state) {
        return initialBorder(state.currentCenter().x(), state.currentCenter().z(), state.radius())
                .withWarningTime(0);
    }

    static WorldBorder initialBorder(double centerX, double centerZ, double radius) {
        return WorldBorder.DEFAULT_BORDER
                .withCenter(centerX, centerZ)
                .withDiameter(Math.max(FINAL_RADIUS, radius) * 2.0)
                .withWarningDistance(8)
                .withWarningTime((int) WARNING_SECONDS);
    }

    static long remainingSeconds(long elapsedSeconds) {
        return Math.max(0, MATCH_DURATION_SECONDS - elapsedSeconds);
    }

    static double radius(long elapsedSeconds, double initialRadius) {
        return state(elapsedSeconds, initialRadius).radius();
    }

    static boolean isOutside(double x, double z, double centerX, double centerZ, double radius) {
        return Math.abs(x - centerX) > radius || Math.abs(z - centerZ) > radius;
    }

    static boolean damageOutside(LivingEntity entity, long elapsedSeconds,
                                 double centerX, double centerZ, double currentRadius) {
        State wall = state(elapsedSeconds, centeredTimeline(centerX, centerZ, Math.max(264, currentRadius)));
        if (!wall.damaging() || !isOutside(entity.getPosition().x(), entity.getPosition().z(),
                centerX, centerZ, currentRadius)) return false;
        return applyUnmitigatedDamage(entity, wall.damageAmount());
    }

    static boolean shouldDamage(long elapsedSeconds, long lastDamageAtSeconds) {
        return elapsedSeconds >= FIRST_WARNING_SECONDS && elapsedSeconds < MATCH_DURATION_SECONDS
                && elapsedSeconds > lastDamageAtSeconds;
    }

    static boolean applyUnmitigatedDamage(LivingEntity entity, float amount) {
        if (entity.isDead() || amount <= 0) return false;
        float healthBefore = entity.getHealth();
        entity.damage(DamageType.OUTSIDE_BORDER, amount);
        float expectedHealth = Math.max(0, healthBefore - amount);
        if (entity.getHealth() > expectedHealth) entity.setHealth(expectedHealth);
        return true;
    }

    enum Phase { EXPLORATION, WARNING, MOVING, HOLD, EXPIRED }

    record State(Phase phase, long elapsedSeconds, long remainingSeconds, int stage,
                 long phaseRemainingSeconds, Center currentCenter, double radius,
                 Center nextCenter, double nextRadius, double progress) {
        boolean hasCurrentWall() {
            return visible();
        }

        boolean hasNextZone() {
            return phase == Phase.WARNING || phase == Phase.MOVING;
        }

        boolean visible() {
            return phase != Phase.EXPLORATION && phase != Phase.EXPIRED;
        }

        boolean damaging() {
            return visible();
        }

        int nextStage() {
            return stage;
        }

        float damageAmount() {
            int activeStage = phase == Phase.WARNING ? Math.max(1, stage - 1) : Math.max(1, stage);
            return OUTSIDE_DAMAGE + activeStage - 1;
        }
    }

    static State state(long elapsedSeconds, double initialRadius) {
        return state(elapsedSeconds, centeredTimeline(0, 0, initialRadius));
    }

    static State state(long elapsedSeconds, Timeline timeline) {
        long elapsed = Math.max(0, elapsedSeconds);
        if (elapsed >= MATCH_DURATION_SECONDS) {
            Center center = timeline.targetCenter(STAGE_COUNT);
            double radius = timeline.targetRadius(STAGE_COUNT);
            return new State(Phase.EXPIRED, elapsed, 0, STAGE_COUNT, 0,
                    center, radius, center, radius, 1.0);
        }
        if (elapsed < FIRST_WARNING_SECONDS) {
            return new State(Phase.EXPLORATION, elapsed, remainingSeconds(elapsed), 0,
                    FIRST_WARNING_SECONDS - elapsed, timeline.initialCenter(), timeline.initialRadius(),
                    timeline.targetCenter(1), timeline.targetRadius(1), 0.0);
        }
        Stage stage = stageDefinition(elapsed);
        Center fromCenter = timeline.centerBefore(stage.number);
        Center targetCenter = timeline.targetCenter(stage.number);
        double fromRadius = timeline.radiusBefore(stage.number);
        double targetRadius = timeline.targetRadius(stage.number);
        Phase phase;
        long remaining;
        double progress;
        Center currentCenter;
        double radius;
        if (elapsed < stage.movingAt) {
            phase = Phase.WARNING;
            remaining = stage.movingAt - elapsed;
            progress = phaseProgress(elapsed, stage.warningAt, stage.movingAt);
            currentCenter = fromCenter;
            radius = fromRadius;
        } else if (elapsed < stage.holdAt) {
            phase = Phase.MOVING;
            remaining = stage.holdAt - elapsed;
            progress = phaseProgress(elapsed, stage.movingAt, stage.holdAt);
            currentCenter = fromCenter.interpolate(targetCenter, progress);
            radius = interpolate(fromRadius, targetRadius, progress);
        } else {
            phase = Phase.HOLD;
            remaining = stage.endsAt - elapsed;
            progress = phaseProgress(elapsed, stage.holdAt, stage.endsAt);
            currentCenter = targetCenter;
            radius = targetRadius;
        }
        return new State(phase, elapsed, remainingSeconds(elapsed), stage.number,
                Math.max(0, remaining), currentCenter, radius, targetCenter, targetRadius, progress);
    }

    static Phase phase(long elapsedSeconds) {
        long elapsed = Math.max(0, elapsedSeconds);
        if (elapsed >= MATCH_DURATION_SECONDS) return Phase.EXPIRED;
        if (elapsed < FIRST_WARNING_SECONDS) return Phase.EXPLORATION;
        Stage stage = stageDefinition(elapsed);
        if (elapsed < stage.movingAt) return Phase.WARNING;
        if (elapsed < stage.holdAt) return Phase.MOVING;
        return Phase.HOLD;
    }

    static double shrinkProgress(long elapsedSeconds) {
        double initialRadius = 262;
        State state = state(elapsedSeconds, initialRadius);
        return Math.clamp((initialRadius - state.radius()) / (initialRadius - FINAL_RADIUS), 0.0, 1.0);
    }

    static int stage(long elapsedSeconds) {
        long elapsed = Math.max(0, elapsedSeconds);
        if (elapsed < FIRST_WARNING_SECONDS) return 0;
        if (elapsed >= MATCH_DURATION_SECONDS) return STAGE_COUNT;
        return stageDefinition(elapsed).number;
    }

    private static Stage stageDefinition(long elapsed) {
        for (Stage stage : STAGES) {
            if (elapsed < stage.endsAt) return stage;
        }
        return STAGES[STAGES.length - 1];
    }

    private static double phaseProgress(long elapsed, long startsAt, long endsAt) {
        if (endsAt <= startsAt) return 1.0;
        return Math.clamp((double) (elapsed - startsAt) / (endsAt - startsAt), 0.0, 1.0);
    }

    private static double interpolate(double from, double target, double progress) {
        return from + (target - from) * Math.clamp(progress, 0.0, 1.0);
    }

    static String status(long elapsedSeconds) {
        return status(state(elapsedSeconds, FINAL_RADIUS));
    }

    static String status(State state) {
        return switch (state.phase()) {
            case EXPLORATION -> "探索 | 剩余 " + formatClock(state.remainingSeconds())
                    + " | 距墙预警 " + formatClock(state.phaseRemainingSeconds());
            case WARNING -> "死亡之墙 " + state.stage() + "/" + STAGE_COUNT + " | 距移动 "
                    + formatClock(state.phaseRemainingSeconds());
            case MOVING -> "死亡之墙 " + state.stage() + "/" + STAGE_COUNT + " | 移动中 | "
                    + formatClock(state.phaseRemainingSeconds());
            case HOLD -> "死亡之墙 " + state.stage() + "/" + STAGE_COUNT + " | 稳定 | "
                    + (state.stage() == STAGE_COUNT ? "距最终闭合 " : "距下次预警 ")
                    + formatClock(state.phaseRemainingSeconds());
            case EXPIRED -> "地牢已过期";
        };
    }

    static String progressStatus(State state) {
        return switch (state.phase()) {
            case EXPLORATION -> "死亡之墙休眠 | 距预警 "
                    + formatClock(state.phaseRemainingSeconds());
            case WARNING -> "死亡之墙第 " + state.stage() + " 阶段预警 | "
                    + formatClock(state.phaseRemainingSeconds());
            case MOVING -> "死亡之墙第 " + state.stage() + " 阶段移动中 | "
                    + formatClock(state.phaseRemainingSeconds());
            case HOLD -> "死亡之墙第 " + state.stage() + " 阶段稳定 | "
                    + formatClock(state.phaseRemainingSeconds());
            case EXPIRED -> "死亡之墙已闭合";
        };
    }

    static String hudStatus(State state) {
        return switch (state.phase()) {
            case EXPLORATION -> "探索";
            case WARNING -> "死亡之墙预警 " + state.stage() + "/" + STAGE_COUNT;
            case MOVING -> "死亡之墙移动中 " + state.stage() + "/" + STAGE_COUNT;
            case HOLD -> "安全区 " + state.stage() + "/" + STAGE_COUNT;
            case EXPIRED -> "地牢已过期";
        };
    }

    static float progressBar(State state) {
        return switch (state.phase()) {
            case EXPLORATION, EXPIRED -> 0f;
            case WARNING, MOVING, HOLD -> (float) state.progress();
        };
    }

    static int mapDiameter(double radius) {
        return Math.clamp((int) Math.round(radius * 2.0), 1, 2048);
    }

    static String formatClock(long seconds) {
        long clamped = Math.max(0, seconds);
        return String.format("%02d:%02d", clamped / 60, clamped % 60);
    }

    static int packMapCenter(double centerX, double centerZ) {
        int x = Math.clamp((int) Math.round((centerX + 1024.0) * 2.0), 0, 0xFFF);
        int z = Math.clamp((int) Math.round((centerZ + 1024.0) * 2.0), 0, 0xFFF);
        return (x << 12) | z;
    }
}
