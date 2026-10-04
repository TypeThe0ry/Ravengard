package net.swofty.type.ravengarddungeon.game;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.swofty.type.ravengarddungeon.config.RavengardDungeonConfig;
import net.swofty.type.ravengarddungeon.generator.DungeonTemplate;
import net.swofty.type.ravengarddungeon.interactables.DungeonExtraction;
import net.swofty.type.ravengarddungeon.interactables.InteractableRegistry;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import net.swofty.type.game.game.GameState;
import net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionPortalsTest {
    @BeforeAll
    static void initializeMinestom() {
        MinecraftServer.init();
    }

    @AfterAll
    static void stopMinestom() {
        MinecraftServer.stopCleanly();
    }

    @Test
    void portalsOpenInWavesAsTheDeathWallClosesAndEverySiteOpensOnce() {
        List<Pos> anchors = DungeonInstanceRegistry.officialExtractionAnchors();
        for (long seed : new long[]{1L, -42L, 777_777L}) {
            var timeline = RavengardDeathWall.timeline(5.5, -0.75, 264.0, anchors, seed);
            var waves = ExtractionPortals.plan(anchors, timeline,
                    anchor -> ExtractionPortals.setAround(anchor, DungeonTemplate.get()::blockAt));

            assertEquals(RavengardDeathWall.STAGE_COUNT, waves.size());
            Set<Pos> opened = new HashSet<>();
            for (int stage = 1; stage <= waves.size(); stage++) {
                var wave = waves.get(stage - 1);
                assertEquals(RavengardDeathWall.stageWarningAt(stage), wave.opensAt());
                assertEquals(ExtractionPortals.SITES_PER_WAVE, wave.sites().size());
                for (var site : wave.sites()) {
                    assertTrue(opened.add(site.anchor()), "site opened twice: " + site.anchor());
                    assertTrue(site.portals().size() == 1 || site.portals().size() == ExtractionPortals.SET_SIZE);
                }
            }
            assertEquals(anchors.size(), opened.size());

            var finalCenter = timeline.targetCenter(RavengardDeathWall.STAGE_COUNT);
            assertTrue(waves.stream().flatMap(wave -> wave.sites().stream())
                    .anyMatch(site -> site.anchor().x() == finalCenter.x() && site.anchor().z() == finalCenter.z()),
                    "the anchor the Death Wall closes on must get a portal");
        }
    }

    @Test
    void somePortalsOnTheOfficialMapOpenAsSetsOfThree() {
        List<Pos> anchors = DungeonInstanceRegistry.officialExtractionAnchors();
        long sets = anchors.stream()
                .filter(anchor -> !ExtractionPortals.setAround(anchor, DungeonTemplate.get()::blockAt).isEmpty())
                .count();
        assertTrue(sets > 0, "no cyan room has room for three portals");
    }

    @Test
    void aSetIsThreeArchesSideBySideAcrossThePortal() {
        DungeonInstanceRegistry.BlockLookup flat = (x, y, z) -> y == 64 ? Block.STONE : Block.AIR;

        assertEquals(List.of(new Pos(-3.5, 65, 0.5), new Pos(0.5, 65, 0.5), new Pos(4.5, 65, 0.5)),
                ExtractionPortals.setAround(new Pos(0.5, 65, 0.5, 0f, 0f), flat).stream()
                        .map(portal -> new Pos(portal.x(), portal.y(), portal.z())).toList());
        assertEquals(List.of(new Pos(0.5, 65, -3.5), new Pos(0.5, 65, 0.5), new Pos(0.5, 65, 4.5)),
                ExtractionPortals.setAround(new Pos(0.5, 65, 0.5, 90f, 0f), flat).stream()
                        .map(portal -> new Pos(portal.x(), portal.y(), portal.z())).toList());

        DungeonInstanceRegistry.BlockLookup corridor = (x, y, z) ->
                y == 64 || Math.abs(x) > 2 ? Block.STONE : Block.AIR;
        assertTrue(ExtractionPortals.setAround(new Pos(0.5, 65, 0.5, 0f, 0f), corridor).isEmpty());
    }

    @Test
    void aPortalOpensOnScheduleAndClosesOnceUsed() {
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        try {
            var config = new RavengardDungeonConfig("portal-test", "Portal test", "unused.polar", null, List.of());
            var game = new RavengardDungeonGame(instance, config);
            Pos anchor = new Pos(2.5, 65, -3.5, 0f, 0f);
            var portals = new ExtractionPortals(instance, game, List.of(
                    new ExtractionPortals.Wave(300, List.of(new ExtractionPortals.Site(anchor, List.of(anchor))))));

            portals.tick(299);
            assertEquals(0, portals.openCount());
            assertFalse(hasPortalEntities(instance));

            portals.tick(300);
            assertEquals(1, portals.openCount());
            assertEquals(List.of(anchor), portals.openAnchors());
            assertTrue(hasPortalEntities(instance));
            DungeonExtraction portal = (DungeonExtraction) InteractableRegistry.byInteraction(
                    instance.getEntities().stream()
                            .filter(entity -> entity.getEntityType() == EntityType.INTERACTION)
                            .findFirst().orElseThrow());

            portals.used(portal);
            assertEquals(0, portals.openCount());
            assertFalse(hasPortalEntities(instance));

            portals.tick(10_000);
            assertEquals(0, portals.openCount());
        } finally {
            InteractableRegistry.removeInstance(instance);
            MinecraftServer.getInstanceManager().unregisterInstance(instance);
        }
    }

    @Test
    void escapingThroughOnePortalOfASetClosesOnlyThatPortal() {
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        try {
            var config = new RavengardDungeonConfig("portal-set-test", "Portal set test", "unused.polar", null, List.of());
            var game = new RavengardDungeonGame(instance, config) {
                @Override
                public GameState getState() {
                    return GameState.IN_PROGRESS;
                }

                @Override
                public boolean extract(RavengardDungeonPlayer player) {
                    return true;
                }
            };
            List<Pos> set = List.of(new Pos(-3.5, 65, 0.5), new Pos(0.5, 65, 0.5), new Pos(4.5, 65, 0.5));
            var portals = new ExtractionPortals(instance, game, List.of(
                    new ExtractionPortals.Wave(0, List.of(new ExtractionPortals.Site(set.get(1), set)))));
            portals.tick(0);
            assertEquals(3, portals.openCount());

            DungeonExtraction middle = instance.getEntities().stream()
                    .filter(entity -> entity.getEntityType() == EntityType.INTERACTION)
                    .map(entity -> (DungeonExtraction) InteractableRegistry.byInteraction(entity))
                    .filter(portal -> portal.anchor().equals(set.get(1)))
                    .findFirst().orElseThrow();
            RavengardDungeonPlayer mercenary = new RavengardDungeonPlayer(
                    new DummyConnection(), new GameProfile(UUID.randomUUID(), "Mercenary"));
            middle.open(mercenary);

            assertEquals(2, portals.openCount());
            assertEquals(List.of(set.get(0), set.get(2)), portals.openAnchors());
            assertTrue(middle.getInteraction().isRemoved());
            assertFalse(middle.channelStillValid(mercenary));
        } finally {
            InteractableRegistry.removeInstance(instance);
            MinecraftServer.getInstanceManager().unregisterInstance(instance);
        }
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

    private static boolean hasPortalEntities(InstanceContainer instance) {
        return instance.getEntities().stream().anyMatch(entity -> !entity.isRemoved()
                && (entity.getEntityType() == EntityType.INTERACTION || entity.getEntityType() == EntityType.ITEM_DISPLAY));
    }
}
