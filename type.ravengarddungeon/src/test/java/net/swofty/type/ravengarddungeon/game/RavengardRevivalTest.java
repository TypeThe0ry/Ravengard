package net.swofty.type.ravengarddungeon.game;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import net.swofty.type.game.game.AbstractGame;
import net.swofty.type.game.game.GameState;
import net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig;
import net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer;
import net.swofty.type.ravengardgeneric.profile.RavengardRunState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavengardRevivalTest {
    @BeforeAll
    static void initializeMinestom() {
        MinecraftServer.init();
    }

    @AfterAll
    static void stopMinestom() {
        MinecraftServer.stopCleanly();
    }

    @Test
    void aLivingTeammateRevivesOnlyTheirOwnFallenTeammatesAtTheStatue() throws Exception {
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 64, Block.STONE));
        instance.loadChunk(0, 0).join();
        RavengardDungeonGame game = new RavengardDungeonGame(instance,
                new RavengardDungeonConfig("revival-test", "Revival test", "unused.polar", null, List.of()));
        var state = AbstractGame.class.getDeclaredField("state");
        state.setAccessible(true);
        state.set(game, GameState.IN_PROGRESS);
        var startedAt = RavengardDungeonGame.class.getDeclaredField("startedAtNanos");
        startedAt.setAccessible(true);
        startedAt.setLong(game, System.nanoTime());
        var players = AbstractGame.class.getDeclaredField("players");
        players.setAccessible(true);
        @SuppressWarnings("unchecked")
        var members = (Map<UUID, RavengardDungeonPlayer>) players.get(game);

        RavengardDungeonPlayer reviver = member(instance, members, game, "reviver", 0, false);
        RavengardDungeonPlayer fallen = member(instance, members, game, "fallen", 0, true);
        RavengardDungeonPlayer rival = member(instance, members, game, "rival", 1, true);
        assertTrue(game.hasFallenTeammates(reviver));
        assertFalse(game.hasFallenTeammates(rival));

        Pos statueFront = new Pos(5.5, 64, 6.7, 0f, 0f);
        assertEquals(1, game.reviveTeammates(reviver, statueFront));

        assertFalse(fallen.isDungeonDead());
        assertEquals(GameMode.SURVIVAL, fallen.getGameMode());
        assertEquals(fallen.getAttributeValue(net.minestom.server.entity.attribute.Attribute.MAX_HEALTH), fallen.getHealth(), 1e-6);
        assertTrue(fallen.getPosition().samePoint(statueFront));
        assertTrue(rival.isDungeonDead());
        assertFalse(game.hasFallenTeammates(reviver));
        assertEquals(0, game.reviveTeammates(reviver, statueFront));

        members.values().forEach(member -> RavengardRunState.finish(member.getUuid(), game.getGameId()));
        List<RavengardDungeonPlayer> joined = List.copyOf(members.values());
        members.clear();
        joined.forEach(RavengardDungeonPlayer::remove);
        game.dispose();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    private static RavengardDungeonPlayer member(InstanceContainer instance, Map<UUID, RavengardDungeonPlayer> members,
                                                 RavengardDungeonGame game, String name, int team, boolean dead) {
        RavengardDungeonPlayer player = new RavengardDungeonPlayer(
                new DummyConnection(), new GameProfile(UUID.randomUUID(), name));
        player.setInstance(instance, new Pos(0, 64, 0)).join();
        player.setGameId(game.getGameId());
        player.setDungeonTeam(team);
        player.setDungeonDead(dead);
        player.setGameMode(dead ? GameMode.SPECTATOR : GameMode.SURVIVAL);
        if (dead) player.setHealth(1f);
        RavengardRunState.begin(player.getUuid(), game.getGameId());
        members.put(player.getUuid(), player);
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
