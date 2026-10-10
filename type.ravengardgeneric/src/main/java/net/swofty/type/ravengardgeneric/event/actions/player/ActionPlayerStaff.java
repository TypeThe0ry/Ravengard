package net.swofty.type.ravengardgeneric.event.actions.player;

import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerEntityInteractEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.swofty.type.generic.event.EventNodes;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.generic.event.phase.EventPhase;
import net.swofty.type.generic.event.phase.PhasedEvent;
import net.swofty.type.ravengardgeneric.classes.RavengardAbilityService;
import net.swofty.type.ravengardgeneric.classes.RavengardSpells;
import net.swofty.type.ravengardgeneric.item.RavengardItemType;
import net.swofty.type.ravengardgeneric.item.attribute.RavengardItemAttributeHandler;
import net.swofty.type.ravengardgeneric.item.components.StandardItemComponent;
import net.swofty.type.ravengardgeneric.item.statistics.RavengardItemStatistic;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Magic weapons attack with a click, left or right: a bolt for their ranged damage, at their ranged attack speed. */
public class ActionPlayerStaff implements HypixelEventClass {
    static final Set<String> MAGIC_WEAPONS = Set.of("STAFF", "ORB", "SCEPTRE", "SPELLBOOK", "MAGIC_SWORD");
    private static final ConcurrentHashMap<UUID, Long> READY_AT = new ConcurrentHashMap<>();

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = true, phase = EventPhase.GAMEPLAY)
    public void onUse(PlayerUseItemEvent event) {
        if (event.getHand() == PlayerHand.MAIN && event.getPlayer() instanceof RavengardPlayer player) {
            cast(player);
        }
    }

    /** A left click swings the arm, in the air or at a mob; a staff has no melee, so it casts instead. */
    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = true, phase = EventPhase.GAMEPLAY)
    public void onSwing(PlayerHandAnimationEvent event) {
        if (event.getHand() == PlayerHand.MAIN && event.getPlayer() instanceof RavengardPlayer player) {
            cast(player);
        }
    }

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = true, phase = EventPhase.GAMEPLAY)
    public void onInteract(PlayerEntityInteractEvent event) {
        if (event.getHand() == PlayerHand.MAIN && event.getPlayer() instanceof RavengardPlayer player) {
            cast(player);
        }
    }

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = false, phase = EventPhase.DISCONNECT)
    public void onDisconnect(PlayerDisconnectEvent event) {
        READY_AT.remove(event.getPlayer().getUuid());
    }

    static boolean isMagicWeapon(RavengardItemType type) {
        StandardItemComponent standard = type == null ? null : type.component(StandardItemComponent.class);
        return standard != null && MAGIC_WEAPONS.contains(standard.getStandardItemType());
    }

    static void cast(RavengardPlayer player) {
        if (player.isTutorial() || player.getInstance() == null) return;
        RavengardItemAttributeHandler handler = new RavengardItemAttributeHandler(player.getItemInMainHand());
        RavengardItemType type = handler.getType();
        StandardItemComponent standard = type == null ? null : type.component(StandardItemComponent.class);
        if (standard == null || !MAGIC_WEAPONS.contains(standard.getStandardItemType())) return;
        if (!type.usableBy(player.getRavengardClass())) {
            player.sendMessage("<c>你的职业无法使用该物品！");
            return;
        }
        double damage = type.statistic(RavengardItemStatistic.RANGED_DAMAGE) * handler.getStatBoost();
        if (damage <= 0) return;
        long now = System.currentTimeMillis();
        if (READY_AT.getOrDefault(player.getUuid(), 0L) > now) return;
        READY_AT.put(player.getUuid(), now + cooldownMillis(type, RavengardAbilityService.magicCooldownMultiplier(player)));
        RavengardSpells.staffBolt(player, damage);
    }

    static long cooldownMillis(RavengardItemType type, double multiplier) {
        double attacksPerSecond = type.statistic(RavengardItemStatistic.RANGED_ATTACK_SPEED);
        double seconds = attacksPerSecond > 0 ? 1.0 / attacksPerSecond : 1.0;
        return Math.round(seconds * 1000 * multiplier);
    }
}
