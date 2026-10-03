package net.swofty.type.ravengarddungeon.interactables;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.MinecraftServer;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import net.swofty.type.ravengarddungeon.game.RavengardDungeonGame;
import net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer;
import net.swofty.type.ravengardgeneric.profile.RavengardRunState;

/** The interaction at an official cyan dungeon room. */
public final class DungeonExtraction extends DungeonInteractable {
    static final String MODEL = "hypixel_ravengard:item/gameplay/portals/exit_portal";
    static final double MODEL_FLOOR_OFFSET = 1.228264169;
    static final double MODEL_X_OFFSET = 0.040813045;
    static final double MODEL_Z_OFFSET = 0.018987701;
    static final float INTERACTION_WIDTH = 2.0f;
    static final float INTERACTION_HEIGHT = 2.8f;
    static final double CHANNEL_HORIZONTAL_DISTANCE = InteractableRegistry.AIM_REACH + INTERACTION_WIDTH / 2.0;
    static final double CHANNEL_VERTICAL_DISTANCE = 3.0;

    private final RavengardDungeonGame game;
    private final Entity display;
    private final java.util.Set<java.util.UUID> channelers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private Task animationTask;

    private DungeonExtraction(Instance instance, RavengardDungeonGame game, Pos position) {
        super(instance);
        this.game = game;
        this.interaction = spawnInteraction(position, INTERACTION_WIDTH, INTERACTION_HEIGHT);
        this.display = spawnDisplay(modelPose(position), MODEL);
    }

    static double[] rotatedModelOffset(float yaw) {
        double yawRadians = Math.toRadians(yaw);
        double cos = Math.cos(yawRadians);
        double sin = Math.sin(yawRadians);
        return new double[] {
                MODEL_X_OFFSET * cos - MODEL_Z_OFFSET * sin,
                MODEL_X_OFFSET * sin + MODEL_Z_OFFSET * cos
        };
    }

    static Pos modelPose(Pos anchor) {
        double[] offset = rotatedModelOffset(anchor.yaw());
        return anchor.add(offset[0], MODEL_FLOOR_OFFSET, offset[1])
                .withYaw(anchor.yaw())
                .withPitch(0f);
    }

    public static DungeonExtraction spawn(Instance instance, RavengardDungeonGame game, Pos position) {
        DungeonExtraction extraction = new DungeonExtraction(instance, game, position);
        InteractableRegistry.register(extraction);
        InteractableRegistry.registerExtraInteraction(extraction, extraction.display);
        org.tinylog.Logger.info(
                "Ravengard extraction ready: anchor=({}, {}, {}) yaw={} model=({}, {}, {}) modelYaw={} interaction=({}, {}, {}) offset=({}, {}) channel=3.0s",
                position.x(), position.y(), position.z(),
                position.yaw(),
                extraction.display.getPosition().x(), extraction.display.getPosition().y(),
                extraction.display.getPosition().z(),
                extraction.display.getPosition().yaw(),
                extraction.interaction.getPosition().x(), extraction.interaction.getPosition().y(),
                extraction.interaction.getPosition().z(),
                extraction.display.getPosition().x() - position.x(),
                extraction.display.getPosition().z() - position.z());
        org.tinylog.Logger.info("Ravengard extraction display context: {}",
                ((ItemDisplayMeta) extraction.display.getEntityMeta()).getDisplayContext());
        return extraction;
    }

    @Override
    public String castLabel() {
        return "Extracting";
    }

    @Override
    public boolean matchesVisibleTarget(Entity target) {
        return display == target;
    }

    @Override
    public int channelTicks() {
        return 60;
    }

    @Override
    public boolean channelStillValid(Player player) {
        if (!(player instanceof RavengardDungeonPlayer dungeonPlayer)
                || player.getInstance() != instance
                || game.getState() != net.swofty.type.game.game.GameState.IN_PROGRESS
                || dungeonPlayer.isDungeonDead()
                || !RavengardRunState.isActive(player.getUuid(), game.getGameId())) {
            return false;
        }
        Pos anchor = interaction.getPosition();
        Pos current = player.getPosition();
        double dx = current.x() - anchor.x();
        double dz = current.z() - anchor.z();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double verticalDistance = Math.abs(current.y() - anchor.y());
        return horizontalDistance <= CHANNEL_HORIZONTAL_DISTANCE
                && verticalDistance <= CHANNEL_VERTICAL_DISTANCE;
    }

    @Override
    public void channelStarted(Player player) {
        net.swofty.type.ravengardgeneric.sound.RavengardSounds.play(
                player, net.swofty.type.ravengardgeneric.sound.RavengardSounds.Cue.EXTRACTION_CHANNEL);
        channelers.add(player.getUuid());
        if (animationTask != null) return;
        animationTask = MinecraftServer.getSchedulerManager().buildTask(() -> {
            if (removed || !isActive(display)) {
                stopAnimation();
                return;
            }
            Pos center = display.getPosition().add(0, -0.2, 0);
            instance.sendGroupedPacket(new ParticlePacket(
                    Particle.PORTAL,
                    center.x(), center.y(), center.z(),
                    0.72f, 0.95f, 0.72f, 0.08f, 10));
        }).repeat(TaskSchedule.tick(2)).schedule();
    }

    @Override
    public void channelCancelled(Player player) {
        net.swofty.type.ravengardgeneric.sound.RavengardSounds.stop(
                player, net.swofty.type.ravengardgeneric.sound.RavengardSounds.Cue.EXTRACTION_CHANNEL);
        stopChanneling(player);
    }

    @Override
    public void open(Player player) {
        stopChanneling(player);
        if (game.getState() == net.swofty.type.game.game.GameState.IN_PROGRESS
                && player instanceof RavengardDungeonPlayer dungeonPlayer) {
            game.extract(dungeonPlayer);
        }
    }

    @Override
    public void channelAbandoned(java.util.UUID playerUuid) {
        channelers.remove(playerUuid);
        if (channelers.isEmpty()) stopAnimation();
    }

    private void stopChanneling(Player player) {
        channelAbandoned(player.getUuid());
    }

    private void stopAnimation() {
        channelers.clear();
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
    }

    @Override
    public void remove() {
        stopAnimation();
        if (display != null && !display.isRemoved()) display.remove();
        super.remove();
    }
}
