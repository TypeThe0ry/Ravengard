package net.swofty.type.ravengarddungeon.interactables;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractableRegistryDoorEdgeTest {
    @BeforeAll
    static void initializeMinestom() {
        MinecraftServer.init();
    }

    @AfterAll
    static void stopMinestom() {
        MinecraftServer.stopCleanly();
    }

    @Test
    void heldClickPacketsToggleOnceButAReleasedClickTogglesAgain() {
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(64, 66, Block.AIR));
        instance.loadChunk(0, 0).join();
        DungeonDoor.spawnAll(instance, List.of(
                marker("door_1", 3, 66.5, 5, 0),
                marker("door_1", 5, 66.5, 5, 180),
                marker("door_1_top", 3.5, 66.5, 5, 180)));
        DungeonDoor door = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.INTERACTION)
                .map(InteractableRegistry::byInteraction)
                .map(DungeonDoor.class::cast)
                .findFirst()
                .orElseThrow();
        assertTrue(door.allowReactivation());
        UUID player = UUID.randomUUID();

        Pos doorway = new Pos(4, 65, 7.5);
        long gap = InteractableRegistry.DOOR_REPEAT_GAP_MILLIS;
        assertTrue(InteractableRegistry.tryDoorClickEdge(player, door, doorway, 1_000));
        assertFalse(InteractableRegistry.tryDoorClickEdge(player, door, doorway, 1_200));
        assertFalse(InteractableRegistry.tryDoorClickEdge(player, door, doorway, 1_400));
        assertFalse(InteractableRegistry.tryDoorClickEdge(player, door, doorway, 1_400 + gap));
        assertTrue(InteractableRegistry.tryDoorClickEdge(player, door, doorway, 1_401 + gap * 2));
        assertTrue(InteractableRegistry.tryDoorClickEdge(player, door, new Pos(4, 65, 9.5), 1_402 + gap * 2));

        InteractableRegistry.removeInstance(instance);
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    private static RavengardDungeonConfig.DungeonObject marker(
            String type, double x, double y, double z, float yaw) {
        return new RavengardDungeonConfig.DungeonObject("door", type, x, y, z, yaw, 0);
    }
}
