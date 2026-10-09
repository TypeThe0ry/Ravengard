package net.swofty.type.ravengardgeneric.classes;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs the starter abilities. Heals work on the hud's 160 point scale over the forty point
 * vanilla pool, timed buffs sit in a registry the damage code reads, and Shadows is real
 * invisibility with the slow, cancelled by attacking or being hit.
 */
public final class RavengardAbilityService {
    /** Display health points per vanilla half heart pool: 160 shown over 40 vanilla. */
    private static final float DISPLAY_SCALE = 4f;

    private static final Map<UUID, Map<RavengardAbility, Long>> COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<RavengardAbility, Long>> BUFFS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Task>> HEALING = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<RavengardAbility, Integer>> CHARGES = new ConcurrentHashMap<>();
    static final int RAPID_FIRE_ARROWS = 3;

    private RavengardAbilityService() {
    }

    public static void use(RavengardPlayer player, int slot) {
        RavengardClass playerClass = player.getRavengardClass();
        if (playerClass == null || player.isTutorial()) {
            return;
        }
        int inventorySlot = slot == 0 ? RavengardKit.SLOT_ABILITY_ONE : RavengardKit.SLOT_ABILITY_TWO;
        RavengardAbility ability = RavengardKit.abilityFrom(player.getInventory().getItemStack(inventorySlot));
        if (ability == null || ability.getOwner() != playerClass) {
            return;
        }
        use(player, ability);
    }

    public static void use(RavengardPlayer player, RavengardAbility ability) {
        RavengardClass playerClass = player.getRavengardClass();
        if (playerClass == null || player.isTutorial() || ability == null
                || ability.getOwner() != playerClass) {
            return;
        }

        long now = System.currentTimeMillis();
        long readyAt = COOLDOWNS.computeIfAbsent(player.getUuid(), ignored -> new ConcurrentHashMap<>())
                .getOrDefault(ability, 0L);
        if (readyAt > now) {
            player.sendMessage("<c>该技能冷却中，还需 {} 秒！", (readyAt - now) / 1000 + 1);
            return;
        }
        COOLDOWNS.get(player.getUuid()).put(ability, now + ability.getCooldownSeconds() * 1000L);

        apply(player, ability);
        player.sendMessage("<a>你使用了 <f>{}<a>！", ability.getDisplayName());
    }

    private static void apply(RavengardPlayer player, RavengardAbility ability) {
        switch (ability) {
            case RECOVERY, MED_KIT -> heal(player, 30);
            case COOL_OFF, HEAL_WOUNDS -> healOverTime(player, 35, 10);
            case SPRING_HEALING -> healOverTime(player, 45, 10);
            case SHADOWS -> shadows(player);
            case FIREBALL -> RavengardSpells.fireball(player);
            case LIGHTNING_STRIKE -> RavengardSpells.lightningStrike(player);
            case ICE_SHARDS -> RavengardSpells.iceShards(player);
            case RUMBLE -> RavengardSpells.rumble(player);
            case WIND_CUTTER -> RavengardSpells.windCutter(player);
            case SHIELD_BASH, OVERPOWER -> RavengardSpells.mobsNear(player.getInstance(), player.getPosition(), 5.0)
                    .forEach(mob -> RavengardSpells.knockback(mob, player.getPosition(), 1.0));
            case PROVOKE -> RavengardSpells.mobsNear(player.getInstance(), player.getPosition(), 7.0)
                    .forEach(mob -> mob.setTarget(player));
            case RALLY -> player.getInstance().getPlayers().stream()
                    .filter(other -> other instanceof RavengardPlayer)
                    .filter(other -> other.getPosition().distanceSquared(player.getPosition()) <= 6.0 * 6.0)
                    .forEach(other -> buff((RavengardPlayer) other, RavengardAbility.RALLY, 10));
            case SHADOW_STEP -> player.setVelocity(player.getPosition().withPitch(0).direction().mul(30).withY(4));
            case SWIFT -> swift(player, 10);
            case DIRECT_HIT, ARMOR_BREAK, CARNAGE, RUSH -> buff(player, ability, 5);
            case RAPID_FIRE -> {
                buff(player, ability, NEXT_ATTACK_WINDOW_SECONDS);
                CHARGES.computeIfAbsent(player.getUuid(), ignored -> new ConcurrentHashMap<>())
                        .put(ability, RAPID_FIRE_ARROWS);
            }
            case WAR_CRY, LACERATE, WOUND, POISON_DAGGER, POISON_ARROW, IMMOBILIZE,
                 DOUBLE_SHOT, POWER_SHOT -> buff(player, ability, NEXT_ATTACK_WINDOW_SECONDS);
            default -> buff(player, ability, 10);
        }
    }

