package net.swofty.type.ravengarddungeon.interactables;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.timer.TaskSchedule;
import net.swofty.commons.text.Text;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class InteractableRegistry {
    private static final double MAX_DISTANCE = 6.0;
    private static final Map<Integer, DungeonInteractable> BY_INTERACTION = new ConcurrentHashMap<>();
    private static final Map<UUID, Channel> CHANNELS = new ConcurrentHashMap<>();
    private static final Map<UUID, BossBar> BARS = new ConcurrentHashMap<>();
    private static final Map<Inventory, CloseHandler> CLOSE_HANDLERS = new ConcurrentHashMap<>();
    private static final Set<UUID> HELD_RIGHT_CLICK = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, DungeonDoor> DOOR_CLICK_LATCHES = new ConcurrentHashMap<>();
    private static final double DOOR_REARM_DISTANCE = 3.0;
    static final int UNVIEWED_CLOSE_TICKS = 40;
    private static volatile java.util.function.Predicate<Player> accessGate = player -> true;

    public static void setAccessGate(java.util.function.Predicate<Player> gate) {
        accessGate = gate;
    }

    private record Channel(DungeonInteractable target, float progress) {
    }

    private static final class CloseHandler {
        private final DungeonInteractable target;
        private final Consumer<Player> handler;
        private boolean wasViewed;
        private int unviewedTicks;

        private CloseHandler(DungeonInteractable target, Consumer<Player> handler) {
            this.target = target;
            this.handler = handler;
        }
    }

    private InteractableRegistry() {
    }

    public static void register(DungeonInteractable interactable) {
        BY_INTERACTION.put(interactable.getInteraction().getEntityId(), interactable);
    }

    public static void registerExtraInteraction(DungeonInteractable interactable, Entity extra) {
        BY_INTERACTION.put(extra.getEntityId(), interactable);
    }

    public static DungeonInteractable byInteraction(Entity entity) {
        return entity == null ? null : BY_INTERACTION.get(entity.getEntityId());
    }

    public static void unregister(DungeonInteractable interactable) {
        BY_INTERACTION.entrySet().removeIf(entry -> entry.getValue() == interactable);
        CHANNELS.entrySet().removeIf(entry -> {
            if (entry.getValue().target() != interactable) return false;
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(entry.getKey());
            if (player != null) hideBar(player);
            else BARS.remove(entry.getKey());
            return true;
        });
        DOOR_CLICK_LATCHES.entrySet().removeIf(entry -> entry.getValue() == interactable);
        CLOSE_HANDLERS.entrySet().removeIf(entry -> entry.getValue().target == interactable);
    }

    public static void onClick(Player player, Entity target) {
        DungeonInteractable interactable = BY_INTERACTION.get(target == null ? -1 : target.getEntityId());
        if (interactable == null
                || (interactable.isOpened() && !interactable.allowReactivation())) {
            return;
        }
        if (interactable.requiresHeldRightClick()) {
            HELD_RIGHT_CLICK.add(player.getUuid());
            player.refreshItemUse(net.minestom.server.entity.PlayerHand.MAIN, interactable.channelTicks() + 20L);
        }
        org.tinylog.Logger.debug("Interact resolved: player={} class={} target={} distance={}",
                player.getUsername(), interactable.getClass().getSimpleName(),
                target.getPosition(), player.getPosition().distance(target.getPosition()));
        startChannel(player, interactable);
    }

    public static void onUse(Player player) {
        onUse(player, null);
    }

    public static void onUse(Player player, Entity visibleTarget) {
        if (player == null) return;
        DungeonInteractable nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (DungeonInteractable candidate : Set.copyOf(BY_INTERACTION.values())) {
            if (!(candidate instanceof DungeonExtraction)
                    && !(candidate instanceof DungeonChest)
                    && !(candidate instanceof DungeonDoor)
                    && !(candidate instanceof DungeonSatchel)
                    || candidate.getInteraction().getInstance() != player.getInstance()) {
                continue;
            }
            double distance;
            double reach;
            if (visibleTarget != null) {
                if (!candidate.matchesVisibleTarget(visibleTarget)) continue;
                distance = player.getPosition().distance(candidate.getInteraction().getPosition());
                reach = MAX_DISTANCE;
            } else {
                distance = aimDistance(player, candidate.getInteraction());
                if (distance < 0) continue;
                reach = AIM_REACH;
            }
            if (distance <= reach && distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        if (nearest != null && visibleTarget == null && mobBlocksAim(player, nearestDistance)) {
            nearest = null;
        }
        if (nearest != null) {
            org.tinylog.Logger.debug("Interact proximity use: player={} class={} target={} distance={}",
                    player.getUsername(), nearest.getClass().getSimpleName(),
                    nearest.getInteraction().getPosition(), nearestDistance);
            startChannel(player, nearest);
        }
    }

    // barrier cells around chests and doors can swallow the client's entity ray, so a
    // right-click on air or a block falls back to the interactable the player aims at
    static final double AIM_TOLERANCE = 0.5;
    static final double AIM_REACH = 4.5;

    private static boolean mobBlocksAim(Player player, double interactableDistance) {
        Pos eye = player.getPosition().add(0, player.getEyeHeight(), 0);
        var direction = player.getPosition().direction();
        for (Entity entity : player.getInstance().getNearbyEntities(player.getPosition(), interactableDistance + 2)) {
            if (net.swofty.type.ravengardgeneric.entity.mob.RavengardMob.combatTarget(entity) == null) continue;
            var box = entity.getBoundingBox();
            Pos at = entity.getPosition();
            double hit = rayBoxDistance(eye, direction,
                    at.x() + box.minX(), at.y() + box.minY(), at.z() + box.minZ(),
                    at.x() + box.maxX(), at.y() + box.maxY(), at.z() + box.maxZ());
            if (hit >= 0 && hit < interactableDistance) return true;
        }
        return false;
    }

    static double aimDistance(Player player, Entity target) {
        Pos eye = player.getPosition().add(0, player.getEyeHeight(), 0);
        var box = target.getBoundingBox();
        Pos at = target.getPosition();
        return rayBoxDistance(eye, player.getPosition().direction(),
                at.x() + box.minX() - AIM_TOLERANCE, at.y() + box.minY() - AIM_TOLERANCE,
                at.z() + box.minZ() - AIM_TOLERANCE,
                at.x() + box.maxX() + AIM_TOLERANCE, at.y() + box.maxY() + AIM_TOLERANCE,
                at.z() + box.maxZ() + AIM_TOLERANCE);
    }

    static double rayBoxDistance(net.minestom.server.coordinate.Point origin,
                                 net.minestom.server.coordinate.Vec direction,
                                 double minX, double minY, double minZ,
                                 double maxX, double maxY, double maxZ) {
        double near = 0;
        double far = Double.MAX_VALUE;
        double[] start = {origin.x(), origin.y(), origin.z()};
        double[] dir = {direction.x(), direction.y(), direction.z()};
        double[] min = {minX, minY, minZ};
        double[] max = {maxX, maxY, maxZ};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dir[axis]) < 1.0e-9) {
                if (start[axis] < min[axis] || start[axis] > max[axis]) return -1;
                continue;
            }
            double t1 = (min[axis] - start[axis]) / dir[axis];
            double t2 = (max[axis] - start[axis]) / dir[axis];
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
            if (near > far) return -1;
        }
        return near;
    }

    public static void startChannel(Player player, DungeonInteractable interactable) {
        if (player == null || interactable == null
                || (interactable.isOpened() && !interactable.allowReactivation())
                || !accessGate.test(player)) {
            return;
        }
        if (!interactable.channelStillValid(player)) {
            interactable.channelRejected(player);
            return;
        }
        Channel current = CHANNELS.get(player.getUuid());
        if (current != null && current.target() == interactable) return;
        if (interactable instanceof DungeonDoor door
                && !tryDoorClickEdge(player.getUuid(), door, player.getPosition())) return;
        CHANNELS.put(player.getUuid(), new Channel(interactable, 0f));
        bar(player, interactable, 0f);
        interactable.channelStarted(player);
    }

    public static void onRightClickReleased(Player player) {
        if (player == null) return;
        DOOR_CLICK_LATCHES.remove(player.getUuid());
        if (!HELD_RIGHT_CLICK.remove(player.getUuid())) return;
        Channel channel = CHANNELS.get(player.getUuid());
        if (channel != null && channel.target().requiresHeldRightClick()) {
            CHANNELS.remove(player.getUuid());
            hideBar(player);
            channel.target().channelCancelled(player);
        }
    }

    public static void clearPlayer(Player player) {
        if (player == null) return;
        HELD_RIGHT_CLICK.remove(player.getUuid());
        DOOR_CLICK_LATCHES.remove(player.getUuid());
        Channel channel = CHANNELS.remove(player.getUuid());
        hideBar(player);
        if (channel != null) channel.target().channelCancelled(player);
    }

    static boolean tryDoorClickEdge(UUID playerId, DungeonDoor door, Pos playerPosition) {
        DungeonDoor latched = DOOR_CLICK_LATCHES.get(playerId);
        if (latched != null && latched == door
                && playerPosition.distance(door.getInteraction().getPosition()) <= DOOR_REARM_DISTANCE) {
            return false;
        }
        if (latched != null) DOOR_CLICK_LATCHES.remove(playerId, latched);
        DOOR_CLICK_LATCHES.put(playerId, door);
        return true;
    }

    public static void watchClose(Inventory inventory, DungeonInteractable target, Consumer<Player> handler) {
        CLOSE_HANDLERS.put(inventory, new CloseHandler(target, handler));
    }

    public static void onInventoryClose(Player player, Inventory inventory) {
        CloseHandler handler = inventory == null ? null : CLOSE_HANDLERS.remove(inventory);
        if (handler != null) {
            handler.handler.accept(player);
        }
    }

    public static void removeInstance(net.minestom.server.instance.Instance instance) {
        Set<DungeonInteractable> interactables = ConcurrentHashMap.newKeySet();
        for (DungeonInteractable interactable : BY_INTERACTION.values()) {
            if (interactable.instance == instance) interactables.add(interactable);
        }
        interactables.forEach(DungeonInteractable::remove);
        CLOSE_HANDLERS.entrySet().removeIf(entry -> entry.getValue().target.instance == instance);
    }

    public static void startTask() {
        MinecraftServer.getSchedulerManager().buildTask(InteractableRegistry::tick)
                .repeat(TaskSchedule.tick(1)).schedule();
    }

    private static void tick() {
        for (Map.Entry<Inventory, CloseHandler> entry : CLOSE_HANDLERS.entrySet()) {
            Inventory inventory = entry.getKey();
            CloseHandler handler = entry.getValue();
            if (!inventory.getViewers().isEmpty()) {
                handler.wasViewed = true;
                continue;
            }
            boolean abandoned = !handler.wasViewed && ++handler.unviewedTicks > UNVIEWED_CLOSE_TICKS;
            if ((handler.wasViewed || abandoned) && CLOSE_HANDLERS.remove(inventory, handler)) {
                handler.handler.accept(null);
            }
        }
        for (Map.Entry<UUID, Channel> entry : CHANNELS.entrySet()) {
            Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(entry.getKey());
            Channel channel = entry.getValue();
            if (player == null
                    || (channel.target().isOpened() && !channel.target().allowReactivation())
                    || (channel.target().requiresHeldRightClick()
                    && !HELD_RIGHT_CLICK.contains(entry.getKey()))
                    || !channel.target().channelStillValid(player)
                    || !accessGate.test(player)
                    || (channel.target().requiresLookingDuringChannel()
                    && !isLookingAt(player, channel.target()))) {
                org.tinylog.Logger.debug("Channel cancel: player={} opened={} valid={} looking={}",
                        player != null,
                        channel.target().isOpened(),
                        player != null && channel.target().channelStillValid(player),
                        player != null && (!channel.target().requiresLookingDuringChannel()
                                || isLookingAt(player, channel.target())));
                CHANNELS.remove(entry.getKey());
                HELD_RIGHT_CLICK.remove(entry.getKey());
                if (player != null) {
                    hideBar(player);
                    channel.target().channelCancelled(player);
                }
                continue;
            }
            float progress = channel.progress() + 1f / channel.target().channelTicks();
            if (progress >= 1f) {
                CHANNELS.remove(entry.getKey());
                HELD_RIGHT_CLICK.remove(entry.getKey());
                hideBar(player);
                org.tinylog.Logger.debug("Interact open: player={} class={} target={}",
                        player.getUsername(), channel.target().getClass().getSimpleName(),
                        channel.target().getInteraction().getPosition());
                channel.target().open(player);
            } else {
                CHANNELS.put(entry.getKey(), new Channel(channel.target(), progress));
                bar(player, channel.target(), progress);
            }
        }
    }

    private static boolean isLookingAt(Player player, DungeonInteractable interactable) {
        Entity interaction = interactable.getInteraction();
        if (interaction.getInstance() != player.getInstance()) {
            return false;
        }
        var meta = (net.minestom.server.entity.metadata.other.InteractionMeta) interaction.getEntityMeta();
        Pos base = interaction.getPosition();
        Vec center = new Vec(base.x(), base.y() + meta.getHeight() / 2.0, base.z());
        Vec eye = new Vec(player.getPosition().x(),
                player.getPosition().y() + player.getEyeHeight(),
                player.getPosition().z());
        Vec toCenter = center.sub(eye);
        double distance = toCenter.length();
        if (distance > MAX_DISTANCE) {
            return false;
        }
        Vec direction = player.getPosition().direction();
        double along = toCenter.dot(direction);
        if (along < -1.0) {
            return false;
        }
        Vec closest = eye.add(direction.mul(Math.max(0, along)));
        double miss = closest.sub(center).length();
        double radius = Math.max(meta.getWidth(), meta.getHeight()) / 2.0 + 1.4;
        return miss <= radius;
    }

    private static void bar(Player player, DungeonInteractable target, float progress) {
        BossBar bar = BARS.computeIfAbsent(player.getUuid(), ignored -> {
            BossBar created = BossBar.bossBar(Component.empty(), 0f,
                    BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
            player.showBossBar(created);
            return created;
        });
        bar.name(Text.of("<#fefd02>{}", target.castLabel()));
        bar.progress(Math.min(1f, progress));
    }

    private static void hideBar(Player player) {
        BossBar bar = BARS.remove(player.getUuid());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }
}
