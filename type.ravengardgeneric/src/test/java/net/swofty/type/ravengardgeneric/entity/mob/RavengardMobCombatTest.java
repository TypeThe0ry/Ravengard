package net.swofty.type.ravengardgeneric.entity.mob;

import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import net.swofty.type.ravengardgeneric.entity.animation.RavengardMobClip;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RavengardMobCombatTest {
    @BeforeAll
    static void initializeMinestom() {
        MinecraftServer.init();
    }

    @AfterAll
    static void stopMinestom() {
        MinecraftServer.stopCleanly();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void attackListenerNodeActuallyReceivesEntityAttackEvents() throws Exception {
        var method = net.swofty.type.ravengardgeneric.event.actions.player.ActionPlayerCombat.class
                .getMethod("onAttack", net.minestom.server.event.entity.EntityAttackEvent.class);
        var annotation = method.getAnnotation(net.swofty.type.generic.event.phase.PhasedEvent.class);
        net.minestom.server.event.EventNode node = annotation.node().newNode("combat-route-test", 0);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        node.addListener(net.minestom.server.event.entity.EntityAttackEvent.class, event -> calls.incrementAndGet());
        var root = net.minestom.server.event.EventNode.all("combat-root");
        root.addChild(node);
        root.call(new net.minestom.server.event.entity.EntityAttackEvent(
                new Entity(EntityType.ZOMBIE), new Entity(EntityType.ZOMBIE)));
        assertEquals(1, calls.get());
    }
    @Test
    void meleeDamageKeepsTheMobAsTheAttacker() throws Exception {
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        RavengardMob mob = new RavengardMob(
                clip(),
                new net.minestom.server.coordinate.Pos(2.5, 1, 2.5));
        mob.spawnMob(instance);
        LivingEntity target = new LivingEntity(EntityType.ZOMBIE);
        target.setInstance(instance, new net.minestom.server.coordinate.Pos(3.5, 1, 2.5)).join();
        target.setHealth(40);
        float before = target.getHealth();

        mob.attack(target, false);

        assertEquals(before - 2, target.getHealth(), 0.001);
        assertSame(mob, target.getLastDamageSource().getAttacker());
        assertSame(mob, target.getLastDamageSource().getSource());

        mob.removeRig();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void lethalDamageStartsTheDeathAnimationAndRemovesTheRig() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "health", 3d);
        RavengardMob mob = new RavengardMob(clip, new net.minestom.server.coordinate.Pos(0, 0, 0));

        mob.damage(net.minestom.server.entity.damage.DamageType.PLAYER_ATTACK, 3);
        mob.tickRig();

        assertEquals(0, mob.getHealth(), 0.001,
                "lethal custom damage must update the LivingEntity health state");
        assertTrue(mob.isRemoved());
    }

    @Test
    void lethalDamageRejectsFurtherHitsBeforeDeathAnimationCompletes() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "health", 3d);
        RavengardMobClip.Animation death = new RavengardMobClip.Animation();
        set(death, "loop", true);
        set(death, "frames", List.of(Map.of(), Map.of()));
        set(clip, "animations", Map.of("death", death));
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(2.5, 1, 2.5));
        mob.spawnMob(instance);

        mob.damage(net.minestom.server.entity.damage.DamageType.PLAYER_ATTACK, 3);

        assertEquals(0, mob.getHealth(), 0.001);
        assertTrue(mob.isDead());
        assertFalse(mob.damage(net.minestom.server.entity.damage.DamageType.PLAYER_ATTACK, 3),
                "a dead mob must not accept repeated client attacks");
        for (int tick = 0; tick < 3; tick++) mob.tickRig();
        assertTrue(mob.isRemoved(), "death animation must finish even if the clip is marked looping");
        mob.removeRig();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void cancelledLethalDamageCannotBypassTheDamageEvent() throws Exception {
        RavengardMob mob = new RavengardMob(clip(), net.minestom.server.coordinate.Pos.ZERO);
        var listener = net.minestom.server.event.EventListener.of(
                net.minestom.server.event.entity.EntityDamageEvent.class,
                event -> {
                    if (event.getEntity() == mob) event.setCancelled(true);
                });
        MinecraftServer.getGlobalEventHandler().addListener(listener);
        try {
            org.junit.jupiter.api.Assertions.assertFalse(mob.damage(
                    net.minestom.server.entity.damage.DamageType.PLAYER_ATTACK, 100));
            assertEquals(100, mob.getHealth(), 0.001);
            org.junit.jupiter.api.Assertions.assertFalse(mob.isDead());
        } finally {
            MinecraftServer.getGlobalEventHandler().removeListener(listener);
            mob.removeRig();
        }
    }

    @Test
    void attributedDamageUsesTheSameDeathAndLootLifecycle() throws Exception {
        AtomicInteger callbacks = new AtomicInteger();
        RavengardMob mob = new RavengardMob(clip(), net.minestom.server.coordinate.Pos.ZERO,
                ignored -> callbacks.incrementAndGet());
        Entity attacker = new Entity(EntityType.ZOMBIE);
        mob.damage(net.minestom.server.entity.damage.Damage.fromEntity(attacker, 40));
        assertEquals(60, mob.getHealth(), 0.001);
        mob.damage(net.minestom.server.entity.damage.Damage.fromEntity(attacker, 60));
        assertEquals(0, mob.getHealth(), 0.001);
        assertEquals(1, callbacks.get());
        assertTrue(mob.isRemoved());
    }

    @Test
    void lethalDamageInvokesTheLootCallbackExactlyOnce() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "health", 3d);
        AtomicInteger callbacks = new AtomicInteger();
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(0, 0, 0), ignored -> callbacks.incrementAndGet());

        mob.damage(net.minestom.server.entity.damage.DamageType.PLAYER_ATTACK, 3);
        mob.damage(net.minestom.server.entity.damage.DamageType.PLAYER_ATTACK, 3);

        assertEquals(1, callbacks.get());
    }

    @Test
    void spawnedMobBodyIsTheScaledCombatTarget() throws Exception {
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        RavengardMob mob = new RavengardMob(clip(), new net.minestom.server.coordinate.Pos(2.5, 1, 2.5));

        mob.spawnMob(instance);

        assertTrue(instance.getEntities().stream().noneMatch(entity -> entity.getEntityType() == EntityType.INTERACTION));
        assertSame(mob, RavengardMob.combatTarget(mob));
        assertEquals(RavengardMob.COMBAT_SCALE,
                mob.getAttributeValue(net.minestom.server.entity.attribute.Attribute.SCALE), 0.001);
        assertEquals(RavengardMob.COMBAT_HITBOX_WIDTH, mob.getBoundingBox().width(), 0.001);
        assertEquals(RavengardMob.COMBAT_HITBOX_HEIGHT, mob.getBoundingBox().height(), 0.001);
        assertTrue(mob.getBoundingBox().width() < 1, "a body wider than a block cannot climb steps");

        mob.removeRig();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void deadMobStaysLongEnoughToFinishItsDeathAnimation() throws Exception {
        RavengardMobClip clip = clip();
        RavengardMobClip.Animation death = new RavengardMobClip.Animation();
        java.util.List<java.util.Map<String, RavengardMobClip.Keyframe>> frames = new java.util.ArrayList<>();
        for (int frame = 0; frame < 31; frame++) frames.add(java.util.Map.of());
        set(death, "frames", frames);
        set(clip, "animations", java.util.Map.of("death", death));

        RavengardMob mob = new RavengardMob(clip, new net.minestom.server.coordinate.Pos(0, 1, 0));

        assertTrue(mob.getRemovalAnimationDelay() >= 31 * 50);
    }

    @Test
    void mobsIgnorePlayersTheTargetGateRejects() throws Exception {
        Player player = new Player(new DummyConnection(),
                new GameProfile(UUID.randomUUID(), "waiting"));
        assertTrue(RavengardMob.isTargetable(player));
        RavengardMob.setTargetGate(candidate -> false);
        try {
            assertFalse(RavengardMob.isTargetable(player));
        } finally {
            RavengardMob.setTargetGate(candidate -> true);
        }
    }

    @Test
    void playerMeleeDamagesTheMobAndIsRecordedAsTheAttacker() throws Exception {
        RavengardMobClip clip = clip();
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        var player = new net.swofty.type.ravengardgeneric.user.RavengardPlayer(new DummyConnection(),
                new GameProfile(UUID.randomUUID(), "swordsman"));
        player.setInstance(instance, new net.minestom.server.coordinate.Pos(4.5, 1, 2.5)).join();
        RavengardMob mob = new RavengardMob(clip, new net.minestom.server.coordinate.Pos(2.5, 1, 2.5));
        mob.spawnMob(instance);
        float before = mob.getHealth();

        new net.swofty.type.ravengardgeneric.event.actions.player.ActionPlayerCombat()
                .onAttack(new net.minestom.server.event.entity.EntityAttackEvent(player, mob));

        assertTrue(mob.getHealth() < before);
        assertSame(player, mob.getLastDamageSource().getAttacker());

        mob.removeRig();
        player.remove();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void mobTurnsOnAPlayerWhoHitsItFromBeyondSightRange() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "walkSpeed", 0.13d);
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        instance.loadChunk(1, 0).join();
        Player player = new Player(new DummyConnection(),
                new GameProfile(UUID.randomUUID(), "archer"));
        player.setInstance(instance, new net.minestom.server.coordinate.Pos(17.5, 1, 8.5)).join();
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(2.5, 1, 8.5));
        mob.spawnMob(instance);

        long time = System.currentTimeMillis();
        for (int tick = 0; tick < 5; tick++) {
            final long now = time + tick * 50L;
            instance.getEntities().forEach(entity -> entity.update(now));
            mob.tickRig();
        }
        assertNull(mob.getTarget());

        mob.damage(new net.minestom.server.entity.damage.EntityDamage(player, 1));
        for (int tick = 5; tick < 10; tick++) {
            final long now = time + tick * 50L;
            instance.getEntities().forEach(entity -> entity.update(now));
            mob.tickRig();
        }
        assertSame(player, mob.getTarget());

        mob.removeRig();
        player.remove();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void mobDropsAnAttackerWhoBecomesASpectator() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "walkSpeed", 0.13d);
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        Player player = new Player(new DummyConnection(),
                new GameProfile(UUID.randomUUID(), "fallen"));
        player.setInstance(instance, new net.minestom.server.coordinate.Pos(6.5, 1, 8.5)).join();
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(2.5, 1, 8.5));
        mob.spawnMob(instance);

        mob.damage(new net.minestom.server.entity.damage.EntityDamage(player, 1));
        player.setGameMode(net.minestom.server.entity.GameMode.SPECTATOR);
        float before = player.getHealth();
        long time = System.currentTimeMillis();
        for (int tick = 0; tick < 10; tick++) {
            final long now = time + tick * 50L;
            instance.getEntities().forEach(entity -> entity.update(now));
            mob.tickRig();
        }
        assertNull(mob.getTarget());
        mob.attack(player, true);
        assertEquals(before, player.getHealth());

        mob.removeRig();
        player.remove();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void mobileMobAcquiresAndApproachesAPlayer() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "walkSpeed", 0.13d);
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        Player player = new Player(new DummyConnection(),
                new GameProfile(UUID.randomUUID(), "target"));
        player.setInstance(instance, new net.minestom.server.coordinate.Pos(10.5, 1, 8.5)).join();
        player.getAttribute(net.minestom.server.entity.attribute.Attribute.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(2.5, 1, 8.5));
        mob.spawnMob(instance);
        double initial = mob.getPosition().distance(player.getPosition());

        long time = System.currentTimeMillis();
        for (int tick = 0; tick < 120; tick++) {
            final long now = time + tick * 50L;
            instance.tick(now);
            instance.getEntities().forEach(entity -> entity.update(now));
            mob.tickRig();
        }

        assertSame(player, mob.getTarget());
        assertTrue(mob.getPosition().distance(player.getPosition()) < initial);

        player.setGameMode(net.minestom.server.entity.GameMode.SPECTATOR);
        long later = time + 120 * 50L;
        instance.tick(later);
        instance.getEntities().forEach(entity -> entity.update(later));
        mob.tickRig();
        assertNull(mob.getTarget());
        assertFalse(RavengardMob.isTargetable(player));

        mob.removeRig();
        player.remove();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void mobKeepsAttackingAPlayerItWalkedUpTo() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "walkSpeed", 0.13d);
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        Player player = new Player(new DummyConnection(),
                new GameProfile(UUID.randomUUID(), "standing"));
        player.setInstance(instance, new net.minestom.server.coordinate.Pos(8.5, 1, 8.5)).join();
        player.getAttribute(net.minestom.server.entity.attribute.Attribute.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(2.5, 1, 8.5));
        mob.spawnMob(instance);

        int hits = 0;
        float last = player.getHealth();
        long time = System.currentTimeMillis();
        for (int tick = 0; tick < 400; tick++) {
            final long now = time + tick * 50L;
            instance.tick(now);
            instance.getEntities().forEach(entity -> entity.update(now));
            mob.tickRig();
            player.teleport(new net.minestom.server.coordinate.Pos(8.5, 1, 8.5)).join();
            player.setVelocity(net.minestom.server.coordinate.Vec.ZERO);
            if (player.getHealth() < last) hits++;
            last = player.getHealth();
        }

        assertTrue(hits >= 3, "hits=" + hits + " distance="
                + mob.getPosition().distance(player.getPosition()));

        mob.removeRig();
        player.remove();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void mobReachesTheJumpNodeAtTheFootOfAStep() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "walkSpeed", 0.13d);
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> {
            unit.modifier().fillHeight(0, 1, Block.STONE);
            var start = unit.absoluteStart();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (start.blockX() + x >= 6) {
                        unit.modifier().setBlock(start.blockX() + x, 1, start.blockZ() + z, Block.STONE);
                    }
                }
            }
        });
        instance.loadChunk(0, 0).join();
        Player player = new Player(new DummyConnection(),
                new GameProfile(UUID.randomUUID(), "uphill"));
        player.setInstance(instance, new net.minestom.server.coordinate.Pos(8.5, 2, 8.5)).join();
        player.getAttribute(net.minestom.server.entity.attribute.Attribute.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(2.5, 1, 8.5));
        mob.spawnMob(instance);

        long time = System.currentTimeMillis();
        for (int tick = 0; tick < 400; tick++) {
            final long now = time + tick * 50L;
            instance.tick(now);
            instance.getEntities().forEach(entity -> entity.update(now));
            mob.tickRig();
            player.teleport(new net.minestom.server.coordinate.Pos(8.5, 2, 8.5)).join();
            player.setVelocity(net.minestom.server.coordinate.Vec.ZERO);
        }

        // a body wider than a block is held back by the step before it gets to the node it jumps from
        assertTrue(mob.getPosition().x() >= 5.5, "mob stopped at " + mob.getPosition());

        mob.removeRig();
        player.remove();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void capturedDemonImpActsAsAStationaryFireCaster() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "name", "demon_imp");
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        Player player = new Player(new DummyConnection(),
                new GameProfile(UUID.randomUUID(), "caster-target"));
        player.setInstance(instance, new net.minestom.server.coordinate.Pos(8.5, 1, 8.5)).join();
        player.setHealth(20);
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(2.5, 1, 8.5));
        mob.spawnMob(instance);

        mob.tickRig();

        assertSame(player, mob.getTarget());
        assertEquals(18.5, player.getHealth(), 0.001);
        assertSame(mob, player.getLastDamageSource().getAttacker());
        assertEquals(new net.minestom.server.coordinate.Pos(2.5, 1, 8.5), mob.getPosition());
        mob.removeRig();
        player.remove();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void eliteAppearanceReusesCapturedAnimationButSelectsOfficialEliteModels() {
        assertEquals("hypixel_ravengard:entity/skeleton_archer_elite/body",
                RavengardMob.appearanceModel("skeleton_archer_elite", "skeleton_archer", "body",
                        "hypixel_ravengard:entity/skeleton_archer/body"));
        assertEquals("hypixel_ravengard:entity/skeleton_knight/eyes",
                RavengardMob.appearanceModel("skeleton_knight_elite", "skeleton_knight", "eyes",
                        "hypixel_ravengard:entity/skeleton_knight/eyes"));
        assertEquals("hypixel_ravengard:entity/skeleton_warrior_elite/leftarm",
                RavengardMob.appearanceModel("skeleton_warrior_elite", "skeleton_warrior", "armleft",
                        "hypixel_ravengard:entity/skeleton_warrior/armleft"));
        assertEquals("hypixel_ravengard:entity/skeleton_warrior/eyes",
                RavengardMob.appearanceModel("skeleton_warrior_elite", "skeleton_warrior", "eyes",
                        "hypixel_ravengard:entity/skeleton_warrior/eyes"));
        assertEquals("hypixel_ravengard:entity/demon_grunt/body",
                RavengardMob.appearanceModel("demon_grunt", "demon_grunt", "body",
                        "hypixel_ravengard:entity/demon_grunt/body"));
        assertEquals("hypixel_ravengard:entity/bottle_mimic_short_green/topcap",
                RavengardMob.appearanceModel("bottle_mimic_short_green",
                        "bottle_mimic_short_yellow", "topcap",
                        "hypixel_ravengard:entity/bottle_mimic_short_yellow/topcap"));
        assertEquals("hypixel_ravengard:entity/bottle_mimic_tall_pink/eye",
                RavengardMob.appearanceModel("bottle_mimic_tall_pink",
                        "bottle_mimic_short_yellow", "eye",
                        "hypixel_ravengard:entity/bottle_mimic_short_yellow/eye"));
    }

    @Test
    void runtimeMobRetainsItsSelectedAppearanceVariant() throws Exception {
        RavengardMob mob = new RavengardMob(clip(), net.minestom.server.coordinate.Pos.ZERO,
                "skeleton_archer_elite");

        assertEquals("skeleton_archer_elite", mob.appearanceType());
        assertEquals("Skeleton Archer Elite", mob.displayName());
    }

    @Test
    void woodenMimicRidesTheSilverMimicRigWithoutItsEyes() {
        for (String part : List.of("bottom", "tongue", "top")) {
            assertEquals("hypixel_ravengard:entity/normal_mimic/" + part,
                    RavengardMob.appearanceModel("normal_mimic", "silver_mimic", part,
                            "hypixel_ravengard:entity/silver_mimic/" + part));
        }
        for (String part : List.of("eye_purple", "eyesocket")) {
            assertNull(RavengardMob.appearanceModel("normal_mimic", "silver_mimic", part,
                    "hypixel_ravengard:entity/silver_mimic/" + part));
            assertEquals("hypixel_ravengard:entity/silver_mimic/" + part,
                    RavengardMob.appearanceModel("silver_mimic", "silver_mimic", part,
                            "hypixel_ravengard:entity/silver_mimic/" + part));
        }
    }

    @Test
    void rigTurnsWithTheMobWhileItsDisplaysStayUnrotated() throws Exception {
        RavengardMobClip clip = clip();
        RavengardMobClip.Part body = part("body", "hypixel_ravengard:entity/skeleton_knight/body");
        set(body, "t", new float[]{0, 0, 1});
        set(body, "r", new float[]{0, 0, 0, 1});
        set(clip, "parts", List.of(body));
        RavengardMobClip.Animation idle = new RavengardMobClip.Animation();
        set(idle, "loop", true);
        set(idle, "frames", List.of(Map.of(), Map.of()));
        set(clip, "animations", Map.of("idle", idle));
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        // yaw 90 looks down -X
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(2.5, 1, 2.5, 90, 0));
        mob.spawnMob(instance);
        mob.tickRig();

        Entity display = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY)
                .findFirst().orElseThrow();
        ItemDisplayMeta meta = (ItemDisplayMeta) display.getEntityMeta();
        assertEquals(0f, display.getPosition().yaw(), 1.0e-4);
        assertEquals(-1.0, meta.getTranslation().x(), 1.0e-4);
        assertEquals(0.0, meta.getTranslation().z(), 1.0e-4);
        float[] rotation = meta.getLeftRotation();
        double forwardX = 2 * (rotation[0] * rotation[2] + rotation[3] * rotation[1]);
        double forwardZ = 1 - 2 * (rotation[0] * rotation[0] + rotation[1] * rotation[1]);
        assertEquals(-1.0, forwardX, 1.0e-4);
        assertEquals(0.0, forwardZ, 1.0e-4);

        mob.removeRig();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void meleeMobStopsInFrontOfItsTargetInsteadOfWalkingIntoIt() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "walkSpeed", 0.13d);
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        Player player = new Player(new DummyConnection(),
                new GameProfile(UUID.randomUUID(), "target"));
        player.setInstance(instance, new net.minestom.server.coordinate.Pos(8.5, 1, 8.5)).join();
        player.getAttribute(net.minestom.server.entity.attribute.Attribute.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(2.5, 1, 8.5));
        mob.spawnMob(instance);

        double closest = Double.MAX_VALUE;
        long time = System.currentTimeMillis();
        for (int tick = 0; tick < 200; tick++) {
            final long now = time + tick * 50L;
            instance.tick(now);
            instance.getEntities().forEach(entity -> entity.update(now));
            mob.tickRig();
            closest = Math.min(closest, mob.getPosition().distance(player.getPosition()));
        }

        assertTrue(closest < 1.8, "closest=" + closest);
        assertTrue(closest > 0.8, "closest=" + closest);

        mob.removeRig();
        player.remove();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void mobReachesATargetAcrossPlantsAndSlabs() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "walkSpeed", 0.0856d);
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        for (int z = 7; z <= 9; z++) {
            instance.setBlock(5, 1, z, Block.DEAD_TUBE_CORAL_FAN);
            for (int x = 6; x <= 11; x++) instance.setBlock(x, 1, z, Block.POLISHED_ANDESITE_SLAB);
        }
        Player player = new Player(new DummyConnection(),
                new GameProfile(UUID.randomUUID(), "target"));
        player.setInstance(instance, new net.minestom.server.coordinate.Pos(9.5, 1.5, 8.5)).join();
        player.getAttribute(net.minestom.server.entity.attribute.Attribute.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(1.5, 1, 8.5));
        mob.spawnMob(instance);

        double closest = Double.MAX_VALUE;
        long time = System.currentTimeMillis();
        for (int tick = 0; tick < 300; tick++) {
            final long now = time + tick * 50L;
            instance.tick(now);
            mob.tick(now);
            mob.tickRig();
            closest = Math.min(closest, mob.getPosition().distance(player.getPosition()));
        }

        assertTrue(closest < 1.8, "closest=" + closest + " mob=" + mob.getPosition());

        mob.removeRig();
        player.remove();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void idleMobsSpawnedOnOneSpotSpreadOutWithTheirRigs() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "walkSpeed", 0.11d);
        set(clip, "parts", List.of(part("body", "hypixel_ravengard:entity/skeleton_knight/body")));
        RavengardMobClip.Animation idle = new RavengardMobClip.Animation();
        set(idle, "loop", true);
        set(idle, "frames", List.of(Map.of(), Map.of()));
        set(clip, "animations", Map.of("idle", idle));
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        List<RavengardMob> pack = new java.util.ArrayList<>();
        for (int index = 0; index < 4; index++) {
            RavengardMob mob = new RavengardMob(clip, new net.minestom.server.coordinate.Pos(5.5, 1, 8.5));
            mob.spawnMob(instance);
            pack.add(mob);
        }

        long time = System.currentTimeMillis();
        for (int tick = 0; tick < 60; tick++) {
            final long now = time + tick * 50L;
            instance.tick(now);
            for (RavengardMob mob : pack) {
                mob.tick(now);
                mob.tickRig();
            }
        }

        List<Entity> displays = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY).toList();
        for (int first = 0; first < displays.size(); first++) {
            for (int second = first + 1; second < displays.size(); second++) {
                double apart = displays.get(first).getPosition().distance(displays.get(second).getPosition());
                assertTrue(apart > 0.6, "rigs " + first + "/" + second + " apart=" + apart);
            }
        }

        pack.forEach(RavengardMob::removeRig);
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void mobsChasingTheSameTargetDoNotEndUpInsideEachOther() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "walkSpeed", 0.11d);
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        Player player = new Player(new DummyConnection(),
                new GameProfile(UUID.randomUUID(), "target"));
        player.setInstance(instance, new net.minestom.server.coordinate.Pos(9.5, 1, 8.5)).join();
        player.getAttribute(net.minestom.server.entity.attribute.Attribute.MAX_HEALTH).setBaseValue(100000);
        player.setHealth(100000);
        set(clip, "parts", List.of(part("body", "hypixel_ravengard:entity/skeleton_knight/body")));
        RavengardMobClip.Animation idle = new RavengardMobClip.Animation();
        set(idle, "loop", true);
        set(idle, "frames", List.of(Map.of(), Map.of()));
        set(clip, "animations", Map.of("idle", idle));
        List<RavengardMob> pack = new java.util.ArrayList<>();
        for (int index = 0; index < 3; index++) {
            RavengardMob mob = new RavengardMob(clip, new net.minestom.server.coordinate.Pos(2.5, 1, 8.5));
            mob.spawnMob(instance);
            pack.add(mob);
        }

        long time = System.currentTimeMillis();
        for (int tick = 0; tick < 200; tick++) {
            final long now = time + tick * 50L;
            instance.tick(now);
            for (RavengardMob mob : pack) {
                mob.tick(now);
                mob.tickRig();
            }
            player.teleport(new net.minestom.server.coordinate.Pos(9.5, 1, 8.5)).join();
        }

        for (int first = 0; first < pack.size(); first++) {
            assertTrue(pack.get(first).getPosition().distance(player.getPosition()) < 2.5,
                    "mob " + first + " at " + pack.get(first).getPosition());
            for (int second = first + 1; second < pack.size(); second++) {
                double apart = pack.get(first).getPosition().distance(pack.get(second).getPosition());
                assertTrue(apart > 0.6, first + "/" + second + " apart=" + apart);
            }
        }

        // the visible rig has to move with its carrier, not stay where the pack first met
        List<Entity> displays = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY).toList();
        assertEquals(pack.size(), displays.size());
        for (Entity display : displays) {
            assertTrue(pack.stream().anyMatch(mob -> Math.hypot(
                            mob.getPosition().x() - display.getPosition().x(),
                            mob.getPosition().z() - display.getPosition().z()) < 0.15),
                    "stray rig part at " + display.getPosition());
        }

        pack.forEach(RavengardMob::removeRig);
        player.remove();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void aRigWithoutAnimationsStillTurnsWithItsMob() throws Exception {
        RavengardMobClip clip = clip();
        RavengardMobClip.Part body = part("body", "hypixel_ravengard:entity/training_dummy/body");
        set(body, "t", new float[]{0, 0, 1});
        set(body, "r", new float[]{0, 0, 0, 1});
        set(clip, "parts", List.of(body));
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(2.5, 1, 2.5, 90, 0));
        mob.spawnMob(instance);
        mob.tickRig();

        ItemDisplayMeta meta = (ItemDisplayMeta) instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY)
                .findFirst().orElseThrow().getEntityMeta();
        assertEquals(-1.0, meta.getTranslation().x(), 1.0e-4);
        assertEquals(0.0, meta.getTranslation().z(), 1.0e-4);

        mob.removeRig();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void aMobShovedOutOfReachWalksBackToAStationaryTarget() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "walkSpeed", 0.13d);
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        Player player = new Player(new DummyConnection(),
                new GameProfile(UUID.randomUUID(), "target"));
        player.setInstance(instance, new net.minestom.server.coordinate.Pos(8.5, 1, 8.5)).join();
        player.getAttribute(net.minestom.server.entity.attribute.Attribute.MAX_HEALTH).setBaseValue(100000);
        player.setHealth(100000);
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(4.5, 1, 8.5));
        mob.spawnMob(instance);

        long time = System.currentTimeMillis();
        int tick = 0;
        for (; tick < 120; tick++) {
            final long now = time + tick * 50L;
            instance.tick(now);
            mob.tick(now);
            mob.tickRig();
            player.teleport(new net.minestom.server.coordinate.Pos(8.5, 1, 8.5)).join();
        }
        assertTrue(mob.getPosition().distance(player.getPosition()) < 1.8, "never arrived: " + mob.getPosition());

        mob.teleport(new net.minestom.server.coordinate.Pos(5.5, 1, 8.5)).join();
        for (; tick < 260; tick++) {
            final long now = time + tick * 50L;
            instance.tick(now);
            mob.tick(now);
            mob.tickRig();
            player.teleport(new net.minestom.server.coordinate.Pos(8.5, 1, 8.5)).join();
        }
        assertTrue(mob.getPosition().distance(player.getPosition()) < 1.8, "stayed at " + mob.getPosition());

        mob.removeRig();
        player.remove();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void aMobCannotHitThroughAWall() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "walkSpeed", 0.13d);
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        for (int x = 5; x <= 7; x++) {
            for (int z = 7; z <= 9; z++) {
                if (x == 6 && z == 8) continue;
                for (int y = 1; y <= 3; y++) instance.setBlock(x, y, z, Block.STONE);
            }
        }
        var walledIn = new net.minestom.server.coordinate.Pos(6.35, 1, 8.5);
        Player player = new Player(new DummyConnection(),
                new GameProfile(UUID.randomUUID(), "target"));
        player.setInstance(instance, walledIn).join();
        float full = player.getHealth();
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(4.65, 1, 8.5));
        mob.spawnMob(instance);
        assertTrue(mob.getPosition().distance(player.getPosition()) < 1.8);

        long time = System.currentTimeMillis();
        int tick = 0;
        for (; tick < 100; tick++) {
            final long now = time + tick * 50L;
            instance.tick(now);
            mob.tick(now);
            mob.tickRig();
            player.teleport(walledIn).join();
        }
        assertEquals(full, player.getHealth());

        for (int y = 1; y <= 3; y++) instance.setBlock(5, y, 8, Block.AIR);
        for (; tick < 300 && player.getHealth() == full; tick++) {
            final long now = time + tick * 50L;
            instance.tick(now);
            mob.tick(now);
            mob.tickRig();
            player.teleport(walledIn.add(0, 0, tick % 2 == 0 ? 0.01 : 0)).join();
        }
        assertTrue(player.getHealth() < full, "the mob never attacked once the wall was open");

        mob.removeRig();
        player.remove();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void aMobStillReachesAPlayerStandingOneStepAboveIt() throws Exception {
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        instance.setBlock(6, 1, 8, Block.STONE);
        Player player = new Player(new DummyConnection(),
                new GameProfile(UUID.randomUUID(), "target"));
        player.setInstance(instance, new net.minestom.server.coordinate.Pos(6.5, 2, 8.5)).join();
        float full = player.getHealth();
        RavengardMob mob = new RavengardMob(clip(),
                new net.minestom.server.coordinate.Pos(5.3, 1, 8.5));
        mob.spawnMob(instance);

        mob.attack(player, false);

        assertTrue(player.getHealth() < full);

        mob.removeRig();
        player.remove();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void aMobComesThroughADoorThatOpensWhileItsTargetStandsStill() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "walkSpeed", 0.13d);
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        for (int x = 5; x <= 7; x++) {
            for (int z = 7; z <= 9; z++) {
                if (x == 6 && z == 8) continue;
                for (int y = 1; y <= 3; y++) instance.setBlock(x, y, z, Block.STONE);
            }
        }
        var walledIn = new net.minestom.server.coordinate.Pos(6.5, 1, 8.5);
        Player player = new Player(new DummyConnection(),
                new GameProfile(UUID.randomUUID(), "target"));
        player.setInstance(instance, walledIn).join();
        player.getAttribute(net.minestom.server.entity.attribute.Attribute.MAX_HEALTH).setBaseValue(1024);
        player.setHealth(1024);
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(1.5, 1, 8.5));
        mob.spawnMob(instance);

        long time = System.currentTimeMillis();
        int tick = 0;
        for (; tick < 100; tick++) {
            final long now = time + tick * 50L;
            instance.tick(now);
            mob.tick(now);
            mob.tickRig();
            player.teleport(walledIn).join();
        }
        assertEquals(1024f, player.getHealth());

        for (int y = 1; y <= 3; y++) instance.setBlock(5, y, 8, Block.AIR);
        for (; tick < 400 && player.getHealth() == 1024f; tick++) {
            final long now = time + tick * 50L;
            instance.tick(now);
            mob.tick(now);
            mob.tickRig();
            player.teleport(walledIn).join();
        }
        assertTrue(player.getHealth() < 1024f, "the mob stayed at " + mob.getPosition());

        mob.removeRig();
        player.remove();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void aChargeDoesNotLandThroughAWall() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "walkSpeed", 0.13d);
        RavengardMobClip.Animation charge = new RavengardMobClip.Animation();
        set(charge, "loop", false);
        set(charge, "frames", List.of(Map.of(), Map.of()));
        set(clip, "animations", Map.of("charge", charge));
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        for (int x = 5; x <= 8; x++) {
            for (int z = 7; z <= 9; z++) {
                if ((x == 6 || x == 7) && z == 8) continue;
                for (int y = 1; y <= 3; y++) instance.setBlock(x, y, z, Block.STONE);
            }
        }
        var farEnd = new net.minestom.server.coordinate.Pos(7.4, 1, 8.5);
        var nearEnd = new net.minestom.server.coordinate.Pos(6.3, 1, 8.5);
        Player player = new Player(new DummyConnection(),
                new GameProfile(UUID.randomUUID(), "target"));
        player.setInstance(instance, farEnd).join();
        float full = player.getHealth();
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(4.3, 1, 8.5));
        mob.spawnMob(instance);

        long time = System.currentTimeMillis();
        for (int tick = 0; tick < 40; tick++) {
            final long now = time + tick * 50L;
            instance.tick(now);
            mob.tick(now);
            mob.tickRig();
            // the charge starts at range, then the target steps up to the wall
            player.teleport(tick < 3 ? farEnd : nearEnd).join();
        }
        assertTrue(mob.getPosition().distance(player.getPosition()) <= 2.4);
        assertEquals(full, player.getHealth());

        mob.removeRig();
        player.remove();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void aKnightDoesNotChargeATargetItCannotSee() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "walkSpeed", 0.13d);
        RavengardMobClip.Animation charge = new RavengardMobClip.Animation();
        set(charge, "loop", false);
        set(charge, "frames", List.of(Map.of(), Map.of()));
        set(clip, "animations", Map.of("charge", charge));
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        for (int z = 0; z < 16; z++) {
            for (int y = 1; y <= 3; y++) instance.setBlock(5, y, z, Block.STONE);
        }
        var behindTheWall = new net.minestom.server.coordinate.Pos(7.4, 1, 8.5);
        Player player = new Player(new DummyConnection(),
                new GameProfile(UUID.randomUUID(), "target"));
        player.setInstance(instance, behindTheWall).join();
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(4.3, 1, 8.5));
        mob.spawnMob(instance);
        var speed = mob.getAttribute(net.minestom.server.entity.attribute.Attribute.MOVEMENT_SPEED);
        double walking = speed.getBaseValue();

        long time = System.currentTimeMillis();
        double fastest = walking;
        for (int tick = 0; tick < 6; tick++) {
            final long now = time + tick * 50L;
            instance.tick(now);
            mob.tick(now);
            mob.tickRig();
            player.teleport(behindTheWall).join();
            fastest = Math.max(fastest, speed.getBaseValue());
        }
        assertEquals(walking, fastest, 1.0e-9);

        for (int z = 0; z < 16; z++) {
            for (int y = 1; y <= 3; y++) instance.setBlock(5, y, z, Block.AIR);
        }
        for (int tick = 6; tick < 12; tick++) {
            final long now = time + tick * 50L;
            instance.tick(now);
            mob.tick(now);
            mob.tickRig();
            player.teleport(behindTheWall).join();
            fastest = Math.max(fastest, speed.getBaseValue());
        }
        assertTrue(fastest > walking, "the knight never charged a target in plain view");

        mob.removeRig();
        player.remove();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void aWokenZombieKnightDoesNotLieBackDownBetweenAttacks() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "name", "zombie_knight");
        set(clip, "parts", List.of(part("body", "hypixel_ravengard:entity/zombie_knight/body")));
        RavengardMobClip.Animation idle = new RavengardMobClip.Animation();
        set(idle, "loop", true);
        set(idle, "frames", List.of(Map.of(), Map.of()));
        RavengardMobClip.Animation walk = new RavengardMobClip.Animation();
        set(walk, "loop", true);
        set(walk, "frames", List.of(Map.of(), Map.of()));
        set(clip, "animations", Map.of("idle", idle, "walk", walk));
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        RavengardMob mob = new RavengardMob(clip, new net.minestom.server.coordinate.Pos(2.5, 1, 2.5));
        mob.spawnMob(instance);
        Field current = RavengardMob.class.getDeclaredField("currentAnimation");
        current.setAccessible(true);

        mob.tickRig();
        assertEquals("idle", current.get(mob));

        Entity prey = new Entity(EntityType.ZOMBIE);
        prey.setInstance(instance, new net.minestom.server.coordinate.Pos(4.5, 1, 2.5)).join();
        mob.setTarget(prey);
        mob.tickRig();
        assertEquals("walk", current.get(mob));

        prey.remove();
        mob.removeRig();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void anEffectEnlargedByAnAttackReturnsToItsRestingSize() throws Exception {
        RavengardMobClip clip = clip();
        RavengardMobClip.Part slash = part("slash", "hypixel_ravengard:entity/skeleton_warrior/slash");
        set(slash, "s", new float[]{0, 0, 0});
        set(clip, "parts", List.of(slash));
        RavengardMobClip.Keyframe grown = emptyKeyframe();
        set(grown, "s", new float[]{2, 2, 2});
        RavengardMobClip.Animation attack = new RavengardMobClip.Animation();
        set(attack, "loop", false);
        set(attack, "frames", List.of(Map.of("0", grown)));
        RavengardMobClip.Animation idle = new RavengardMobClip.Animation();
        set(idle, "loop", true);
        set(idle, "frames", List.of(Map.of(), Map.of()));
        set(clip, "animations", Map.of("idle", idle, "attack", attack));
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        RavengardMob mob = new RavengardMob(clip, new net.minestom.server.coordinate.Pos(2.5, 1, 2.5));
        mob.spawnMob(instance);
        mob.tickRig();
        Entity display = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY)
                .findFirst().orElseThrow();

        var startOneShot = RavengardMob.class.getDeclaredMethod("startOneShot", String.class);
        startOneShot.setAccessible(true);
        startOneShot.invoke(mob, "attack");
        mob.tickRig();
        assertEquals(2.0, ((ItemDisplayMeta) display.getEntityMeta()).getScale().x(), 1.0e-6);
        for (int tick = 0; tick < 3; tick++) mob.tickRig();
        assertEquals(0.0, ((ItemDisplayMeta) display.getEntityMeta()).getScale().x(), 1.0e-6);

        mob.removeRig();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void spawnedItemDisplaysUseVariantModelsAndValidBaseFallbacks() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "name", "skeleton_archer");
        set(clip, "parts", List.of(
                part("body", "hypixel_ravengard:entity/skeleton_archer/body"),
                part("eyes", "hypixel_ravengard:entity/skeleton_archer/eyes")));
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(2.5, 1, 2.5), "skeleton_archer_elite");

        mob.spawnMob(instance);

        Set<String> models = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY)
                .map(entity -> ((ItemDisplayMeta) entity.getEntityMeta()).getItemStack()
                        .get(DataComponents.ITEM_MODEL))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(
                "hypixel_ravengard:entity/skeleton_archer_elite/body",
                "hypixel_ravengard:entity/skeleton_archer/eyes"), models);

        mob.removeRig();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void spawnedBottleVariantUsesItsOfficialItemModels() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "name", "bottle_mimic_short_yellow");
        set(clip, "parts", List.of(
                part("topcap", "hypixel_ravengard:entity/bottle_mimic_short_yellow/topcap"),
                part("eye", "hypixel_ravengard:entity/bottle_mimic_short_yellow/eye")));
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        RavengardMob mob = new RavengardMob(clip,
                new net.minestom.server.coordinate.Pos(2.5, 1, 2.5), "bottle_mimic_tall_pink");

        mob.spawnMob(instance);

        Set<String> models = instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.ITEM_DISPLAY)
                .map(entity -> ((ItemDisplayMeta) entity.getEntityMeta()).getItemStack()
                        .get(DataComponents.ITEM_MODEL))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(
                "hypixel_ravengard:entity/bottle_mimic_tall_pink/topcap",
                "hypixel_ravengard:entity/bottle_mimic_tall_pink/eye"), models);

        mob.removeRig();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    @Test
    void malformedEmptyRigArraysDoNotBreakAnimationTick() throws Exception {
        RavengardMobClip clip = clip();
        set(clip, "name", "empty_array_mob");
        RavengardMobClip.Part part = part("body", "hypixel_ravengard:entity/skeleton_knight/body");
        set(part, "t", new float[0]);
        set(part, "r", new float[0]);
        set(part, "s", new float[0]);
        set(clip, "parts", List.of(part));

        RavengardMobClip.Animation animation = new RavengardMobClip.Animation();
        set(animation, "loop", true);
        set(animation, "frames", List.of(Map.of("0", emptyKeyframe())));
        set(clip, "animations", Map.of("idle", animation));

        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));
        instance.loadChunk(0, 0).join();
        RavengardMob mob = new RavengardMob(clip, new net.minestom.server.coordinate.Pos(2.5, 1, 2.5));
        mob.spawnMob(instance);
        mob.tickRig();
        mob.tickRig();
        assertTrue(instance.getEntities().contains(mob));
        mob.removeRig();
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }

    private static RavengardMobClip.Keyframe emptyKeyframe() throws Exception {
        RavengardMobClip.Keyframe keyframe = new RavengardMobClip.Keyframe();
        set(keyframe, "t", new float[0]);
        set(keyframe, "r", new float[0]);
        set(keyframe, "s", new float[0]);
        return keyframe;
    }

    @Test
    void packetBridgeHonorsReachAndRightClickDoesNotAttack() throws Exception {
        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.loadChunk(0, 0).join();
        var player = new net.swofty.type.ravengardgeneric.user.RavengardPlayer(
                new DummyConnection(), new GameProfile(UUID.randomUUID(), "packet-combat"));
        player.setInstance(instance, new net.minestom.server.coordinate.Pos(4, 1, 2.5)).join();
        var mob = new RavengardMob(clip(), new net.minestom.server.coordinate.Pos(2.5, 1, 2.5));
        mob.spawnMob(instance);
        var hitbox = mob;
        hitbox.addViewer(player);
        var attacks = new AtomicInteger();
        var listener = net.minestom.server.event.EventListener.of(
                net.minestom.server.event.entity.EntityAttackEvent.class, event -> {
                    if (event.getEntity() == player && event.getTarget() == hitbox) attacks.incrementAndGet();
                });
        MinecraftServer.getGlobalEventHandler().addListener(listener);
        try {
            net.swofty.type.ravengardgeneric.packets.client.PacketListenerRavengardCombat.bridge(player, hitbox.getEntityId());
            assertEquals(1, attacks.get());
            player.teleport(new net.minestom.server.coordinate.Pos(14, 1, 2.5)).join();
            net.swofty.type.ravengardgeneric.packets.client.PacketListenerRavengardCombat.bridge(player, hitbox.getEntityId());
            assertEquals(1, attacks.get(), "out-of-reach attacks must be rejected");
            player.teleport(new net.minestom.server.coordinate.Pos(4, 1, 2.5)).join();
            new net.swofty.type.ravengardgeneric.packets.client.PacketListenerRavengardInteractAttack().run(null,
                    new net.minestom.server.network.packet.client.play.ClientInteractEntityPacket(hitbox.getEntityId(),
                            net.minestom.server.entity.PlayerHand.MAIN, net.minestom.server.coordinate.Vec.ZERO, false), player);
            assertEquals(1, attacks.get(), "right click is not an attack");
            hitbox.removeViewer(player);
            net.swofty.type.ravengardgeneric.packets.client.PacketListenerRavengardCombat.bridge(player, hitbox.getEntityId());
            assertEquals(1, attacks.get(), "unviewed targets must be rejected");
        } finally {
            MinecraftServer.getGlobalEventHandler().removeListener(listener);
            mob.removeRig();
            player.remove();
            MinecraftServer.getInstanceManager().unregisterInstance(instance);
        }
    }
    private static RavengardMobClip clip() throws Exception {
        RavengardMobClip clip = new RavengardMobClip();
        set(clip, "name", "test_mob");
        set(clip, "health", 100d);
        set(clip, "walkSpeed", 0d);
        return clip;
    }

    private static RavengardMobClip.Part part(String name, String model) throws Exception {
        RavengardMobClip.Part part = new RavengardMobClip.Part();
        set(part, "name", name);
        set(part, "model", model);
        return part;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
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
