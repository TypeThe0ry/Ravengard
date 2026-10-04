package net.swofty.type.ravengardgeneric.sound;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

public final class RavengardSounds {
    public enum Cue {
        DOOR_CHANNEL("feature.door.interact", Sound.Source.BLOCK, 1.0f, 1.0f),
        DOOR_OPEN("feature.door.open", Sound.Source.BLOCK, 1.0f, 1.0f),
        DOOR_CLOSE("feature.door.close", Sound.Source.BLOCK, 1.0f, 1.0f),
        LOOT_OPEN("ui.open", Sound.Source.PLAYER, 1.0f, 1.0f),
        LOOT_READY("ui.clicks.good_click", Sound.Source.PLAYER, 1.0f, 1.0f),
        MOB_ATTACK("feature.combat.unblockable", Sound.Source.HOSTILE, 0.8f, 1.0f),
        MOB_HURT("action.impact", Sound.Source.HOSTILE, 0.8f, 1.0f),
        MOB_DEATH("action.braam", Sound.Source.HOSTILE, 0.8f, 1.1f),
        EXTRACTION_CHANNEL("feature.portal.exit.ambience", Sound.Source.BLOCK, 0.8f, 1.0f),
        EXTRACTION_SUCCESS("feature.portal.exit.spawn", Sound.Source.PLAYER, 1.0f, 1.0f),
        EXTRACTION_FAIL("feature.portal.exit.close", Sound.Source.PLAYER, 1.0f, 1.0f),
        PORTAL_SPAWN("feature.portal.exit.spawn", Sound.Source.BLOCK, 1.0f, 1.0f),
        PORTAL_CLOSE("feature.portal.exit.close", Sound.Source.BLOCK, 1.0f, 1.0f),
        REVIVE("action.revive", Sound.Source.PLAYER, 1.0f, 1.0f),
        MATCH_COUNTDOWN("action.alert.timpani", Sound.Source.PLAYER, 0.8f, 1.0f),
        MATCH_START("music.game.start", Sound.Source.MUSIC, 1.0f, 1.0f),
        DUNGEON_AMBIENCE("music.game.dungeon_ambience", Sound.Source.MUSIC, 0.8f, 1.0f),
        DEATH_WALL_WARNING("action.riser", Sound.Source.PLAYER, 0.8f, 1.0f),
        DEATH_WALL_START("action.alert.dramatic", Sound.Source.PLAYER, 1.0f, 1.0f);

        private final String key;
        private final Sound sound;

        Cue(String key, Sound.Source source, float volume, float pitch) {
            this.key = key;
            this.sound = Sound.sound(Key.key("hypixel_ravengard", key), source, volume, pitch);
        }

        public String key() {
            return key;
        }

        public Sound sound() {
            return sound;
        }
    }

    private RavengardSounds() {
    }

    public static void play(Player player, Cue cue) {
        if (player == null || !player.isOnline()) return;
        player.playSound(cue.sound(), Sound.Emitter.self());
    }

    public static void play(Instance instance, Point point, Cue cue) {
        if (instance == null || point == null) return;
        instance.playSound(cue.sound(), point.x(), point.y(), point.z());
    }

    public static void stop(Player player, Cue cue) {
        if (player == null || !player.isOnline()) return;
        player.stopSound(SoundStop.namedOnSource(
                Key.key("hypixel_ravengard", cue.key()), cue.sound().source()));
    }
}