    /** How much of an ability's cooldown has passed, from 0 just after use to 1 once it is ready again. */
    public static float cooldownProgress(RavengardPlayer player, RavengardAbility ability) {
        Map<RavengardAbility, Long> cooldowns = COOLDOWNS.get(player.getUuid());
        long readyAt = cooldowns == null ? 0L : cooldowns.getOrDefault(ability, 0L);
        long total = ability.getCooldownSeconds() * 1000L;
        long left = readyAt - System.currentTimeMillis();
        if (left <= 0 || total <= 0) return 1f;
        return Math.clamp(1f - left / (float) total, 0f, 1f);
    }

    /** How long a "next attack" ability waits for that attack. */
    static final int NEXT_ATTACK_WINDOW_SECONDS = 15;

    /** Rally (+20%) and Armor Break (+10%) on whatever the player deals to mobs. */
    public static double outgoingDamageMultiplier(RavengardPlayer player) {
        double multiplier = 1.0;
        if (hasBuff(player, RavengardAbility.RALLY)) multiplier *= 1.2;
        if (hasBuff(player, RavengardAbility.ARMOR_BREAK)) multiplier *= 1.1;
        return multiplier;
    }

    /** Sentinel (+30% physical defence) and Bolster (-20%) on the damage mobs deal to the player. */
    public static double incomingDamageMultiplier(RavengardPlayer player) {
        double multiplier = 1.0;
        if (hasBuff(player, RavengardAbility.SENTINEL)) multiplier /= 1.3;
        if (hasBuff(player, RavengardAbility.BOLSTER)) multiplier *= 0.8;
        return multiplier;
    }

    /** Accelerate halves the cooldown of magic items such as staves. */
    public static double magicCooldownMultiplier(RavengardPlayer player) {
        return hasBuff(player, RavengardAbility.ACCELERATE) ? 0.5 : 1.0;
    }

    private static void swift(RavengardPlayer player, int seconds) {
        buff(player, RavengardAbility.SWIFT, seconds);
        net.kyori.adventure.key.Key id = net.kyori.adventure.key.Key.key("hypixel_ravengard", "swift");
        var speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        speed.removeModifier(id);
        speed.addModifier(new net.minestom.server.entity.attribute.AttributeModifier(id, 0.5,
                net.minestom.server.entity.attribute.AttributeOperation.ADD_MULTIPLIED_TOTAL));
        MinecraftServer.getSchedulerManager().buildTask(() -> speed.removeModifier(id))
                .delay(TaskSchedule.seconds(seconds)).schedule();
    }

    /** Whether a timed buff from an ability is currently active, for the damage code to read. */
    public static boolean hasBuff(RavengardPlayer player, RavengardAbility ability) {
        Map<RavengardAbility, Long> buffs = BUFFS.get(player.getUuid());
        return buffs != null && buffs.getOrDefault(ability, 0L) > System.currentTimeMillis();
    }

