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
        RavengardMob mob = new RavengardMob(
                clip(),
                new net.minestom.server.coordinate.Pos(0, 0, 0));
        LivingEntity target = new LivingEntity(EntityType.ZOMBIE);
        target.setHealth(40);
        float before = target.getHealth();

        mob.attack(target, false);

        assertEquals(before - 2, target.getHealth(), 0.001);
        assertSame(mob, target.getLastDamageSource().getAttacker());
        assertSame(mob, target.getLastDamageSource().getSource());
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
        assertEquals("hypixel_ravengard:entity/demon_archer/quiver",
                RavengardMob.appearanceModel("demon_archer", "skeleton_archer", "quiver",
                        "hypixel_ravengard:entity/skeleton_archer/quiver"));
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
    void officialEnemyAppearancesAddTheirPackModelsWithoutDroppingCombatRigParts() {
        assertEquals(Set.of(
                        "hypixel_ravengard:entity/normal_mimic/bottom",
                        "hypixel_ravengard:entity/normal_mimic/tongue",
                        "hypixel_ravengard:entity/normal_mimic/top"),
                Set.copyOf(RavengardMob.supplementalAppearanceModels("normal_mimic", List.of())));
        assertTrue(RavengardMob.supplementalAppearanceModels("dire_wolf", List.of()).size() >= 9);
        assertTrue(RavengardMob.supplementalAppearanceModels("cursed_knight", List.of()).size() >= 10);
        assertTrue(RavengardMob.supplementalAppearanceModels("wraith", List.of()).size() >= 10);
        assertTrue(RavengardMob.supplementalAppearanceModels("warden", List.of()).size() >= 8);
        assertTrue(RavengardMob.supplementalAppearanceModels("skeleton_archer", List.of()).isEmpty());
        assertTrue(RavengardMob.supplementalAppearanceModels("normal_mimic", List.of(
                "hypixel_ravengard:entity/normal_mimic/top")).stream()
                .noneMatch(model -> model.endsWith("/top")));
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
