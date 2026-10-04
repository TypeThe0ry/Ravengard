package net.swofty.type.ravengardgeneric.classes;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeModifier;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.entity.damage.EntityDamage;
import net.minestom.server.entity.metadata.item.SnowballMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.timer.TaskSchedule;
import net.swofty.type.ravengardgeneric.entity.mob.RavengardMob;
import net.swofty.type.ravengardgeneric.item.RavengardItemType;
import net.swofty.type.ravengardgeneric.item.attribute.RavengardItemAttributeHandler;
import net.swofty.type.ravengardgeneric.item.statistics.RavengardItemStatistic;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

import java.util.List;
import java.util.function.Consumer;

/**
 * Spells and the other ability effects that act on mobs. Damage figures are the tooltip's display
 * numbers; mobs take a quarter of them, like weapon damage.
 */
public final class RavengardSpells {
    static final double DISPLAY_DAMAGE_SCALE = 4.0;
    private static final double HIT_RADIUS = 1.6;
    private static final double MOB_CENTRE_HEIGHT = 1.1;
    private static final int SPELL_LIFETIME_TICKS = 60;

    private RavengardSpells() {
    }

    /** Hits a mob with display damage from a player's ability or spell, with the player's damage buffs. */
    public static boolean hit(RavengardPlayer player, RavengardMob mob, double displayDamage) {
        if (mob == null || mob.isDying() || mob.getHealth() <= 0) return false;
        double damage = displayDamage * RavengardAbilityService.outgoingDamageMultiplier(player) / DISPLAY_DAMAGE_SCALE;
        return mob.damage(new EntityDamage(player, (float) Math.max(1.0, damage)));
    }

    /** Ability damage scales with the held item's ability damage boost (a percentage). */
    static double abilityDamage(RavengardPlayer player, double displayDamage) {
        RavengardItemAttributeHandler handler = new RavengardItemAttributeHandler(player.getItemInMainHand());
        RavengardItemType type = handler.getType();
        double boost = type == null ? 0 : type.statistic(RavengardItemStatistic.ABILITY_DAMAGE_BOOST);
        return displayDamage * (1.0 + boost / 100.0);
    }

    public static List<RavengardMob> mobsNear(Instance instance, Pos centre, double radius) {
        return RavengardMob.mobs().stream()
                .filter(mob -> mob.getInstance() == instance && !mob.isDying() && mob.getHealth() > 0)
                .filter(mob -> mob.getPosition().distanceSquared(centre) <= radius * radius)
                .toList();
    }

    public static void knockback(RavengardMob mob, Pos from, double strength) {
        Vec away = mob.getPosition().sub(from).asVec().withY(0);
        if (away.lengthSquared() < 1e-6) return;
        away = away.normalize();
        mob.setVelocity(new Vec(away.x() * strength * 20, 0.25 * 20, away.z() * strength * 20));
    }

    /** Display damage every second for a few seconds, from a bleed or a poison. */
    public static void damageOverTime(RavengardPlayer player, RavengardMob mob, double displayPerSecond, int seconds,
                                      Particle particle) {
        for (int second = 1; second <= seconds; second++) {
            MinecraftServer.getSchedulerManager().buildTask(() -> {
                if (mob.isRemoved() || mob.isDying() || mob.getInstance() == null) return;
                Pos at = mob.getPosition().add(0, MOB_CENTRE_HEIGHT, 0);
                mob.getInstance().sendGroupedPacket(new ParticlePacket(particle, at.x(), at.y(), at.z(),
                        0.3f, 0.5f, 0.3f, 0f, 8));
                hit(player, mob, displayPerSecond);
            }).delay(TaskSchedule.seconds(second)).schedule();
        }
    }

    public static void slow(RavengardMob mob, int seconds) {
        Key id = Key.key("hypixel_ravengard", "immobilize");
        var speed = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        speed.addModifier(new AttributeModifier(id, -0.7, AttributeOperation.ADD_MULTIPLIED_TOTAL));
        MinecraftServer.getSchedulerManager().buildTask(() -> speed.removeModifier(id))
                .delay(TaskSchedule.seconds(seconds)).schedule();
    }

    public static void fireball(RavengardPlayer player) {
        double damage = abilityDamage(player, 60);
        launch(player, EntityType.SMALL_FIREBALL, null, 0, 26, Particle.FLAME,
                mob -> {
                    hit(player, mob, damage);
                    burst(mob.getInstance(), mob.getPosition(), Particle.LAVA);
                });
        player.playSound(Sound.sound(SoundEvent.ENTITY_BLAZE_SHOOT, Sound.Source.PLAYER, 1f, 1f), player.getPosition());
    }

    public static void lightningStrike(RavengardPlayer player) {
        double damage = abilityDamage(player, 40);
        Pos eye = player.getPosition().add(0, player.getEyeHeight(), 0);
        Vec direction = player.getPosition().direction();
        Pos impact = null;
        RavengardMob struck = null;
        for (double distance = 0.5; distance <= 30; distance += 0.5) {
            Pos point = eye.add(direction.mul(distance));
            if (solid(player.getInstance(), point)) {
                impact = point;
                break;
            }
            struck = mobAt(player.getInstance(), point);
            if (struck != null) {
                impact = struck.getPosition();
                break;
            }
            impact = point;
        }
        if (impact == null) return;
        Entity bolt = new Entity(EntityType.LIGHTNING_BOLT);
        bolt.setInstance(player.getInstance(), impact);
        MinecraftServer.getSchedulerManager().buildTask(bolt::remove).delay(TaskSchedule.tick(20)).schedule();
        for (RavengardMob mob : mobsNear(player.getInstance(), impact, 3.0)) {
            hit(player, mob, mob == struck ? damage : damage / 2);
        }
    }

