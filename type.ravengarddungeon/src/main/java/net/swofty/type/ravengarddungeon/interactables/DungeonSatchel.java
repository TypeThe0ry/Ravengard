package net.swofty.type.ravengarddungeon.interactables;

import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import net.swofty.type.generic.gui.inventory.Inventories;
import net.swofty.type.ravengardgeneric.sound.RavengardSounds;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class DungeonSatchel extends DungeonInteractable {
    private static final String CLOSED_MODEL = "hypixel_ravengard:item/gameplay/lootbags/lootbag_2";
    private static final String OPEN_MODEL = "hypixel_ravengard:item/gameplay/lootbags/lootbag_2_open";
    private static final String SKULL_MODEL = "hypixel_ravengard:item/gameplay/trinkets/crystal_skull/sprite";
    private static final int[] LOOT_SLOTS = {29, 30, 31, 32, 33};
    private static final net.minestom.server.tag.Tag<Boolean> DROPPED =
            net.minestom.server.tag.Tag.Boolean("ravengard_dropped_satchel").defaultValue(false);

    private final Entity bag;
    private final Pos base;
    private Task skullTask;

    private DungeonSatchel(Instance instance, Pos base) {
        super(instance);
        this.base = base;
        this.interaction = spawnInteraction(base.sub(0, 0.5, 0), 0.5f, 0.65f);
        this.bag = spawnDisplay(base, CLOSED_MODEL);
        bag.setTag(DROPPED, true);
    }

    public static DungeonSatchel spawn(Instance instance, Pos position) {
        Pos base = groundedAt(instance, position).add(0, 0.5, 0)
                .withPitch(0f).withYaw(0f);
        DungeonSatchel satchel = new DungeonSatchel(instance, base);
        instance.sendGroupedPacket(new ParticlePacket(Particle.CAMPFIRE_COSY_SMOKE,
                base.x(), base.y(), base.z(), 0.25f, 0.2f, 0.25f, 0.01f, 14));
        satchel.skullTask = MinecraftServer.getSchedulerManager()
                .buildTask(satchel::riseSkull)
                .delay(TaskSchedule.tick(15)).repeat(TaskSchedule.tick(24)).schedule();
        InteractableRegistry.register(satchel);
        return satchel;
    }

    /**
     * The official map was captured mid-run, with the satchels players and mobs had dropped still on
     * the floor; a fresh dungeon starts without them.
     */
    public static int clearCapturedSatchels(Instance instance) {
        int cleared = 0;
        for (Entity entity : java.util.List.copyOf(instance.getEntities())) {
            if (entity.getTag(DROPPED) || !(entity.getEntityMeta() instanceof ItemDisplayMeta meta)) continue;
            var model = meta.getItemStack().get(DataComponents.ITEM_MODEL);
            if (model == null) continue;
            String name = model.toString();
            if (name.equals(CLOSED_MODEL) || name.equals(OPEN_MODEL)) {
                entity.remove();
                cleared++;
            }
        }
        return cleared;
    }

    private void riseSkull() {
        if (opened || removed || !isActive(bag)) {
            if (skullTask != null) skullTask.cancel();
            return;
        }
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        for (int burst = 0; burst < 3; burst++) {
            double jitterX = random.nextDouble(-0.25, 0.25);
            double jitterZ = random.nextDouble(-0.25, 0.25);
            for (int height = 0; height < 4; height++) {
                int delay = burst * 3 + height * 4 + 1;
                double y = 0.2 + height * 0.28;
                MinecraftServer.getSchedulerManager().buildTask(() -> {
                    if (opened || removed || !isActive(bag)) return;
                    instance.sendGroupedPacket(new ParticlePacket(
                            Particle.DUST.withColor(new net.minestom.server.color.Color(255, 30, 30))
                                    .withScale(0.7f),
                            base.x() + jitterX, base.y() + y, base.z() + jitterZ,
                            0.03f, 0.05f, 0.03f, 0f, 2));
                }).delay(TaskSchedule.tick(delay)).schedule();
            }
        }
    }

    private static final int GROUND_SEARCH_RADIUS = 2;

    static Pos groundedAt(Instance instance, Pos position) {
        int originX = (int) Math.floor(position.x()), originZ = (int) Math.floor(position.z());
        for (int radius = 0; radius <= GROUND_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    Integer feetY = standableY(instance, originX + dx, originZ + dz, position.y());
                    if (feetY == null) continue;
                    return radius == 0
                            ? position.withY(feetY)
                            : new Pos(originX + dx + 0.5, feetY, originZ + dz + 0.5);
                }
            }
        }
        return position.withY(groundY(instance, position));
    }

    private static Integer standableY(Instance instance, int x, int z, double referenceY) {
        int top = (int) Math.floor(referenceY) + 1;
        try {
            for (int y = top; y >= top - 9; y--) {
                if (!instance.getBlock(x, y, z).isSolid()) continue;
                if (!instance.getBlock(x, y + 1, z).isSolid() && !instance.getBlock(x, y + 2, z).isSolid()) {
                    return y + 1;
                }
                return null;
            }
        } catch (Exception exception) {
            return null;
        }
        return null;
    }

    private static double groundY(Instance instance, Pos position) {
        int x = (int) Math.floor(position.x()), z = (int) Math.floor(position.z());
        for (int y = (int) Math.floor(position.y()) + 1; y >= (int) Math.floor(position.y()) - 8; y--) {
            try {
                if (!instance.getBlock(x, y, z).air()) {
                    return y + 1;
                }
            } catch (Exception exception) {
                return position.y();
            }
        }
        return position.y();
    }

    @Override
    public boolean openOnLook() {
        return false;
    }

    @Override
    public String castLabel() {
        return "Searching Body";
    }

    @Override
    public void open(Player player) {
        if (removed || !isActive(bag) || !player.isOnline() || player.getInstance() != instance) return;
        opened = true;
        RavengardSounds.play(player, RavengardSounds.Cue.LOOT_OPEN);
        if (skullTask != null) {
            skullTask.cancel();
        }
        bag.editEntityMeta(ItemDisplayMeta.class, meta -> meta.setItemStack(modelStack(OPEN_MODEL)));

        Inventory inventory = Inventories.of(InventoryType.CHEST_5_ROW, "Dead Body");
        Random random = ThreadLocalRandom.current();
        double distance = Math.hypot(base.x(), base.z());
        int rolls = 1 + random.nextInt(3);
        for (int slot : DungeonLoot.slots(random, LOOT_SLOTS, rolls)) {
            inventory.setItemStack(slot, DungeonLoot.roll(random, distance));
        }
        InteractableRegistry.watchClose(inventory, this, closer -> {
            instance.sendGroupedPacket(new ParticlePacket(Particle.POOF,
                    base.x(), base.y(), base.z(), 0.25f, 0.25f, 0.25f, 0.02f, 12));
            bag.remove();
            remove();
        });
        player.openInventory(inventory);
        RavengardSounds.play(player, RavengardSounds.Cue.LOOT_READY);
    }

    @Override
    public void remove() {
        if (removed) return;
        if (skullTask != null) skullTask.cancel();
        super.remove();
        if (!bag.isRemoved()) bag.remove();
    }
}
