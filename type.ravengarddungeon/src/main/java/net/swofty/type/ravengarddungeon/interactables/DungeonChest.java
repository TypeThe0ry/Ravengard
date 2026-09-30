package net.swofty.type.ravengarddungeon.interactables;

import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.timer.TaskSchedule;
import net.swofty.type.generic.gui.inventory.Inventories;
import net.swofty.type.ravengardgeneric.entity.animation.RavengardMobClip;
import net.swofty.type.ravengardgeneric.entity.mob.RavengardMob;
import net.swofty.type.ravengardgeneric.sound.RavengardSounds;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class DungeonChest extends DungeonInteractable {
    private static final String TITLE_GLYPHS = new String(Character.toChars(0xCFFD0)) + "";
    private static final int LID_TICKS = 5;
    private static final net.minestom.server.tag.Tag<Boolean> CONVERTED =
            net.minestom.server.tag.Tag.Boolean("ravengard_chest_converted").defaultValue(false);
    private static final int MIMIC_ROLL_BOUND = 1_000;
    private static final int NORMAL_MIMIC_CHANCE = 50;
    private static final int SILVER_MIMIC_CHANCE = 100;
    private static final int GOLD_MIMIC_CHANCE = 120;
    private static final List<String> BOTTLE_MIMIC_POOL = List.of(
            "bottle_mimic_short_yellow", "bottle_mimic_short_green", "bottle_mimic_short_red",
            "bottle_mimic_tall_aqua", "bottle_mimic_tall_green", "bottle_mimic_tall_pink");
    private static final int[] LOOT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 22, 24};

    private final Entity bottom;
    private final Entity lid;
    private final Pos base;
    private final Random random;
    private final String tier;
    private final Set<Vec> collisionCells;

    private DungeonChest(Instance instance, Pos base, Random random, String tier,
                         Entity bottom, Entity lid) {
        super(instance);
        this.base = base;
        this.random = random;
        this.tier = tier;
        this.bottom = bottom;
        this.lid = lid;
        this.collisionCells = new HashSet<>(captureCollisionCells(instance, base));
        this.interaction = spawnInteraction(base.sub(0, 0.5, 0), 1.3f, 1.2f);
    }

    private static Set<Vec> captureCollisionCells(Instance instance, Pos base) {
        Set<Vec> cells = new HashSet<>();
        int centerX = base.blockX();
        int centerZ = base.blockZ();
        int fromY = (int) Math.floor(base.y() - 0.5);
        int toY = (int) Math.floor(base.y() + 1.7);
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                for (int y = fromY; y <= toY; y++) {
                    var point = new Vec(x, y, z);
                    if (blockAtLoaded(instance, x, y, z)
                            == net.minestom.server.instance.block.Block.BARRIER
                            && !DungeonDoor.isBarrierManaged(instance, point)) {
                        cells.add(point);
                    }
                }
            }
        }
        return Set.copyOf(cells);
    }

    public static int convertDecorChests(Instance instance, long seed) {
        List<Entity> bottoms = new ArrayList<>();
        List<Entity> tops = new ArrayList<>();
        java.util.Set<String> seenBottoms = new java.util.HashSet<>();
        java.util.Set<String> seenTops = new java.util.HashSet<>();
        for (Entity entity : instance.getEntities()) {
            if (!entity.getTag(CONVERTED)) continue;
            String model = modelOf(entity);
            if (model == null) continue;
            if (model.endsWith("_bottom")) seenBottoms.add(displayKey(entity));
            else if (model.endsWith("_top")) seenTops.add(displayKey(entity));
        }
        for (Entity entity : instance.getEntities()) {
            String model = modelOf(entity);
            if (model == null || !model.contains(":item/gameplay/chests/")) continue;
            if (entity.getTag(CONVERTED)) continue;
            String key = displayKey(entity);
            if (model.endsWith("_bottom")) {
                if (seenBottoms.add(key)) bottoms.add(entity);
                else entity.remove();
            } else if (model.endsWith("_top")) {
                if (seenTops.add(key)) tops.add(entity);
                else entity.remove();
            }
        }
        int converted = 0;
        for (Entity bottom : bottoms) {
            Entity top = null;
            double nearest = 1.1 * 1.1;
            String bottomModel = modelOf(bottom);
            String expectedTop = bottomModel.substring(0, bottomModel.length() - "_bottom".length()) + "_top";
            for (Entity candidate : tops) {
                if (!expectedTop.equals(modelOf(candidate))) continue;
                double distance = candidate.getPosition().distanceSquared(bottom.getPosition());
                if (distance <= nearest) {
                    top = candidate;
                    nearest = distance;
                }
            }
            if (top == null) continue;
            tops.remove(top);
            bottom.setTag(CONVERTED, true);
            top.setTag(CONVERTED, true);
            String model = modelOf(bottom);
            String tier = model.contains("/silver") ? "silver"
                    : model.contains("/gold") ? "gold" : "normal";
            Pos base = bottom.getPosition();
            DungeonChest chest = new DungeonChest(instance, base,
                    new Random(seed ^ base.hashCode()), tier, bottom, top);
            InteractableRegistry.register(chest);
            InteractableRegistry.registerExtraInteraction(chest, bottom);
            InteractableRegistry.registerExtraInteraction(chest, top);
            converted++;
        }
        return converted;
    }

    private static String displayKey(Entity entity) {
        Pos position = entity.getPosition();
        return modelOf(entity) + String.format(java.util.Locale.ROOT, ":%.3f:%.3f:%.3f",
                position.x(), position.y(), position.z());
    }

    private static String modelOf(Entity entity) {
        if (!(entity.getEntityMeta() instanceof ItemDisplayMeta meta)) return null;
        var stack = meta.getItemStack();
        if (stack == null || stack.isAir()) return null;
        var model = stack.get(DataComponents.ITEM_MODEL);
        return model == null ? null : model.toString();
    }

    @Override
    public String castLabel() {
        return "Opening Chest";
    }

    @Override
    public boolean matchesVisibleTarget(Entity target) {
        return target == bottom || target == lid;
    }

    @Override
    public void open(Player player) {
        if (player == null || opened || removed || !isActive(lid) || !player.isOnline() || player.getInstance() != instance) {
            org.tinylog.Logger.warn("Ravengard chest open rejected: player={} base={}",
                    player == null ? "<null>" : player.getUsername(), base);
            return;
        }
        opened = true;
        String mimicType = rollMimic(random, tier);
        if (mimicType != null) {
            revealMimic(player, mimicType);
            return;
        }
        openLoot(player);
    }

    static String rollMimic(Random random, String tier) {
        int chance = switch (tier) {
            case "silver" -> SILVER_MIMIC_CHANCE;
            case "gold" -> GOLD_MIMIC_CHANCE;
            default -> NORMAL_MIMIC_CHANCE;
        };
        if (random.nextInt(MIMIC_ROLL_BOUND) >= chance) return null;
        if ("silver".equals(tier)) return "silver_mimic";
        if ("gold".equals(tier) && random.nextBoolean()) return "silver_mimic";
        return BOTTLE_MIMIC_POOL.get(random.nextInt(BOTTLE_MIMIC_POOL.size()));
    }

    private void revealMimic(Player player, String mimicType) {
        RavengardMobClip clip;
        try {
            clip = RavengardMobClip.load(mimicType.startsWith("bottle_mimic_")
                    ? "bottle_mimic_short_yellow" : mimicType);
        } catch (RuntimeException exception) {
            org.tinylog.Logger.warn("Mimic clip {} could not be loaded; opening chest normally: {}",
                    mimicType, exception.getMessage());
            openLoot(player);
            return;
        }

        Pos playerPosition = player.getPosition();
        double dx = playerPosition.x() - base.x();
        double dz = playerPosition.z() - base.z();
        float yaw = dx * dx + dz * dz < 1.0e-6
                ? base.yaw() : (float) Math.toDegrees(Math.atan2(-dx, dz));
        Pos spawn = base.sub(0, 0.5, 0).withYaw(yaw).withPitch(0);
        remove();
        instance.sendGroupedPacket(new ParticlePacket(Particle.POOF,
                base.x(), base.y(), base.z(), 0.55f, 0.55f, 0.55f, 0.05f, 36));
        instance.playSound(net.kyori.adventure.sound.Sound.sound(
                        net.kyori.adventure.key.Key.key("hypixel_ravengard", "entity.mimic.munch"),
                        net.kyori.adventure.sound.Sound.Source.HOSTILE, 1.0f, 0.9f),
                spawn.x(), spawn.y(), spawn.z());
        new RavengardMob(clip, spawn, mimicType,
                defeated -> DungeonSatchel.spawn(instance, defeated.getPosition()))
                .spawnMob(instance);
        player.sendActionBar(net.kyori.adventure.text.Component.text("MIMIC!"));
        org.tinylog.Logger.info("Ravengard chest mimic revealed: type={} tier={} pos={}",
                mimicType, tier, spawn);
    }

    private void openLoot(Player player) {
        clearCapturedBarriers();
        RavengardSounds.play(player, RavengardSounds.Cue.LOOT_OPEN);
        Pos lidHome = lid.getPosition();
        double radians = Math.toRadians(lidHome.yaw());
        Vec back = new Vec(Math.sin(radians), 0, -Math.cos(radians));
        for (int step = 1; step <= LID_TICKS; step++) {
            int tick = step;
            MinecraftServer.getSchedulerManager().buildTask(() -> {
                if (!isActive(lid)) return;
                lid.teleport(lidHome
                        .add(back.mul(0.0625 * tick))
                        .add(0, 0.1375 * tick, 0));
            }).delay(TaskSchedule.tick(step)).schedule();
        }

        Inventory inventory = Inventories.of(InventoryType.CHEST_3_ROW, "<f>{}",
                net.kyori.adventure.text.Component.text(TITLE_GLYPHS)
                        .font(net.swofty.type.ravengardgeneric.gui.RavengardFont.RAVENGARD));
        double distance = Math.hypot(base.x(), base.z());
        double tierBonus = tier.equals("gold") ? 200 : tier.equals("silver") ? 100 : 0;
        int rolls = 2 + random.nextInt(2) + (tier.equals("normal") ? 0 : 1);
        for (int slot : DungeonLoot.slots(random, LOOT_SLOTS, rolls)) {
            inventory.setItemStack(slot, DungeonLoot.roll(random, distance + tierBonus));
        }
        InteractableRegistry.watchClose(inventory, this, closer -> explode());
        MinecraftServer.getSchedulerManager().buildTask(() -> {
                    if (removed) return;
                    if (!player.isOnline() || player.getInstance() != instance
                            || !InteractableRegistry.canAccess(player)) {
                        explode();
                        return;
                    }
                    RavengardSounds.play(player, RavengardSounds.Cue.LOOT_READY);
                    player.openInventory(inventory);
                    remove();
                    watchInventoryState(player, inventory);
                })
                .delay(TaskSchedule.tick(LID_TICKS + 1)).schedule();
    }

    private void watchInventoryState(Player player, Inventory inventory) {
        MinecraftServer.getSchedulerManager().buildTask(() -> {
            if (removed) return;
            if (!player.isOnline() || player.getInstance() != instance
                    || player.getOpenInventory() != inventory) {
                explode();
                return;
            }
            watchInventoryState(player, inventory);
        }).delay(TaskSchedule.tick(5)).schedule();
    }

    private void explode() {
        if (removed) return;
        Pos center = base.add(0, 0.4, 0);
        instance.sendGroupedPacket(new ParticlePacket(Particle.POOF,
                center.x(), center.y(), center.z(), 0.4f, 0.4f, 0.4f, 0.02f, 24));
        remove();
    }

    @Override
    public void remove() {
        if (removed) return;
        super.remove();
        if (!bottom.isRemoved()) bottom.remove();
        if (!lid.isRemoved()) lid.remove();
        clearCapturedBarriers();
        if (!collisionCells.isEmpty()) {
            MinecraftServer.getSchedulerManager().buildTask(this::clearCapturedBarriers)
                    .delay(TaskSchedule.tick(1)).schedule();
        }
    }

    private void clearCapturedBarriers() {
        collisionCells.addAll(captureCollisionCells(instance, base));
        int remaining = 0;
        for (Vec cell : collisionCells) {
            if (blockAtLoaded(instance, cell.blockX(), cell.blockY(), cell.blockZ())
                    == net.minestom.server.instance.block.Block.BARRIER
                    && !DungeonDoor.isBarrierManaged(instance, cell)) {
                instance.setBlock(cell.blockX(), cell.blockY(), cell.blockZ(),
                        net.minestom.server.instance.block.Block.AIR);
            }
            if (blockAtLoaded(instance, cell.blockX(), cell.blockY(), cell.blockZ())
                    == net.minestom.server.instance.block.Block.BARRIER) {
                remaining++;
            }
        }
        if (remaining > 0) {
            org.tinylog.Logger.warn("Ravengard chest collision remains after removal: base={} cells={} (door-managed or restored concurrently)",
                    base, remaining);
        }
    }

    private static net.minestom.server.instance.block.Block blockAtLoaded(
            Instance instance, int x, int y, int z) {
        try {
            return instance.getBlock(x, y, z);
        } catch (RuntimeException unloaded) {
            try {
                instance.loadChunk(x >> 4, z >> 4).join();
                return instance.getBlock(x, y, z);
            } catch (RuntimeException ignored) {
                return net.minestom.server.instance.block.Block.AIR;
            }
        }
    }
}