    public static void iceShards(RavengardPlayer player) {
        double damage = abilityDamage(player, 10);
        for (int shard = 0; shard < 8; shard++) {
            MinecraftServer.getSchedulerManager().buildTask(() -> {
                if (!player.isOnline() || player.getInstance() == null) return;
                launch(player, EntityType.SNOWBALL, ItemStack.of(Material.PACKED_ICE), 4, 30, Particle.SNOWFLAKE,
                        mob -> {
                            hit(player, mob, damage);
                            knockback(mob, player.getPosition(), 0.4);
                        });
                player.playSound(Sound.sound(SoundEvent.BLOCK_GLASS_HIT, Sound.Source.PLAYER, 0.6f, 1.6f),
                        player.getPosition());
            }).delay(TaskSchedule.tick(shard * 4)).schedule();
        }
    }

    public static void rumble(RavengardPlayer player) {
        double damage = abilityDamage(player, 20);
        Pos centre = player.getPosition();
        burst(player.getInstance(), centre, Particle.EXPLOSION);
        player.getInstance().playSound(Sound.sound(SoundEvent.ENTITY_GENERIC_EXPLODE, Sound.Source.PLAYER, 0.8f, 0.7f),
                centre.x(), centre.y(), centre.z());
        for (RavengardMob mob : mobsNear(player.getInstance(), centre, 5.0)) {
            hit(player, mob, damage);
        }
    }

    public static void windCutter(RavengardPlayer player) {
        double damage = abilityDamage(player, 50);
        for (float spread : new float[]{-12f, 0f, 12f}) {
            launch(player, EntityType.WIND_CHARGE, null, spread, 24, Particle.GUST,
                    mob -> {
                        hit(player, mob, damage);
                        knockback(mob, player.getPosition(), 0.8);
                    });
        }
        player.playSound(Sound.sound(SoundEvent.ENTITY_BREEZE_SHOOT, Sound.Source.PLAYER, 1f, 1f), player.getPosition());
    }

    /** The staff's ordinary attack: a bolt dealing its ranged damage. */
    public static void staffBolt(RavengardPlayer player, double displayDamage) {
        launch(player, EntityType.SNOWBALL, ItemStack.of(Material.AMETHYST_SHARD), 0, 32, Particle.WITCH,
                mob -> hit(player, mob, displayDamage));
        player.playSound(Sound.sound(SoundEvent.BLOCK_AMETHYST_BLOCK_CHIME, Sound.Source.PLAYER, 1f, 1.4f),
                player.getPosition());
    }

    private static void launch(RavengardPlayer player, EntityType type, ItemStack item, float yawSpread,
                               double blocksPerSecond, Particle trail, Consumer<RavengardMob> onHit) {
        Instance instance = player.getInstance();
        if (instance == null) return;
        Pos eye = player.getPosition().add(0, player.getEyeHeight() - 0.1, 0);
        Vec direction = player.getPosition().withYaw(player.getPosition().yaw() + yawSpread).direction();
        Spell spell = new Spell(type, direction.mul(blocksPerSecond), trail, onHit);
        if (item != null) {
            spell.editEntityMeta(SnowballMeta.class, meta -> meta.setItem(item));
        }
        spell.setInstance(instance, eye.add(direction.mul(0.8)));
    }

    private static boolean solid(Instance instance, Pos point) {
        if (!instance.isChunkLoaded(point)) return true;
        return instance.getBlock(point).registry().collisionShape().relativeEnd().y() > 0;
    }

    private static RavengardMob mobAt(Instance instance, Pos point) {
        for (RavengardMob mob : RavengardMob.mobs()) {
            if (mob.getInstance() != instance || mob.isDying() || mob.getHealth() <= 0) continue;
            if (mob.getPosition().add(0, MOB_CENTRE_HEIGHT, 0).distanceSquared(point) <= HIT_RADIUS * HIT_RADIUS) {
                return mob;
            }
        }
        return null;
    }

    private static void burst(Instance instance, Pos at, Particle particle) {
        if (instance == null) return;
        instance.sendGroupedPacket(new ParticlePacket(particle, at.x(), at.y() + 1, at.z(), 0.8f, 0.6f, 0.8f, 0.05f, 20));
    }

    /** A straight flying spell: no gravity, gone on the first mob or wall it meets. */
    private static final class Spell extends Entity {
        private final Particle trail;
        private final Consumer<RavengardMob> onHit;

        private Spell(EntityType type, Vec velocity, Particle trail, Consumer<RavengardMob> onHit) {
            super(type);
            this.trail = trail;
            this.onHit = onHit;
            setNoGravity(true);
            setHasPhysics(false);
            setVelocity(velocity);
        }

        @Override
        public void update(long time) {
            Instance instance = getInstance();
            if (instance == null) return;
            Pos at = getPosition();
            instance.sendGroupedPacket(new ParticlePacket(trail, at.x(), at.y(), at.z(), 0.05f, 0.05f, 0.05f, 0f, 2));
            RavengardMob mob = mobAt(instance, at);
            if (mob != null) {
                onHit.accept(mob);
                remove();
                return;
            }
            if (solid(instance, at) || getAliveTicks() > SPELL_LIFETIME_TICKS) {
                burst(instance, at, Particle.POOF);
                remove();
            }
        }
    }
}
