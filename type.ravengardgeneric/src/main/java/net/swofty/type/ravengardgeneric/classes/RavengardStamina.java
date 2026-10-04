package net.swofty.type.ravengardgeneric.classes;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.GameMode;
import net.minestom.server.timer.TaskSchedule;
import net.swofty.commons.ServerType;
import net.swofty.type.generic.HypixelConst;
import net.swofty.type.ravengardgeneric.hud.RavengardHud;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sprinting in the dungeon spends stamina, which refills once the player has stopped sprinting for
 * a moment. Run dry, the player cannot sprint until it is back to a third: the client refuses to
 * sprint on six food points or fewer, so the food bar (hidden by the pack) is what holds them back.
 * Rush and Swift sprint for free.
 */
public final class RavengardStamina {
    static final double MAX = 100.0;
    static final double SPRINT_COST_PER_SECOND = 12.0;
    static final double REGEN_PER_SECOND = 20.0;
    static final long REGEN_DELAY_MILLIS = 1000;
    static final double RECOVERED_AT = 30.0;
    private static final int TICKS = 2;
    private static final int FOOD_FULL = 20;
    private static final int FOOD_NO_SPRINT = 6;

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    static final class State {
        double stamina = MAX;
        long lastSpentAt;
        boolean exhausted;
    }

    private RavengardStamina() {
    }

    public static void start() {
        if (HypixelConst.getTypeLoader().getType() != ServerType.RAVENGARD_DUNGEON) return;
        MinecraftServer.getSchedulerManager().buildTask(RavengardStamina::tick)
                .repeat(TaskSchedule.tick(TICKS)).schedule();
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        STATES.keySet().removeIf(uuid -> MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) == null);
        for (var online : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (!(online instanceof RavengardPlayer player)) continue;
            State state = STATES.computeIfAbsent(player.getUuid(), ignored -> new State());
            boolean spending = player.isSprinting()
                    && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                    && !RavengardAbilityService.hasBuff(player, RavengardAbility.RUSH)
                    && !RavengardAbilityService.hasBuff(player, RavengardAbility.SWIFT);
            step(state, spending, now, TICKS / 20.0);
            int food = state.exhausted ? FOOD_NO_SPRINT : FOOD_FULL;
            if (player.getFood() != food) player.setFood(food);
            RavengardHud.state(player).setStamina((int) Math.round(state.stamina));
        }
    }

    static void step(State state, boolean spending, long now, double seconds) {
        if (spending) {
            state.stamina = Math.max(0, state.stamina - SPRINT_COST_PER_SECOND * seconds);
            state.lastSpentAt = now;
        } else if (now - state.lastSpentAt >= REGEN_DELAY_MILLIS) {
            state.stamina = Math.min(MAX, state.stamina + REGEN_PER_SECOND * seconds);
        }
        if (state.stamina <= 0) state.exhausted = true;
        else if (state.exhausted && state.stamina >= RECOVERED_AT) state.exhausted = false;
    }
}
