package net.swofty.type.ravengarddungeon.interactables;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig;
import net.swofty.type.ravengarddungeon.game.RavengardDungeonGame;
import net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * A Revival Statue from the official map markers: a living mercenary channels at it to bring
 * fallen teammates back, who return in front of the statue with only their class kit.
 */
public final class DungeonRevivalStatue extends DungeonInteractable {
    static final String MARKER_TYPE = "resurrection.resurrection_altar";
    static final String MODEL = "hypixel_ravengard:item/gameplay/altar/resurrection/resurrection_altar";
    static final float INTERACTION_WIDTH = 1.6f;
    static final float INTERACTION_HEIGHT = 2.6f;
    static final double MARKER_FLOOR_OFFSET = 1.5;
    static final double REVIVE_DISTANCE = 1.8;

    private final RavengardDungeonGame game;
    private final Pos marker;

    private DungeonRevivalStatue(Instance instance, RavengardDungeonGame game, Pos marker) {
        super(instance);
        this.game = game;
        this.marker = marker;
        this.interaction = spawnInteraction(marker.sub(0, MARKER_FLOOR_OFFSET, 0), INTERACTION_WIDTH, INTERACTION_HEIGHT);
        Entity display = spawnDisplay(marker, MODEL);
        InteractableRegistry.register(this);
        InteractableRegistry.registerExtraInteraction(this, display);
    }

    public static int spawnAll(Instance instance, RavengardDungeonGame game,
                               List<RavengardDungeonConfig.DungeonObject> objects) {
        int spawned = 0;
        for (Pos marker : markers(objects)) {
            new DungeonRevivalStatue(instance, game, marker);
            spawned++;
        }
        return spawned;
    }

    static List<Pos> markers(List<RavengardDungeonConfig.DungeonObject> objects) {
        List<Pos> markers = new ArrayList<>();
        for (RavengardDungeonConfig.DungeonObject object : objects) {
            if (!MARKER_TYPE.equals(object.type())) continue;
            Pos marker = new Pos(object.x(), object.y(), object.z(), ((object.yaw() % 360f) + 360f) % 360f, 0f);
            if (markers.stream().noneMatch(existing -> existing.samePoint(marker))) markers.add(marker);
        }
        return markers;
    }

    /** Where revived mercenaries stand: on the floor in front of the statue, facing away from it. */
    static Pos revivePoint(Pos marker) {
        double radians = Math.toRadians(marker.yaw());
        return new Pos(marker.x() - Math.sin(radians) * REVIVE_DISTANCE,
                marker.y() - MARKER_FLOOR_OFFSET,
                marker.z() + Math.cos(radians) * REVIVE_DISTANCE,
                marker.yaw(), 0f);
    }

    @Override
    public int channelTicks() {
        return 60;
    }

    @Override
    public String castLabel() {
        return "Reviving";
    }

    @Override
    public boolean allowReactivation() {
        return true;
    }

    @Override
    public boolean channelStillValid(Player player) {
        return player instanceof RavengardDungeonPlayer mercenary && game.hasFallenTeammates(mercenary);
    }

    @Override
    public void channelRejected(Player player) {
        if (player instanceof RavengardDungeonPlayer mercenary) {
            mercenary.sendMessage("<c>None of your teammates need reviving.");
        }
    }

    @Override
    public void open(Player player) {
        if (player instanceof RavengardDungeonPlayer mercenary) {
            game.reviveTeammates(mercenary, revivePoint(marker));
        }
    }
}