    /** Uses one of an ability's charges (Rapid Fire's three arrows) while its window lasts. */
    public static boolean consumeCharge(RavengardPlayer player, RavengardAbility ability) {
        Map<RavengardAbility, Integer> charges = CHARGES.get(player.getUuid());
        if (charges == null || !hasBuff(player, ability)) return false;
        Integer left = charges.get(ability);
        if (left == null || left <= 0) return false;
        if (left == 1) {
            charges.remove(ability);
            consumeBuff(player, ability);
        } else {
            charges.put(ability, left - 1);
        }
        return true;
    }

    public static boolean consumeBuff(RavengardPlayer player, RavengardAbility ability) {
        Map<RavengardAbility, Long> buffs = BUFFS.get(player.getUuid());
        if (buffs == null) {
            return false;
        }
        long expiresAt = buffs.getOrDefault(ability, 0L);
        if (expiresAt <= System.currentTimeMillis()) {
            buffs.remove(ability);
            return false;
        }
        buffs.remove(ability);
        return true;
    }

    private static void buff(RavengardPlayer player, RavengardAbility ability, int seconds) {
        BUFFS.computeIfAbsent(player.getUuid(), ignored -> new ConcurrentHashMap<>())
                .put(ability, System.currentTimeMillis() + seconds * 1000L);
    }

    /** A consumable's heal on the display scale, instant or spread over the given seconds. */
    public static void consumeHeal(RavengardPlayer player, int displayAmount, int seconds) {
        if (seconds <= 0) {
            heal(player, displayAmount);
        } else {
            healOverTime(player, displayAmount, seconds);
        }
    }

    private static void heal(RavengardPlayer player, int displayAmount) {
        float max = (float) player.getAttributeValue(Attribute.MAX_HEALTH);
        player.setHealth(Math.min(max, player.getHealth() + displayAmount / DISPLAY_SCALE));
    }

    private static void healOverTime(RavengardPlayer player, int displayTotal, int seconds) {
        float perSecond = displayTotal / (float) seconds / DISPLAY_SCALE;
        List<Task> tasks = HEALING.computeIfAbsent(player.getUuid(), ignored -> new CopyOnWriteArrayList<>());
        for (int second = 1; second <= seconds; second++) {
            Task[] handle = new Task[1];
            handle[0] = MinecraftServer.getSchedulerManager().buildTask(() -> {
                tasks.remove(handle[0]);
                if (player.isOnline() && !player.isDead()
                        && player.getGameMode() != net.minestom.server.entity.GameMode.SPECTATOR) {
                    float max = (float) player.getAttributeValue(Attribute.MAX_HEALTH);
                    player.setHealth(Math.min(max, player.getHealth() + perSecond));
                }
            }).delay(TaskSchedule.tick(second * 20)).schedule();
            tasks.add(handle[0]);
        }
    }

    public static void stopHealing(UUID uuid) {
        List<Task> tasks = HEALING.remove(uuid);
        if (tasks != null) tasks.forEach(Task::cancel);
    }

    private static void shadows(RavengardPlayer player) {
        buff(player, RavengardAbility.SHADOWS, 25);
        player.setInvisible(true);
        var speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        double base = speed.getBaseValue();
        speed.setBaseValue(base * 0.5);
        MinecraftServer.getSchedulerManager().buildTask(() -> endShadows(player, base))
                .delay(TaskSchedule.tick(25 * 20)).schedule();
    }

    public static void breakShadows(RavengardPlayer player) {
        if (!hasBuff(player, RavengardAbility.SHADOWS)) {
            return;
        }
        BUFFS.get(player.getUuid()).remove(RavengardAbility.SHADOWS);
        endShadows(player, 0.1);
    }

    private static void endShadows(RavengardPlayer player, double baseSpeed) {
        if (!player.isOnline()) {
            return;
        }
        player.setInvisible(false);
        player.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(baseSpeed);
    }

    public static void forget(UUID uuid) {
        COOLDOWNS.remove(uuid);
        BUFFS.remove(uuid);
        CHARGES.remove(uuid);
        stopHealing(uuid);
    }
}
