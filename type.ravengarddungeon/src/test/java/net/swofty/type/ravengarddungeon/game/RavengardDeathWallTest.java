package net.swofty.type.ravengarddungeon.game;

import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavengardDeathWallTest {
    @BeforeAll
    static void initializeMinestom() {
        net.minestom.server.MinecraftServer.init();
    }

    @AfterAll
    static void stopMinestom() {
        net.minestom.server.MinecraftServer.stopCleanly();
    }

    @Test
    void wallClockStartsOnlyWithTheGameAndUsesTheThirtyMinuteLimit() throws Exception {
        var instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
        var config = new net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig(
                "clock-test", "Clock test", "unused.polar", null, List.of());
        var game = new RavengardDungeonGame(instance, config);
        var clock = RavengardDungeonGame.class.getDeclaredField("startedAtNanos");
        clock.setAccessible(true);
        var gameState = net.swofty.type.game.game.AbstractGame.class.getDeclaredField("state");
        gameState.setAccessible(true);
        assertEquals(0, clock.getLong(game));
        assertEquals("Waiting for game start", game.getDeathWallStatus());
        gameState.set(game, net.swofty.type.game.game.GameState.COUNTDOWN);
        assertEquals("Game starting soon", game.getDeathWallStatus());
        game.start();
        long started = clock.getLong(game);
        assertTrue(started != 0);
        assertEquals("Explore | 30:00 remaining | Wall signal in 05:00", game.getDeathWallStatus());
        game.start();
        assertEquals(started, clock.getLong(game));
        game.end();
        assertFalse(game.isDeathWallActive());
        game.dispose();
        net.minestom.server.MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void exposesTheThirtyMinuteRoundAndRemainingHudClock() throws Exception {
        assertEquals(1800, RavengardDeathWall.remainingSeconds(0));
        assertEquals(1799, RavengardDeathWall.remainingSeconds(1));
        assertEquals(1, RavengardDeathWall.remainingSeconds(1799));
        assertEquals(0, RavengardDeathWall.remainingSeconds(1800));
        assertEquals("30:00", RavengardDeathWall.formatClock(1800));

        var instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
        var config = new net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig(
                "hud-clock-test", "HUD Clock test", "unused.polar", null, List.of());
        var game = new RavengardDungeonGame(instance, config);
        var gameState = net.swofty.type.game.game.AbstractGame.class.getDeclaredField("state");
        gameState.setAccessible(true);
        gameState.set(game, net.swofty.type.game.game.GameState.IN_PROGRESS);
        var startedAt = RavengardDungeonGame.class.getDeclaredField("startedAtNanos");
        startedAt.setAccessible(true);
        startedAt.setLong(game, System.nanoTime() - java.util.concurrent.TimeUnit.SECONDS.toNanos(125));

        var player = new net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer(
                new DummyConnection(), new GameProfile(UUID.randomUUID(), "hud-clock"));
        player.setInstance(instance, new net.minestom.server.coordinate.Pos(0, 66, 0)).join();
        var players = net.swofty.type.game.game.AbstractGame.class.getDeclaredField("players");
        players.setAccessible(true);
        @SuppressWarnings("unchecked")
        var members = (java.util.Map<UUID,
                net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer>) players.get(game);
        members.put(player.getUuid(), player);
        game.applyDeathWallForTest();
        assertEquals("27:55", net.swofty.type.ravengardgeneric.hud.RavengardHud.state(player).getClock());
        assertEquals("Explore", net.swofty.type.ravengardgeneric.hud.RavengardHud.state(player).getLocation());
        members.clear();
        player.remove();
        game.dispose();
        net.minestom.server.MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void aPlayerWhoNeverLoadsInIsDroppedSoTheRunStillStarts() throws Exception {
        var instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
        var config = new net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig(
                "ready-test", "Ready test", "unused.polar", null, List.of());
        var game = new RavengardDungeonGame(instance, config);
        var gameState = net.swofty.type.game.game.AbstractGame.class.getDeclaredField("state");
        gameState.setAccessible(true);
        gameState.set(game, net.swofty.type.game.game.GameState.IN_PROGRESS);
        var startedAt = RavengardDungeonGame.class.getDeclaredField("startedAtNanos");
        startedAt.setAccessible(true);
        var players = net.swofty.type.game.game.AbstractGame.class.getDeclaredField("players");
        players.setAccessible(true);
        @SuppressWarnings("unchecked")
        var members = (java.util.Map<UUID,
                net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer>) players.get(game);
        var loaded = new net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer(
                new DummyConnection(), new GameProfile(UUID.randomUUID(), "ready-loaded"));
        loaded.getPlayerConnection().setClientState(ConnectionState.PLAY);
        loaded.getPlayerConnection().setServerState(ConnectionState.PLAY);
        loaded.setInstance(instance, new net.minestom.server.coordinate.Pos(0, 66, 0)).join();
        var stuck = new net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer(
                new DummyConnection(), new GameProfile(UUID.randomUUID(), "ready-stuck"));
        members.put(loaded.getUuid(), loaded);
        members.put(stuck.getUuid(), stuck);

        for (int tick = 0; tick < RavengardDungeonGame.READY_GRACE_TICKS; tick++) game.tickGameForTest();
        assertEquals(0, startedAt.getLong(game));
        game.tickGameForTest();

        assertTrue(startedAt.getLong(game) != 0);
        assertFalse(members.containsKey(stuck.getUuid()));
        assertTrue(members.containsKey(loaded.getUuid()));

        members.clear();
        loaded.remove();
        game.dispose();
        net.minestom.server.MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void lootAndDoorsStayLockedUntilTheRunIsLiveForThatPlayer() throws Exception {
        var instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
        var config = new net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig(
                "gate-test", "Gate test", "unused.polar", null, List.of());
        var game = new RavengardDungeonGame(instance, config);
        var gameState = net.swofty.type.game.game.AbstractGame.class.getDeclaredField("state");
        gameState.setAccessible(true);
        var startedAt = RavengardDungeonGame.class.getDeclaredField("startedAtNanos");
        startedAt.setAccessible(true);
        var players = net.swofty.type.game.game.AbstractGame.class.getDeclaredField("players");
        players.setAccessible(true);
        @SuppressWarnings("unchecked")
        var members = (java.util.Map<UUID,
                net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer>) players.get(game);
        var player = new net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer(
                new DummyConnection(), new GameProfile(UUID.randomUUID(), "gate-player"));
        members.put(player.getUuid(), player);
        net.swofty.type.ravengardgeneric.profile.RavengardRunState.begin(player.getUuid(), game.getGameId());

        assertFalse(game.canInteract(player));
        gameState.set(game, net.swofty.type.game.game.GameState.IN_PROGRESS);
        assertFalse(game.canInteract(player));
        startedAt.setLong(game, System.nanoTime());
        assertTrue(game.canInteract(player));

        player.setDungeonDead(true);
        assertFalse(game.canInteract(player));
        player.setDungeonDead(false);
        net.swofty.type.ravengardgeneric.profile.RavengardRunState.finish(player.getUuid(), game.getGameId());
        assertFalse(game.canInteract(player));
        members.clear();

        game.dispose();
        net.minestom.server.MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void leavingAStartedRunWithoutExtractingForfeitsTheLoot() throws Exception {
        var instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
        var config = new net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig(
                "forfeit-test", "Forfeit test", "unused.polar", null, List.of());
        var game = new RavengardDungeonGame(instance, config);
        var startedAt = RavengardDungeonGame.class.getDeclaredField("startedAtNanos");
        startedAt.setAccessible(true);
        var loot = net.minestom.server.item.ItemStack.of(net.minestom.server.item.Material.DIAMOND, 5);

        var waiting = new net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer(
                new DummyConnection(), new GameProfile(UUID.randomUUID(), "forfeit-waiting"));
        waiting.getInventory().setItemStack(9, loot);
        net.swofty.type.ravengardgeneric.profile.RavengardRunState.begin(waiting.getUuid(), game.getGameId());
        game.forfeitRun(waiting);
        assertEquals(loot, waiting.getInventory().getItemStack(9));
        assertFalse(net.swofty.type.ravengardgeneric.profile.RavengardRunState.isActive(waiting.getUuid()));

        startedAt.setLong(game, System.nanoTime());
        var running = new net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer(
                new DummyConnection(), new GameProfile(UUID.randomUUID(), "forfeit-running"));
        var ravengardData = net.swofty.type.ravengardgeneric.data.RavengardDomain.KEY;
        net.swofty.type.generic.data.domain.PlayerDataService.store(ravengardData, running.getUuid(),
                net.swofty.type.ravengardgeneric.data.RavengardDataHandler.initUserWithDefaultData(running.getUuid()));
        running.getInventory().setItemStack(9, loot);
        net.swofty.type.ravengardgeneric.profile.RavengardRunState.begin(running.getUuid(), game.getGameId());
        game.forfeitRun(running);
        assertTrue(running.getInventory().getItemStack(9).isAir());
        assertFalse(net.swofty.type.ravengardgeneric.profile.RavengardRunState.isActive(running.getUuid()));
        net.swofty.type.generic.data.domain.PlayerDataService.evict(ravengardData, running.getUuid());

        var extracted = new net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer(
                new DummyConnection(), new GameProfile(UUID.randomUUID(), "forfeit-done"));
        extracted.getInventory().setItemStack(9, loot);
        game.forfeitRun(extracted);
        assertEquals(loot, extracted.getInventory().getItemStack(9));

        game.dispose();
        net.minestom.server.MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void playersCycleAcrossAllOfficialSpawnPads() {
        var instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
        var config = new net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig(
                "spawn-test", "Spawn test", "unused.polar", null, List.of());
        var game = new RavengardDungeonGame(instance, config);

        assertEquals(RavengardDungeonGame.SPAWN_POINTS.get(0), game.getSpawnPoint(0));
        assertEquals(RavengardDungeonGame.SPAWN_POINTS.get(1), game.getSpawnPoint(1));
        assertEquals(RavengardDungeonGame.SPAWN_POINTS.get(2), game.getSpawnPoint(2));
        assertEquals(RavengardDungeonGame.SPAWN_POINTS.get(0), game.getSpawnPoint(3));
        assertEquals(RavengardDungeonGame.SPAWN_POINTS.get(1), game.getSpawnPoint(4));
        assertEquals(3, new java.util.HashSet<>(RavengardDungeonGame.SPAWN_POINTS).size());

        game.dispose();
        net.minestom.server.MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void doesNotDisplayOrMoveAtMatchStartAndFirstSignalAppearsAtFiveMinutes() {
        var before = RavengardDeathWall.state(299, 262);
        var warning = RavengardDeathWall.state(300, 262);
        var moving = RavengardDeathWall.state(360, 262);
        assertEquals(RavengardDeathWall.Phase.EXPLORATION, before.phase());
        assertFalse(before.visible());
        assertEquals(262, before.radius());
        assertEquals(RavengardDeathWall.Phase.WARNING, warning.phase());
        assertTrue(warning.visible());
        assertEquals(60, warning.phaseRemainingSeconds());
        assertEquals(262, warning.radius());
        assertEquals(RavengardDeathWall.Phase.MOVING, moving.phase());
        assertTrue(moving.damaging());
    }

    @Test
    void timelineUsesFiveWarningMovingHoldCyclesAndNestedZones() {
        var timeline = RavengardDeathWall.timeline(5.5, -0.75, 264,
                List.of(new net.minestom.server.coordinate.Pos(-62.5, 70, 148.5),
                        new net.minestom.server.coordinate.Pos(182.5, 67, 234.5)), 7);
        long[][] boundaries = {
                {300, 360, 480}, {600, 660, 780}, {900, 960, 1080},
                {1200, 1260, 1380}, {1500, 1560, 1740}
        };
        double previousRadius = timeline.initialRadius();
        for (int index = 0; index < boundaries.length; index++) {
            int stage = index + 1;
            var warning = RavengardDeathWall.state(boundaries[index][0], timeline);
            var moving = RavengardDeathWall.state(boundaries[index][1], timeline);
            var hold = RavengardDeathWall.state(boundaries[index][2], timeline);
            assertEquals(stage, warning.stage());
            assertEquals(RavengardDeathWall.Phase.WARNING, warning.phase());
            assertEquals(RavengardDeathWall.Phase.MOVING, moving.phase());
            assertEquals(RavengardDeathWall.Phase.HOLD, hold.phase());
            assertTrue(hold.radius() < previousRadius);
            assertEquals(hold.nextCenter(), hold.currentCenter());
            assertEquals(hold.nextRadius(), hold.radius());
            previousRadius = hold.radius();
        }
        assertEquals(RavengardDeathWall.FINAL_RADIUS, previousRadius);
        assertEquals(RavengardDeathWall.Phase.EXPIRED,
                RavengardDeathWall.state(1800, timeline).phase());
    }

    @Test
    void deterministicSeedSelectsOneFinalZoneAndStagesMoveTowardIt() {
        var candidates = List.of(
                new net.minestom.server.coordinate.Pos(-80, 66, 120),
                new net.minestom.server.coordinate.Pos(140, 66, -90),
                new net.minestom.server.coordinate.Pos(260, 66, 0));
        var first = RavengardDeathWall.timeline(0, 0, 264, candidates, 7);
        var again = RavengardDeathWall.timeline(0, 0, 264, candidates, 7);
        var other = RavengardDeathWall.timeline(0, 0, 264, candidates, 8);
        assertEquals(first, again);
        assertNotEquals(first.targetCenter(5), other.targetCenter(5));
        assertEquals(first.targetCenter(5), RavengardDeathWall.state(1740, first).currentCenter());
        assertEquals(12, RavengardDeathWall.state(1740, first).radius());
    }

    @Test
    void stateSeparatesCurrentBoundaryFromNextTargetDuringMovement() {
        var timeline = RavengardDeathWall.timeline(0, 0, 264,
                List.of(new net.minestom.server.coordinate.Pos(140, 66, -90)), 1);
        var warning = RavengardDeathWall.state(300, timeline);
        assertEquals(new RavengardDeathWall.Center(0, 0), warning.currentCenter());
        assertNotEquals(warning.currentCenter(), warning.nextCenter());
        assertEquals(264, warning.radius());
        assertTrue(warning.nextRadius() < warning.radius());

        var halfMove = RavengardDeathWall.state(420, timeline);
        assertEquals(RavengardDeathWall.Phase.MOVING, halfMove.phase());
        assertNotEquals(halfMove.currentCenter(), halfMove.nextCenter());
        assertTrue(halfMove.radius() < warning.radius());
        assertTrue(halfMove.radius() > halfMove.nextRadius());
        assertEquals(0.5, halfMove.progress(), 0.001);
    }

    @Test
    void outsideDamageStartsWhenWallIsVisibleAndIncreasesPerStage() {
        assertFalse(RavengardDeathWall.shouldDamage(299, 0));
        assertTrue(RavengardDeathWall.shouldDamage(300, 0));
        assertFalse(RavengardDeathWall.shouldDamage(300, 300));
        assertTrue(RavengardDeathWall.shouldDamage(301, 300));
        assertEquals(2, RavengardDeathWall.state(300, 264).damageAmount());
        assertEquals(3, RavengardDeathWall.state(900, 264).damageAmount());
        assertEquals(6, RavengardDeathWall.state(1740, 264).damageAmount());
    }

    @Test
    void onlyOutsideEntitiesTakeOutsideBorderDamage() {
        var entity = new net.minestom.server.entity.LivingEntity(net.minestom.server.entity.EntityType.ZOMBIE);
        entity.setHealth(20);
        var instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.loadChunk(0, 0).join();
        entity.setInstance(instance, new net.minestom.server.coordinate.Pos(2, 66, 0)).join();
        assertFalse(RavengardDeathWall.damageOutside(entity, 299, 0, 0, 1));
        assertTrue(RavengardDeathWall.damageOutside(entity, 360, 0, 0, 1));
        assertEquals(18, entity.getHealth());
        entity.setHealth(20);
        assertTrue(RavengardDeathWall.damageOutside(entity, 960, 0, 0, 1));
        assertEquals(16, entity.getHealth());
        entity.teleport(new net.minestom.server.coordinate.Pos(1, 66, 1)).join();
        assertFalse(RavengardDeathWall.damageOutside(entity, 1560, 0, 0, 1));
        entity.remove();
        net.minestom.server.MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void clientBorderUsesTheSameStateCenterAndSquareBoundaryAsDamageChecks() {
        var timeline = RavengardDeathWall.timeline(10, -20, 264,
                List.of(new net.minestom.server.coordinate.Pos(100, 66, 60)), 1);
        var state = RavengardDeathWall.state(420, timeline);
        var border = RavengardDeathWall.border(state);
        assertEquals(state.radius() * 2, border.diameter());
        assertEquals(state.currentCenter().x(), border.centerX());
        assertEquals(state.currentCenter().z(), border.centerZ());
        for (double x : new double[]{-300, 0, 10, 200, 300}) {
            for (double z : new double[]{-400, -20, 0, 200, 400}) {
                assertEquals(!RavengardDeathWall.isOutside(x, z,
                                state.currentCenter().x(), state.currentCenter().z(), state.radius()),
                        border.inBounds(new net.minestom.server.coordinate.Pos(x, 66, z)));
            }
        }
    }

    @Test
    void liveGameTickDamagesAnOutsidePlayerOncePerSecond() throws Exception {
        var instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.loadChunk(0, 0).join();
        var config = new net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig(
                "damage-test", "Damage test", "unused.polar", null, List.of());
        var game = new RavengardDungeonGame(instance, config);
        game.configureDeathWall(0, 0, 8);
        var gameState = net.swofty.type.game.game.AbstractGame.class.getDeclaredField("state");
        gameState.setAccessible(true);
        gameState.set(game, net.swofty.type.game.game.GameState.IN_PROGRESS);
        var startedAt = RavengardDungeonGame.class.getDeclaredField("startedAtNanos");
        startedAt.setAccessible(true);
        startedAt.setLong(game, System.nanoTime() - java.util.concurrent.TimeUnit.SECONDS.toNanos(360));

        var player = new net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer(
                new DummyConnection(), new GameProfile(UUID.randomUUID(), "wall-test"));
        player.setInstance(instance, new net.minestom.server.coordinate.Pos(20, 66, 0)).join();
        player.setHealth(20);
        var players = net.swofty.type.game.game.AbstractGame.class.getDeclaredField("players");
        players.setAccessible(true);
        @SuppressWarnings("unchecked")
        var members = (java.util.Map<UUID,
                net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer>) players.get(game);
        members.put(player.getUuid(), player);

        game.applyDeathWallForTest();
        assertEquals(18, player.getHealth());
        game.applyDeathWallForTest();
        assertEquals(18, player.getHealth());
        startedAt.setLong(game, System.nanoTime() - java.util.concurrent.TimeUnit.SECONDS.toNanos(361));
        game.applyDeathWallForTest();
        assertEquals(16, player.getHealth());

        members.clear();
        player.remove();
        game.dispose();
        net.minestom.server.MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void exactPhaseBoundariesCoverTheEntireThirtyMinuteRound() {
        long[][] samples = {
                {299, 0}, {300, 1}, {359, 1}, {360, 2}, {479, 2}, {480, 3}, {599, 3},
                {600, 1}, {659, 1}, {660, 2}, {779, 2}, {780, 3}, {899, 3},
                {900, 1}, {959, 1}, {960, 2}, {1079, 2}, {1080, 3}, {1199, 3},
                {1200, 1}, {1259, 1}, {1260, 2}, {1379, 2}, {1380, 3}, {1499, 3},
                {1500, 1}, {1559, 1}, {1560, 2}, {1739, 2}, {1740, 3}, {1799, 3},
                {1800, 4}
        };
        RavengardDeathWall.Phase[] phases = RavengardDeathWall.Phase.values();
        for (long[] sample : samples) {
            assertEquals(phases[(int) sample[1]], RavengardDeathWall.phase(sample[0]),
                    "elapsed=" + sample[0]);
        }
    }

    @Test
    void startWaitsForPlayStateBeforeStartingTheClockOrSendingPlayPackets() throws Exception {
        var instance = net.minestom.server.MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.loadChunk(0, 0).join();
        var config = new net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig(
                "state-test", "State test", "unused.polar", null, List.of());
        var game = new RavengardDungeonGame(instance, config);
        var connection = new DummyConnection();
        connection.setClientState(ConnectionState.PLAY);
        connection.setServerState(ConnectionState.PLAY);
        var player = new net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer(
                connection, new GameProfile(UUID.randomUUID(), "state-test"));
        player.setInstance(instance, new net.minestom.server.coordinate.Pos(0, 66, 0)).join();
        var players = net.swofty.type.game.game.AbstractGame.class.getDeclaredField("players");
        players.setAccessible(true);
        @SuppressWarnings("unchecked")
        var members = (java.util.Map<UUID,
                net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer>) players.get(game);
        members.put(player.getUuid(), player);

        connection.setClientState(ConnectionState.CONFIGURATION);
        connection.setServerState(ConnectionState.CONFIGURATION);
        connection.resetPackets();
        game.start();
        assertEquals(0, game.getElapsedSeconds());
        assertEquals(0, connection.packetCount());

        connection.setClientState(ConnectionState.PLAY);
        connection.setServerState(ConnectionState.PLAY);
        game.tickGameForTest();
        assertTrue(game.getRemainingSeconds() >= 1799);
        assertTrue(connection.packetCount() > 0);

        members.clear();
        player.remove();
        game.dispose();
        net.minestom.server.MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void packsMapCentersUsingTheOfficialResourcePackProtocol() {
        assertEquals(0x800800, RavengardDeathWall.packMapCenter(0, 0));
        assertEquals(0x8017FF, RavengardDeathWall.packMapCenter(0.5, -0.5));
        assertEquals(0, RavengardDeathWall.packMapCenter(-5000, -5000));
        assertEquals(0xFFFFFF, RavengardDeathWall.packMapCenter(5000, 5000));
    }

    private static final class DummyConnection extends PlayerConnection {
        private int packetCount;

        @Override
        public void sendPacket(SendablePacket packet) {
            packetCount++;
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 25565);
        }

        int packetCount() {
            return packetCount;
        }

        void resetPackets() {
            packetCount = 0;
        }
    }
}
