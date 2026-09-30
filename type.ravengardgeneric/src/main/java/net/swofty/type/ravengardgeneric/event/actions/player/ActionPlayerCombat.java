package net.swofty.type.ravengardgeneric.event.actions.player;

import net.minestom.server.entity.damage.EntityDamage;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.item.ItemStack;
import net.swofty.type.generic.event.EventNodes;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.generic.event.phase.EventPhase;
import net.swofty.type.generic.event.phase.PhasedEvent;
import net.swofty.type.ravengardgeneric.entity.mob.RavengardMob;
import net.swofty.type.ravengardgeneric.classes.RavengardAbility;
import net.swofty.type.ravengardgeneric.classes.RavengardAbilityService;
import net.swofty.type.ravengardgeneric.item.RavengardItemType;
import net.swofty.type.ravengardgeneric.item.attribute.RavengardItemAttributeHandler;
import net.swofty.type.ravengardgeneric.item.statistics.RavengardItemStatistic;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;
import org.tinylog.Logger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActionPlayerCombat implements HypixelEventClass {
    private static final double DISPLAY_DAMAGE_SCALE = 4.0;
    private static final float FIST_DAMAGE = 1f;
    private static final double DEFAULT_ATTACK_SPEED = 4.0;
    static final double MIN_ATTACK_INTERVAL_FRACTION = 0.5;
    private static final ConcurrentHashMap<UUID, Long> LAST_ATTACK_NANOS = new ConcurrentHashMap<>();

    @PhasedEvent(node = EventNodes.ENTITY, requireDataLoaded = true, phase = EventPhase.GAMEPLAY)
    public void onAttack(EntityAttackEvent event) {
        Logger.debug("Ravengard combat event received: attacker={} target={} targetType={}",
                event.getEntity().getEntityId(), event.getTarget().getEntityId(),
                event.getTarget().getEntityType());
        if (!(event.getEntity() instanceof RavengardPlayer player)) {
            Logger.debug("Ravengard combat event ignored: attacker is {}", event.getEntity().getClass().getName());
            return;
        }
        RavengardMob mob = RavengardMob.combatTarget(event.getTarget());
        if (mob == null) {
            Logger.debug("Ravengard combat event ignored: target {} is not a Ravengard mob",
                    event.getTarget().getEntityId());
            return;
        }

        ItemStack held = player.getItemInMainHand();
        RavengardItemAttributeHandler handler = new RavengardItemAttributeHandler(held);
        RavengardItemType type = handler.getType();
        if (type != null && !type.usableBy(player.getRavengardClass())) {
            player.sendMessage("<c>Your class cannot use this item!");
            return;
        }

        double attackSpeed = DEFAULT_ATTACK_SPEED;
        if (type != null) {
            double bonus = type.statistic(RavengardItemStatistic.ATTACK_SPEED) * handler.getStatBoost();
            if (bonus > 0) attackSpeed += bonus;
        }
        if (mob.isDying()) return;
        double strength = attackStrength(player.getUuid(), System.nanoTime(), attackSpeed);
        if (strength < 0) return;

        float damage = FIST_DAMAGE;
        if (type != null) {
            double weapon = type.statistic(RavengardItemStatistic.DAMAGE) * handler.getStatBoost();
            if (weapon > 0) {
                damage = (float) Math.max(FIST_DAMAGE,
                        weapon / DISPLAY_DAMAGE_SCALE * (0.2 + strength * strength * 0.8));
            }
        }
        if (RavengardAbilityService.hasBuff(player, RavengardAbility.DIRECT_HIT)
                || RavengardAbilityService.hasBuff(player, RavengardAbility.PRECISE_ACCURACY)) {
            double sustainedMultiplier = 1.0;
            if (RavengardAbilityService.hasBuff(player, RavengardAbility.DIRECT_HIT)) {
                sustainedMultiplier *= 1.2;
            }
            if (RavengardAbilityService.hasBuff(player, RavengardAbility.PRECISE_ACCURACY)) {
                sustainedMultiplier *= 1.3;
            }
            damage *= (float) sustainedMultiplier;
        }
        boolean warCry = RavengardAbilityService.hasBuff(player, RavengardAbility.WAR_CRY);
        boolean lacerate = RavengardAbilityService.hasBuff(player, RavengardAbility.LACERATE);
        if (warCry) damage *= 1.2f;
        if (lacerate) damage *= 1.8f;
        Logger.debug("Ravengard player damage: player={} mob={} amount={} healthBefore={}",
                player.getUsername(), mob.getEntityId(), damage, mob.getHealth());
        if (!mob.damage(new EntityDamage(player, damage))) return;
        if (warCry) RavengardAbilityService.consumeBuff(player, RavengardAbility.WAR_CRY);
        if (lacerate) RavengardAbilityService.consumeBuff(player, RavengardAbility.LACERATE);
    }

    /** A swing inside half the weapon's recovery answers with a negative strength and is dropped. */
    static double attackStrength(UUID player, long nowNanos, double attackSpeed) {
        Long previous = LAST_ATTACK_NANOS.get(player);
        if (previous == null) {
            LAST_ATTACK_NANOS.put(player, nowNanos);
            return 1.0;
        }
        long elapsed = Math.max(0L, nowNanos - previous);
        double intervalNanos = 1_000_000_000.0 * 20.0 / Math.max(0.1, attackSpeed) / 20.0;
        if (elapsed < intervalNanos * MIN_ATTACK_INTERVAL_FRACTION) return -1.0;
        LAST_ATTACK_NANOS.put(player, nowNanos);
        return Math.clamp(elapsed / intervalNanos, 0.0, 1.0);
    }

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = false, phase = EventPhase.DISCONNECT)
    public void onDisconnect(PlayerDisconnectEvent event) {
        LAST_ATTACK_NANOS.remove(event.getPlayer().getUuid());
    }
}
