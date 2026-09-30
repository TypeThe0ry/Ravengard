package net.swofty.type.ravengarddungeon.game;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonInstanceMembershipTest {
    @Test
    void activeGameWinsOverStaleMembershipAndJoiningReconcilesDuplicates() {
        UUID player = UUID.randomUUID();
        var oldInstance = new FakeInstance();
        var currentInstance = new FakeInstance();
        oldInstance.members().add(player);
        currentInstance.activePlayers().add(player);

        var active = DungeonInstanceRegistry.findActiveByPlayer(
                player, List.of(oldInstance, currentInstance), FakeInstance::hasActivePlayer);

        assertSame(currentInstance, active);
        var emptied = DungeonInstanceRegistry.reconcilePlayerMemberships(
                player, currentInstance, List.of(oldInstance, currentInstance), FakeInstance::members);
        assertEquals(List.of(oldInstance), emptied);
        assertFalse(oldInstance.members().contains(player));
        assertTrue(currentInstance.members().contains(player));
    }

    @Test
    void joiningNewGameDetachesEveryOldActiveGameBeforeJoin() {
        UUID player = UUID.randomUUID();
        var first = new FakeInstance();
        var second = new FakeInstance();
        var target = new FakeInstance();
        first.activePlayers().add(player);
        second.activePlayers().add(player);

        var detached = DungeonInstanceRegistry.reconcileActiveGameMemberships(
                player, target, List.of(first, target, second), FakeInstance::detachActivePlayer);

        assertEquals(List.of(first, second), detached);
        assertFalse(first.activePlayers().contains(player));
        assertFalse(second.activePlayers().contains(player));
        assertFalse(target.activePlayers().contains(player));
    }

    @Test
    void staleRegistryCleanupCanBeScopedWithoutRemovingNewMembership() {
        UUID player = UUID.randomUUID();
        var oldInstance = new FakeInstance();
        var currentInstance = new FakeInstance();
        oldInstance.members().add(player);
        currentInstance.members().add(player);

        assertTrue(DungeonInstanceRegistry.removeOwnedMembership(
                player, oldInstance, List.of(oldInstance, currentInstance), FakeInstance::members));
        assertFalse(oldInstance.members().contains(player));
        assertTrue(currentInstance.members().contains(player));
    }

    @Test
    void staleGameCleanupDoesNotOwnNewGameId() {
        String oldGameId = UUID.randomUUID().toString();
        String newGameId = UUID.randomUUID().toString();

        assertFalse(RavengardDungeonGame.ownsPlayerState(oldGameId, newGameId));
        assertTrue(RavengardDungeonGame.ownsPlayerState(newGameId, newGameId));
    }

    @Test
    void expiryRoutesLivePlayersAndNeverUnregistersTheirInstance() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var routed = new HashSet<UUID>();
        var unregistered = new AtomicBoolean();

        boolean result = DungeonInstanceRegistry.drainBeforeUnregister(
                List.of(first, second), routed::add, () -> unregistered.set(true));

        assertFalse(result);
        assertEquals(Set.of(first, second), routed);
        assertFalse(unregistered.get());
    }

    @Test
    void expiryUnregistersOnlyAfterTheInstanceIsDrained() {
        var unregistered = new AtomicBoolean();

        boolean result = DungeonInstanceRegistry.drainBeforeUnregister(
                List.<UUID>of(), ignored -> { }, () -> unregistered.set(true));

        assertTrue(result);
        assertTrue(unregistered.get());
    }

    @Test
    void configurationReservationKeepsRegisteredNonRetiringDungeonAlive() {
        UUID game = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        var assigned = new FakeInstance();

        assertTrue(DungeonInstanceRegistry.reserveRegisteredMembership(
                game, assigned, player, Map.of(game, assigned), ignored -> false, FakeInstance::members));
        assertTrue(assigned.members().contains(player));
    }

    @Test
    void configurationReservationIsIdempotent() {
        UUID game = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        var assigned = new FakeInstance();
        var registered = Map.of(game, assigned);

        assertTrue(DungeonInstanceRegistry.reserveRegisteredMembership(
                game, assigned, player, registered, ignored -> false, FakeInstance::members));
        assertTrue(DungeonInstanceRegistry.reserveRegisteredMembership(
                game, assigned, player, registered, ignored -> false, FakeInstance::members));
        assertEquals(1, assigned.members().size());
    }

    @Test
    void configurationReservationRejectsRetiringOrReplacedDungeon() {
        UUID game = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        var assigned = new FakeInstance();
        var replacement = new FakeInstance();

        assertFalse(DungeonInstanceRegistry.reserveRegisteredMembership(
                game, assigned, player, Map.of(game, assigned), ignored -> true, FakeInstance::members));
        assertFalse(DungeonInstanceRegistry.reserveRegisteredMembership(
                game, assigned, player, Map.of(game, replacement), ignored -> false, FakeInstance::members));
        assertFalse(assigned.members().contains(player));
    }

    private record FakeInstance(Set<UUID> members, Set<UUID> activePlayers) {
        private FakeInstance() {
            this(new HashSet<>(), new HashSet<>());
        }

        private boolean hasActivePlayer(UUID player) {
            return activePlayers.contains(player);
        }

        private boolean detachActivePlayer(UUID player) {
            return activePlayers.remove(player);
        }
    }
}
