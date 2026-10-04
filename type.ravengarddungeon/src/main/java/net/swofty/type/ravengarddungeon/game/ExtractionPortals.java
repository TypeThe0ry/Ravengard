package net.swofty.type.ravengarddungeon.game;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;
import net.swofty.type.ravengarddungeon.interactables.DungeonExtraction;
import net.swofty.type.ravengardgeneric.sound.RavengardSounds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Escape portals as the official game runs them: a limited number appear in waves as the Death
 * Wall closes in, each closes once a Mercenary has escaped through it, and some appear in sets of
 * three so a whole team can leave together.
 */
final class ExtractionPortals {
    static final int SITES_PER_WAVE = 2;
    static final int SET_SIZE = 3;
    static final int SET_SPACING = 4;

    record Site(Pos anchor, List<Pos> portals) {
        boolean isSet() {
            return portals.size() > 1;
        }
    }

    record Wave(long opensAt, List<Site> sites) {
    }

    private final Instance instance;
    private final RavengardDungeonGame game;
    private final List<Wave> waves;
    private final List<DungeonExtraction> open = new java.util.concurrent.CopyOnWriteArrayList<>();
    private int nextWave;

    ExtractionPortals(Instance instance, RavengardDungeonGame game, List<Wave> waves) {
        this.instance = instance;
        this.game = game;
        this.waves = List.copyOf(waves);
    }

    /**
     * One wave as each Death Wall stage is announced, taking the sites that stay inside the zone that
     * stage closes to first; the site nearest that zone's centre opens as a set when it has room.
     */
    static List<Wave> plan(List<Pos> anchors, RavengardDeathWall.Timeline timeline,
                           Function<Pos, List<Pos>> setAt) {
        List<Pos> remaining = new ArrayList<>(anchors);
        List<Wave> waves = new ArrayList<>();
        for (int stage = 1; stage <= RavengardDeathWall.STAGE_COUNT && !remaining.isEmpty(); stage++) {
            RavengardDeathWall.Center center = timeline.targetCenter(stage);
            double radius = timeline.targetRadius(stage);
            remaining.sort(Comparator
                    .comparing((Pos anchor) -> !inside(anchor, center, radius))
                    .thenComparingDouble(anchor -> distance(anchor, center)));
            List<Site> sites = new ArrayList<>();
            for (int index = 0; index < SITES_PER_WAVE && !remaining.isEmpty(); index++) {
                Pos anchor = remaining.removeFirst();
                List<Pos> set = index == 0 ? setAt.apply(anchor) : List.of();
                sites.add(new Site(anchor, set.isEmpty() ? List.of(anchor) : set));
            }
            waves.add(new Wave(RavengardDeathWall.stageWarningAt(stage), sites));
        }
        return waves;
    }

    static boolean inside(Pos anchor, RavengardDeathWall.Center center, double radius) {
        return !RavengardDeathWall.isOutside(anchor.x(), anchor.z(), center.x(), center.z(), radius);
    }

    private static double distance(Pos anchor, RavengardDeathWall.Center center) {
        return Math.max(Math.abs(anchor.x() - center.x()), Math.abs(anchor.z() - center.z()));
    }

    /** Three arches side by side across the portal's facing, when the floor and headroom allow it. */
    static List<Pos> setAround(Pos anchor, DungeonInstanceRegistry.BlockLookup blocks) {
        boolean alongX = Math.floorMod(Math.round(anchor.yaw()), 180) == 0;
        int x = anchor.blockX();
        int z = anchor.blockZ();
        int feetY = (int) Math.floor(anchor.y());
        for (int[] offsets : new int[][]{{-SET_SPACING, SET_SPACING}, {SET_SPACING, 2 * SET_SPACING},
                {-2 * SET_SPACING, -SET_SPACING}}) {
            List<Pos> set = new ArrayList<>(SET_SIZE);
            set.add(anchor);
            for (int offset : offsets) {
                int px = x + (alongX ? offset : 0);
                int pz = z + (alongX ? 0 : offset);
                if (!DungeonInstanceRegistry.portalFitsAt(blocks, px, feetY, pz, alongX)) break;
                set.add(anchor.add(alongX ? offset : 0, 0, alongX ? 0 : offset));
            }
            if (set.size() == SET_SIZE) {
                set.sort(Comparator.comparingDouble(Pos::x).thenComparingDouble(Pos::z));
                return List.copyOf(set);
            }
        }
        return List.of();
    }

    void tick(long elapsedSeconds) {
        while (nextWave < waves.size() && elapsedSeconds >= waves.get(nextWave).opensAt()) {
            openWave(waves.get(nextWave++));
        }
    }

    private void openWave(Wave wave) {
        for (Site site : wave.sites()) {
            for (Pos portal : site.portals()) {
                open.add(DungeonExtraction.spawn(instance, game, portal, this::used));
                RavengardSounds.play(instance, portal, RavengardSounds.Cue.PORTAL_SPAWN);
            }
            org.tinylog.Logger.info("Ravengard escape portals opened: game={} anchor=({}, {}, {}) portals={}",
                    game.getGameId(), site.anchor().x(), site.anchor().y(), site.anchor().z(), site.portals().size());
        }
        int sets = (int) wave.sites().stream().filter(Site::isSet).count();
        int singles = wave.sites().size() - sets;
        String message = sets > 0 && singles > 0
                ? "<b>" + singles + " 道撤离传送门和 " + sets + " 组三联撤离传送门已在地牢中开启！"
                : sets > 0 ? "<b>" + sets + " 组三联撤离传送门已在地牢中开启，全队可一同撤离！"
                : "<b>" + singles + " 道撤离传送门已在地牢中开启！";
        game.announcePortals(message);
    }

    void used(DungeonExtraction portal) {
        if (!open.remove(portal)) return;
        RavengardSounds.play(instance, portal.anchor(), RavengardSounds.Cue.PORTAL_CLOSE);
        portal.remove();
        org.tinylog.Logger.info("Ravengard escape portal closed after use: game={} at=({}, {}, {}) stillOpen={}",
                game.getGameId(), portal.anchor().x(), portal.anchor().y(), portal.anchor().z(), open.size());
        game.portalsChanged();
    }

    int openCount() {
        return open.size();
    }

    List<Pos> openAnchors() {
        return open.stream().map(DungeonExtraction::anchor).toList();
    }

    void close() {
        open.forEach(DungeonExtraction::remove);
        open.clear();
        nextWave = waves.size();
    }
}
