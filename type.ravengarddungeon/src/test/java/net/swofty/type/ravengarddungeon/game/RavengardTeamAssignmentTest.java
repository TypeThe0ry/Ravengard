package net.swofty.type.ravengarddungeon.game;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import net.swofty.type.game.game.AbstractGame;
import net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig;
import net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer;
import net.swofty.type.ravengardgeneric.queue.RavengardMatchMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RavengardTeamAssignmentTest {
    @BeforeAll
    static void initializeMinestom() {
        MinecraftServer.init();
    }

    @AfterAll
    static void stopMinestom() {
        MinecraftServer.stopCleanly();
    }

    @Test
    void aPartyKeepsItsTeamAndStrangersDoNotTakeItsSeats() throws Exception {
        var setup = game(RavengardMatchMode.TRIOS);
        UUID leader = UUID.randomUUID();
        var stranger = place(setup, "stranger", null);
        var first = place(setup, "first", leader);
        var second = place(setup, "second", leader);
        var other = place(setup, "other", null);

        assertEquals(0, stranger.getDungeonTeam());
        assertNotEquals(stranger.getDungeonTeam(), first.getDungeonTeam(), "a party starts its own team");
        assertEquals(first.getDungeonTeam(), second.getDungeonTeam());
        assertEquals(0, other.getDungeonTeam(), "strangers fill the strangers' team, not the party's");
        assertEquals(9, setup.game().getMaxPlayers());
    }

    @Test
    void soloRunsPutEveryoneOnTheirOwnTeam() throws Exception {
        var setup = game(RavengardMatchMode.SOLO);
        var a = place(setup, "a", null);
        var b = place(setup, "b", null);
        var c = place(setup, "c", null);
        assertEquals(List.of(0, 1, 2), List.of(a.getDungeonTeam(), b.getDungeonTeam(), c.getDungeonTeam()));
        assertEquals(8, setup.game().getMaxPlayers());
    }

    private record Setup(RavengardDungeonGame game, Map<UUID, RavengardDungeonPlayer> members) {
    }

    @SuppressWarnings("unchecked")
    private static Setup game(RavengardMatchMode mode) throws Exception {
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        var game = new RavengardDungeonGame(instance,
                new RavengardDungeonConfig("team-test", "Team test", "unused.polar", null, List.of()));
        game.setMatchMode(mode);
        var players = AbstractGame.class.getDeclaredField("players");
        players.setAccessible(true);
        return new Setup(game, (Map<UUID, RavengardDungeonPlayer>) players.get(game));
    }

    private static RavengardDungeonPlayer place(Setup setup, String name, UUID partyLeader) {
        var player = new RavengardDungeonPlayer(new DummyConnection(), new GameProfile(UUID.randomUUID(), name));
        setup.game().setPartyLeader(player.getUuid(), partyLeader);
        player.setDungeonTeam(setup.game().openTeam(player));
        setup.members().put(player.getUuid(), player);
        return player;
    }

    private static final class DummyConnection extends PlayerConnection {
        @Override
        public void sendPacket(SendablePacket packet) {
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 25565);
        }
    }
}
