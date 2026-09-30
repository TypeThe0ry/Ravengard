package net.swofty.type.ravengardgeneric.event.actions.player;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithBlockEvent;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithEntityEvent;
import net.minestom.server.instance.block.Block;
import net.swofty.type.ravengardgeneric.entity.RavengardArrow;
import net.swofty.type.ravengardgeneric.entity.animation.RavengardMobClip;
import net.swofty.type.ravengardgeneric.entity.mob.RavengardMob;
import net.swofty.type.ravengardgeneric.item.RavengardItemType;
import net.swofty.type.ravengardgeneric.item.statistics.RavengardItemStatistic;
import net.minestom.server.MinecraftServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionPlayerBowTest {
    @BeforeAll
    static void initializeMinestom() {
        MinecraftServer.init();
    }

    @AfterAll
    static void stopMinestom() {
        MinecraftServer.stopCleanly();
    }

    @Test
    void vanillaChargeCurveIsPreserved() {
        assertEquals(0, ActionPlayerBow.bowPower(0), 0.0001);
        assertEquals(0.416666, ActionPlayerBow.bowPower(10), 0.0001);
        assertEquals(1, ActionPlayerBow.bowPower(20), 0.0001);
        assertEquals(1, ActionPlayerBow.bowPower(100), 0.0001);
    }

    @Test
    void projectileHasVelocityBeforeItIsSpawned() {
        RavengardArrow arrow = new RavengardArrow(new Entity(EntityType.PLAYER), 9, false);

        assertTrue(arrow.hasPhysics());
        arrow.shoot(
                new net.minestom.server.coordinate.Pos(0, 1.62, 100),
                3.0,
                0.0
        );

        assertTrue(arrow.getVelocity().z() > 0.0);
        assertTrue(arrow.getVelocity().y() > 0.0);
    }

    @Test
    void rangedDamageUsesBoostAndCharge() {
        RavengardItemType type = new RavengardItemType("TEST_BOW");
        type.getStatistics().withBase(RavengardItemStatistic.RANGED_DAMAGE, 40);

        assertEquals(5, ActionPlayerBow.projectileDamage(type, 1, 0.5), 0.0001);
        assertEquals(15, ActionPlayerBow.projectileDamage(type, 1.5, 1), 0.0001);
    }

    @Test
    void currentBowConfigurationsFallBackToDamageStatistic() {
        RavengardItemType type = new RavengardItemType("CONFIG_STYLE_BOW");
        type.getStatistics().withBase(RavengardItemStatistic.DAMAGE, 46);

        assertEquals(11.5, ActionPlayerBow.projectileDamage(type, 1, 1), 0.0001);
    }

    @Test
    void collisionListenersUseTheEntityEventNode() throws Exception {
        assertEquals(net.swofty.type.generic.event.EventNodes.ENTITY,
                phasedEvent("onEntityCollision", ProjectileCollideWithEntityEvent.class).node());
        assertEquals(net.swofty.type.generic.event.EventNodes.ENTITY,
                phasedEvent("onBlockCollision", ProjectileCollideWithBlockEvent.class).node());
    }

    @Test
    void collisionDamagesMobAndRemovesArrow() throws Exception {
        RavengardMob mob = new RavengardMob(clip(), net.minestom.server.coordinate.Pos.ZERO);
        Entity shooter = new Entity(EntityType.PLAYER);
        RavengardArrow arrow = new RavengardArrow(shooter, 9, false);
        ActionPlayerBow listener = new ActionPlayerBow();

        listener.onEntityCollision(new ProjectileCollideWithEntityEvent(
                arrow, net.minestom.server.coordinate.Pos.ZERO, mob));

        assertEquals(91, mob.getHealth(), 0.0001);
        assertTrue(arrow.isRemoved());
        org.junit.jupiter.api.Assertions.assertSame(shooter, mob.getLastDamageSource().getAttacker());
    }

    @Test
    void arrowInFlightHitsAMobStandingDownRange() throws Exception {
        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        RavengardMob mob = new RavengardMob(clip(), new net.minestom.server.coordinate.Pos(2.5, 1, 9.5));
        mob.spawnMob(instance);
        Entity shooter = new Entity(EntityType.PLAYER);
        shooter.setInstance(instance, new net.minestom.server.coordinate.Pos(2.5, 1, 2.5)).join();
        RavengardArrow arrow = new RavengardArrow(shooter, 9, false);
        arrow.setInstance(instance, new net.minestom.server.coordinate.Pos(2.5, 2.6, 2.5)).join();
        arrow.shoot(new net.minestom.server.coordinate.Pos(2.5, 2.0, 9.5), 3.0, 0.0);

        long time = System.currentTimeMillis();
        for (int tick = 0; tick < 40 && !arrow.isRemoved(); tick++) {
            final long now = time + tick * 50L;
            instance.tick(now);
            instance.getEntities().forEach(entity -> entity.tick(now));
        }

        assertTrue(mob.getHealth() < 100, "arrow ended at " + arrow.getPosition() + " removed=" + arrow.isRemoved());

        mob.removeRig();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void nonMobCollisionIsCancelledSoPartyMembersDoNotStopTheArrow() {
        RavengardArrow arrow = new RavengardArrow(new Entity(EntityType.PLAYER), 9, false);
        ProjectileCollideWithEntityEvent event = new ProjectileCollideWithEntityEvent(
                arrow, net.minestom.server.coordinate.Pos.ZERO, new Entity(EntityType.ZOMBIE));

        new ActionPlayerBow().onEntityCollision(event);

        assertTrue(event.isCancelled());
        assertFalse(arrow.isRemoved());
    }

    @Test
    void blockCollisionCleansArrowUp() {
        RavengardArrow arrow = new RavengardArrow(new Entity(EntityType.PLAYER), 9, false);

        new ActionPlayerBow().onBlockCollision(new ProjectileCollideWithBlockEvent(
                arrow, net.minestom.server.coordinate.Pos.ZERO, Block.STONE));

        assertTrue(arrow.isRemoved());
    }

    private static RavengardMobClip clip() throws Exception {
        RavengardMobClip clip = new RavengardMobClip();
        set(clip, "name", "bow_target");
        set(clip, "health", 100d);
        set(clip, "walkSpeed", 0d);
        return clip;
    }

    private static net.swofty.type.generic.event.phase.PhasedEvent phasedEvent(
            String name, Class<?> eventType) throws Exception {
        Method method = ActionPlayerBow.class.getMethod(name, eventType);
        return method.getAnnotation(net.swofty.type.generic.event.phase.PhasedEvent.class);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
