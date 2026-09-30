package net.swofty.type.ravengarddungeon.interactables;

import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.SharedInstance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.network.packet.server.SendablePacket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DungeonChestLifecycleTest {
    private InstanceContainer instance;

    @BeforeAll
    static void start() {
        MinecraftServer.init();
    }

    @AfterAll
    static void stop() {
        MinecraftServer.stopCleanly();
    }

    @BeforeEach
    void createInstance() {
        instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.loadChunk(0, 0).join();
    }

    @AfterEach
    void removeInstance() {
        InteractableRegistry.removeInstance(instance);
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void duplicateDecorPairsBecomeOneChestAndConversionIsIdempotent() {
        Pos base = new Pos(4.5, 3, 4.5);
        display("normal", "bottom", base);
        display("normal", "top", base.add(0, 0.3, 0));
        display("normal", "bottom", base);
        display("normal", "top", base.add(0, 0.3, 0));

        assertEquals(1, DungeonChest.convertDecorChests(instance, 1));
        assertEquals(1, chests().size());
        assertEquals(2, displayCount());
        assertEquals(0, DungeonChest.convertDecorChests(instance, 1));
        assertEquals(1, chests().size());

        display("normal", "bottom", base);
        display("normal", "top", base.add(0, 0.3, 0));
        assertEquals(0, DungeonChest.convertDecorChests(instance, 1));
        assertEquals(2, displayCount());
        assertEquals(1, chests().size());
    }

    @Test
    void nearestLidOfTheSameTierIsPairedWithoutStealingAnotherChestLid() throws Exception {
        Pos base = new Pos(4.5, 3, 4.5);
        Entity bottom = display("normal", "bottom", base);
        Entity wrongTier = display("gold", "top", base);
        Entity distant = display("normal", "top", base.add(0.8, 0, 0));
        Entity nearest = display("normal", "top", base.add(0.1, 0.2, 0));

        assertEquals(1, DungeonChest.convertDecorChests(instance, 1));
        DungeonChest chest = chests().getFirst();
        Field lid = DungeonChest.class.getDeclaredField("lid");
        lid.setAccessible(true);
        assertSame(nearest, lid.get(chest));
        chest.remove();
        assertTrue(bottom.isRemoved());
        assertTrue(nearest.isRemoved());
        assertFalse(wrongTier.isRemoved());
        assertFalse(distant.isRemoved());
    }

    @Test
    void visibleChestDisplaysResolveBackToTheirInteractionHitbox() throws Exception {
        Pos base = new Pos(4.5, 3, 4.5);
        Entity bottom = display("normal", "bottom", base);
        Entity lid = display("normal", "top", base.add(0, 0.3, 0));

        assertEquals(1, DungeonChest.convertDecorChests(instance, 1));
        DungeonChest chest = chests().getFirst();

        assertTrue(chest.matchesVisibleTarget(bottom));
        assertTrue(chest.matchesVisibleTarget(lid));
        assertSame(chest, InteractableRegistry.byInteraction(bottom));
        assertSame(chest, InteractableRegistry.byInteraction(lid));
        assertFalse(chest.matchesVisibleTarget(new Entity(EntityType.ITEM_DISPLAY)));
    }

    @Test
    void removalClearsOnlyCapturedChestBarriersAndUnregistersInteraction() {
        Pos base = new Pos(4.5, 3, 4.5);
        instance.setBlock(4, 2, 4, Block.BARRIER);
        instance.setBlock(4, 3, 4, Block.BARRIER);
        instance.setBlock(5, 3, 4, Block.BARRIER);
        instance.setBlock(6, 3, 4, Block.BARRIER);
        instance.setBlock(4, 1, 4, Block.BARRIER);
        display("normal", "bottom", base);
        display("normal", "top", base.add(0, 0.3, 0));
        assertEquals(1, DungeonChest.convertDecorChests(instance, 1));
        DungeonChest chest = chests().getFirst();
        Entity interaction = chest.getInteraction();
        instance.setBlock(4, 3, 4, Block.STONE);
        instance.setBlock(5, 3, 4, Block.BARRIER);

        chest.remove();
        chest.remove();

        assertEquals(Block.AIR, instance.getBlock(4, 2, 4));
        assertEquals(Block.STONE, instance.getBlock(4, 3, 4));
        assertEquals(Block.AIR, instance.getBlock(5, 3, 4));
        assertEquals(Block.BARRIER, instance.getBlock(6, 3, 4));
        assertEquals(Block.BARRIER, instance.getBlock(4, 1, 4));
        assertTrue(interaction.isRemoved());
        assertNull(InteractableRegistry.byInteraction(interaction));
        assertEquals(0, displayCount());
    }

    @Test
    void removalDoesNotClearADoorBarrierSharedWithAnOpenableDoor() {
        Pos base = new Pos(4.5, 3, 4.5);
        display("normal", "bottom", base);
        display("normal", "top", base.add(0, 0.3, 0));
        Vec shared = new Vec(5, 3, 4);
        instance.setBlock(shared.blockX(), shared.blockY(), shared.blockZ(), Block.AIR);
        assertTrue(DungeonDoor.retainBarrier(instance, shared));
        assertEquals(Block.BARRIER, instance.getBlock(shared));
        assertEquals(1, DungeonChest.convertDecorChests(instance, 1));

        chests().getFirst().remove();

        assertEquals(Block.BARRIER, instance.getBlock(shared));
        DungeonDoor.releaseBarrier(instance, shared);
        assertEquals(Block.AIR, instance.getBlock(shared));
    }


    @Test
    void inventoryCloseCallbackRemovesModelsInteractionAndCapturedCollision() {
        Pos base = new Pos(4.5, 3, 4.5);
        instance.setBlock(4, 3, 4, Block.BARRIER);
        display("normal", "bottom", base);
        display("normal", "top", base.add(0, 0.3, 0));
        assertEquals(1, DungeonChest.convertDecorChests(instance, 1));
        DungeonChest chest = chests().getFirst();
        Entity interaction = chest.getInteraction();
        var inventory = new net.minestom.server.inventory.Inventory(
                net.minestom.server.inventory.InventoryType.CHEST_3_ROW, "Loot");
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        InteractableRegistry.watchClose(inventory, chest, player -> {
            calls.incrementAndGet();
            chest.remove();
        });

        InteractableRegistry.onInventoryClose(null, inventory);
        InteractableRegistry.onInventoryClose(null, inventory);

        assertEquals(1, calls.get());
        assertEquals(Block.AIR, instance.getBlock(4, 3, 4));
        assertEquals(0, displayCount());
        assertTrue(interaction.isRemoved());
        assertNull(InteractableRegistry.byInteraction(interaction));
    }

    @Test
    void removalClearsBarrierAddedAfterChestConversion() {
        Pos base = new Pos(4.5, 3, 4.5);
        display("normal", "bottom", base);
        display("normal", "top", base.add(0, 0.3, 0));
        assertEquals(1, DungeonChest.convertDecorChests(instance, 1));
        instance.setBlock(5, 3, 4, Block.BARRIER);

        chests().getFirst().remove();

        assertEquals(Block.AIR, instance.getBlock(5, 3, 4));
    }

    @Test
    void chestRemovalPreservesLateDoorOwnershipUntilBothDoorsReleaseIt() {
        Pos base = new Pos(4.5, 3, 4.5);
        Vec shared = new Vec(4, 3, 4);
        instance.setBlock(4, 3, 4, Block.BARRIER);
        display("normal", "bottom", base);
        display("normal", "top", base.add(0, 0.3, 0));
        assertEquals(1, DungeonChest.convertDecorChests(instance, 1));
        assertTrue(DungeonDoor.retainBarrier(instance, shared));
        assertTrue(DungeonDoor.retainBarrier(instance, shared));

        chests().getFirst().remove();
        assertEquals(Block.BARRIER, instance.getBlock(shared));
        DungeonDoor.releaseBarrier(instance, shared);
        assertEquals(Block.BARRIER, instance.getBlock(shared));
        DungeonDoor.releaseBarrier(instance, shared);
        assertEquals(Block.AIR, instance.getBlock(shared));
        assertFalse(DungeonDoor.isBarrierManaged(instance, shared));
    }

    @Test
    void removalClearsCollisionThroughDungeonSharedInstance() {
        SharedInstance shared = MinecraftServer.getInstanceManager().createSharedInstance(instance);
        RecordingConnection connection = new RecordingConnection();
        var player = new net.minestom.server.entity.Player(connection,
                new GameProfile(UUID.randomUUID(), "shared-viewer"));
        try {
            player.setInstance(shared, new Pos(8, 3, 8)).join();
            Pos base = new Pos(4.5, 3, 4.5);
            shared.setBlock(4, 3, 4, Block.BARRIER);
            Entity bottom = display(shared, "normal", "bottom", base);
            Entity lid = display(shared, "normal", "top", base.add(0, 0.3, 0));

            assertEquals(1, DungeonChest.convertDecorChests(shared, 1));
            DungeonChest chest = shared.getEntities().stream().map(InteractableRegistry::byInteraction)
                    .filter(DungeonChest.class::isInstance).map(DungeonChest.class::cast)
                    .findFirst().orElseThrow();
            Entity interaction = chest.getInteraction();
            connection.reset();

            chest.remove();

            assertEquals(Block.AIR, shared.getBlock(4, 3, 4));
            assertEquals(Block.AIR, instance.getBlock(4, 3, 4));
            assertTrue(bottom.isRemoved());
            assertTrue(lid.isRemoved());
            assertTrue(interaction.isRemoved());
            assertNull(InteractableRegistry.byInteraction(interaction));
            assertTrue(connection.packetCount() > 0, "SharedInstance removal must send block updates to viewers");
        } finally {
            player.remove();
            InteractableRegistry.removeInstance(shared);
            MinecraftServer.getInstanceManager().unregisterInstance(shared);
        }
    }

    private Entity display(String tier, String part, Pos pos) {
        return display(instance, tier, part, pos);
    }

    private Entity display(net.minestom.server.instance.Instance target, String tier, String part, Pos pos) {
        Entity entity = new Entity(EntityType.ITEM_DISPLAY);
        entity.editEntityMeta(ItemDisplayMeta.class, meta -> meta.setItemStack(
                ItemStack.builder(Material.PAPER).set(DataComponents.ITEM_MODEL,
                        "hypixel_ravengard:item/gameplay/chests/" + tier + "/" + tier + "_" + part).build()));
        entity.setInstance(target, pos).join();
        return entity;
    }

    private List<DungeonChest> chests() {
        return instance.getEntities().stream().map(InteractableRegistry::byInteraction)
                .filter(DungeonChest.class::isInstance).map(DungeonChest.class::cast).distinct().toList();
    }

    private long displayCount() {
        return instance.getEntities().stream().filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY).count();
    }

    private static final class RecordingConnection extends PlayerConnection {
        private int packets;

        @Override
        public void sendPacket(SendablePacket packet) {
            packets++;
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 25565);
        }

        void reset() {
            packets = 0;
        }

        int packetCount() {
            return packets;
        }
    }
}
