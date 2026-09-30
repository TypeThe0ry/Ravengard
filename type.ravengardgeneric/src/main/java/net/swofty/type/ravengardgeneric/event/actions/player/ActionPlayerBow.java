package net.swofty.type.ravengardgeneric.event.actions.player;

import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.damage.EntityDamage;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithBlockEvent;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithEntityEvent;
import net.minestom.server.event.item.PlayerBeginItemUseEvent;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.sound.SoundEvent;
import net.swofty.type.generic.event.EventNodes;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.generic.event.phase.EventPhase;
import net.swofty.type.generic.event.phase.PhasedEvent;
import net.swofty.type.ravengardgeneric.entity.RavengardArrow;
import net.swofty.type.ravengardgeneric.entity.mob.RavengardMob;
import net.swofty.type.ravengardgeneric.classes.RavengardAbility;
import net.swofty.type.ravengardgeneric.classes.RavengardAbilityService;
import net.swofty.type.ravengardgeneric.item.RavengardItemType;
import net.swofty.type.ravengardgeneric.item.attribute.RavengardItemAttributeHandler;
import net.swofty.type.ravengardgeneric.item.statistics.RavengardItemStatistic;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class ActionPlayerBow implements HypixelEventClass {
    private static final double MIN_POWER = 0.1;
    private static final double DISPLAY_DAMAGE_SCALE = 4.0;
    private static final double ARROW_SPEED = 3.0;
    private static final double ARROW_SPREAD = 0.0;
    private static final double TARGET_DISTANCE = 100.0;
    private static final ConcurrentHashMap<UUID, Long> DRAW_START_NANOS = new ConcurrentHashMap<>();

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = false, phase = EventPhase.GAMEPLAY)
    public void onBegin(PlayerBeginItemUseEvent event) {
            if (event.getPlayer() instanceof RavengardPlayer player
                && event.getHand() == PlayerHand.MAIN
                && event.getItemStack().material() == Material.BOW) {
            markDrawStart(player);
        }
    }

    public static void markDrawStart(net.minestom.server.entity.Player player) {
        DRAW_START_NANOS.put(player.getUuid(), System.nanoTime());
    }

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = true, phase = EventPhase.GAMEPLAY)
    public void onRelease(PlayerCancelItemUseEvent event) {
        if (!(event.getPlayer() instanceof RavengardPlayer player)
                || event.getHand() != PlayerHand.MAIN
                || event.getItemStack().material() != Material.BOW
                || player.getInstance() == null) {
            return;
        }

        Long startedAt = DRAW_START_NANOS.remove(player.getUuid());
        long useTicks = event.getUseDuration();
        if (startedAt != null) {
            long measuredTicks = Math.max(1L, Math.round((System.nanoTime() - startedAt) / 50_000_000.0));
            useTicks = Math.max(useTicks, measuredTicks);
        }
        double power = bowPower(useTicks);
        org.tinylog.Logger.debug("Ravengard bow release: player={} ticks={} power={} item={} ",
                player.getUsername(), useTicks, power, event.getItemStack().material());
        if (power < MIN_POWER) return;

        ItemStack bow = event.getItemStack();
        RavengardItemAttributeHandler handler = new RavengardItemAttributeHandler(bow);
        RavengardItemType type = handler.getType();
        if (type == null || !type.usableBy(player.getRavengardClass())) {
            if (type != null) {
                player.sendMessage("<c>Your class cannot use this item!");
            }
            return;
        }

        float damage = projectileDamage(type, handler.getStatBoost(), power);
        if (RavengardAbilityService.hasBuff(player, RavengardAbility.EAGLE_EYE)) {
            damage *= 1.2f;
        }
        if (RavengardAbilityService.consumeBuff(player, RavengardAbility.POWER_SHOT)) {
            power = 1.0;
            damage = projectileDamage(type, handler.getStatBoost(), power);
            if (RavengardAbilityService.hasBuff(player, RavengardAbility.EAGLE_EYE)) {
                damage *= 1.2f;
            }
        }
        RavengardArrow arrow = new RavengardArrow(player, damage, power >= 1.0);
        Pos eye = player.getPosition().add(0, 1.62, 0);
        arrow.setInstance(player.getInstance(), eye);
        arrow.shoot(eye.add(player.getPosition().direction().mul(TARGET_DISTANCE)),
                ARROW_SPEED * power, ARROW_SPREAD);
        org.tinylog.Logger.debug("Ravengard arrow spawned: player={} uuid={} pos={} damage={} power={}",
                player.getUsername(), arrow.getUuid(), eye, damage, power);
        player.playSound(Sound.sound(SoundEvent.ENTITY_ARROW_SHOOT, Sound.Source.PLAYER,
                1f, shootPitch(power)), player.getPosition());
    }

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = false, phase = EventPhase.DISCONNECT)
    public void onDisconnect(PlayerDisconnectEvent event) {
        DRAW_START_NANOS.remove(event.getPlayer().getUuid());
    }

    @PhasedEvent(node = EventNodes.ENTITY, requireDataLoaded = false, phase = EventPhase.GAMEPLAY)
    public void onEntityCollision(ProjectileCollideWithEntityEvent event) {
        if (!(event.getEntity() instanceof RavengardArrow arrow)) {
            return;
        }
        org.tinylog.Logger.debug("Ravengard arrow entity collision: arrow={} target={} pos={}",
                arrow.getUuid(), event.getTarget().getUuid(), arrow.getPosition());
        RavengardMob mob = RavengardMob.combatTarget(event.getTarget());
        if (mob == null || !tryHit(arrow, mob)) {
            event.setCancelled(true);
        }
    }

    public static boolean tryHit(RavengardArrow arrow, RavengardMob mob) {
        if (arrow.resolved() || mob.isDying()) return false;
        float before = mob.getHealth();
        boolean applied = mob.damage(arrow.getShooter() != null
                ? new EntityDamage(arrow.getShooter(), arrow.damage())
                : new Damage(DamageType.PLAYER_ATTACK, arrow, null, null, arrow.damage()));
        if (!applied) return false;
        arrow.markResolved();
        org.tinylog.Logger.debug("Ravengard arrow damage: mob={} before={} after={} amount={}",
                mob.getUuid(), before, mob.getHealth(), arrow.damage());
        arrow.remove();
        return true;
    }

    @PhasedEvent(node = EventNodes.ENTITY, requireDataLoaded = false, phase = EventPhase.GAMEPLAY)
    public void onBlockCollision(ProjectileCollideWithBlockEvent event) {
        if (event.getEntity() instanceof RavengardArrow arrow) {
            arrow.remove();
        }
    }

    public static double bowPower(long ticks) {
        double seconds = Math.max(0, ticks) / (double) ServerFlag.SERVER_TICKS_PER_SECOND;
        return Math.min((seconds * seconds + seconds * 2.0) / 3.0, 1.0);
    }

    static float projectileDamage(RavengardItemType type, double statBoost, double power) {
        double ranged = type.statistic(RavengardItemStatistic.RANGED_DAMAGE);
        double base = ranged > 0 ? ranged : type.statistic(RavengardItemStatistic.DAMAGE);
        return (float) Math.max(1.0, base * statBoost * power / DISPLAY_DAMAGE_SCALE);
    }

    private static float shootPitch(double power) {
        return (float) (1.0 / (ThreadLocalRandom.current().nextFloat() * 0.4 + 1.2)
                + power * 0.5);
    }
}
