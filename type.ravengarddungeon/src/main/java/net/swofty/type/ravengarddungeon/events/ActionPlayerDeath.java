package net.swofty.type.ravengarddungeon.events;

import net.minestom.server.event.player.PlayerDeathEvent;
import net.swofty.type.generic.event.EventNodes;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.generic.event.phase.PhasedEvent;
import net.swofty.type.generic.utility.ScheduleUtility;
import net.swofty.type.ravengarddungeon.TypeRavengardDungeonLoader;
import net.swofty.type.ravengarddungeon.game.DungeonInstanceRegistry;
import net.swofty.type.ravengarddungeon.user.RavengardDungeonPlayer;

public class ActionPlayerDeath implements HypixelEventClass {
    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = false)
    public void run(PlayerDeathEvent event) {
        event.setChatMessage(null);
        RavengardDungeonPlayer player = (RavengardDungeonPlayer) event.getPlayer();
        net.swofty.type.ravengarddungeon.interactables.InteractableRegistry.clearPlayer(player);
        net.swofty.type.ravengardgeneric.sound.RavengardSounds.stop(player,
                net.swofty.type.ravengardgeneric.sound.RavengardSounds.Cue.EXTRACTION_CHANNEL);
        String killer = killerName(player.getLastDamageSource());
        event.setDeathText(deathCause(player.getLastDamageSource(), killer).asComponent());
        ScheduleUtility.nextTick(() -> {
            var instance = DungeonInstanceRegistry.findByPlayer(player.getUuid());
            if (instance != null) {
                instance.getGame().eliminate(player, killer);
                return;
            }
            var game = TypeRavengardDungeonLoader.getGame();
            if (game != null) {
                game.eliminate(player, killer);
            }
        });
    }

    private static net.swofty.commons.text.Text deathCause(net.minestom.server.entity.damage.Damage damage, String killer) {
        if (killer != null) return net.swofty.commons.text.Text.of("<7>你被 <f>{} <7>击败了", killer);
        if (damage != null && damage.getType().equals(net.minestom.server.entity.damage.DamageType.OUT_OF_WORLD)) {
            return net.swofty.commons.text.Text.of("<7>你掉入了虚空");
        }
        return net.swofty.commons.text.Text.of("<7>你倒下了");
    }

    private static String killerName(net.minestom.server.entity.damage.Damage damage) {
        if (damage == null) {
            return null;
        }
        net.minestom.server.entity.Entity attacker = damage.getAttacker() != null
                ? damage.getAttacker() : damage.getSource();
        if (attacker instanceof net.swofty.type.ravengardgeneric.entity.mob.RavengardMob mob) {
            return mob.displayName();
        }
        if (attacker instanceof net.minestom.server.entity.Player other) {
            return other.getUsername();
        }
        return null;
    }
}
