package net.swofty.type.ravengarddungeon.interactables;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.network.packet.server.SendablePacket;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonInteractionReachTest {
    @Test
    void wideDoorEdgeWithinReachUsesItsVolumeRatherThanItsOrigin() throws Exception {
        MinecraftServer.init();
        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.loadChunk(0, 0).join();
        var target = new DungeonInteractable(instance) {
            public void open(Player player) { }
        };
        var interaction = target.spawnInteraction(new Pos(5, 65, 5), 5.4f, 3.2f);
        assertTrue(interaction.isAutoViewable());
        assertTrue(!interaction.hasEntityCollision());
        assertTrue(!(interaction instanceof net.minestom.server.entity.LivingEntity));
        var connection = new PlayerConnection() {
            public void sendPacket(SendablePacket packet) { }
            public SocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 25565); }
        };
        var player = new Player(connection, new GameProfile(UUID.randomUUID(), "reach-test"));
        try {
            player.setInstance(instance, new Pos(10, 65, 5)).join();
            var method = net.minestom.server.listener.UseEntityListener.class
                    .getDeclaredMethod("getDistSquared", Player.class, net.minestom.server.entity.Entity.class);
            method.setAccessible(true);
            double distanceSquared = (double) method.invoke(null, player, interaction);
            assertTrue(distanceSquared < 9.0, "Door edge is 2.3 blocks away, squared distance=" + distanceSquared);
        } finally {
            player.remove();
            interaction.remove();
            MinecraftServer.getInstanceManager().unregisterInstance(instance);
            MinecraftServer.stopCleanly();
        }
    }
}
