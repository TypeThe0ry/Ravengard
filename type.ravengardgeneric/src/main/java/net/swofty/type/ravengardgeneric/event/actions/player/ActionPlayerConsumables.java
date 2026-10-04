package net.swofty.type.ravengardgeneric.event.actions.player;

import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.ItemStack;
import net.swofty.type.generic.event.EventNodes;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.generic.event.phase.EventPhase;
import net.swofty.type.generic.event.phase.PhasedEvent;
import net.swofty.type.ravengardgeneric.classes.RavengardAbilityService;
import net.swofty.type.ravengardgeneric.item.RavengardItemType;
import net.swofty.type.ravengardgeneric.item.attribute.RavengardItemAttributeHandler;
import net.swofty.type.ravengardgeneric.item.components.StandardItemComponent;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consumables heal what their captured effect line says: the amount and optional duration are
 * read straight from the item's effect text, so a new potion works by existing in the config.
 */
public class ActionPlayerConsumables implements HypixelEventClass {
    private static final Pattern AMOUNT = Pattern.compile("\\+?(\\d+) ?HP");
    private static final Pattern DURATION = Pattern.compile("(\\d+) ?(?:seconds?|秒)");

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = true, phase = EventPhase.GAMEPLAY)
    public void onUse(PlayerUseItemEvent event) {
        if (!(event.getPlayer() instanceof RavengardPlayer player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        RavengardItemType type = new RavengardItemAttributeHandler(stack).getType();
        if (type == null) {
            return;
        }
        StandardItemComponent standard = type.component(StandardItemComponent.class);
        if (standard == null || !"CONSUMABLE".equals(standard.getStandardItemType())
                || type.getEffect() == null) {
            return;
        }

        event.setCancelled(true);
        Matcher amountMatcher = AMOUNT.matcher(type.getEffect());
        if (!amountMatcher.find()) {
            return;
        }
        int amount = Integer.parseInt(amountMatcher.group(1));
        Matcher durationMatcher = DURATION.matcher(type.getEffect());
        int seconds = durationMatcher.find() ? Integer.parseInt(durationMatcher.group(1)) : 0;
        RavengardAbilityService.consumeHeal(player, amount, seconds);

        ItemStack held = player.getItemInHand(event.getHand());
        if (held.amount() > 1) {
            player.setItemInHand(event.getHand(), held.withAmount(held.amount() - 1));
        } else {
            player.setItemInHand(event.getHand(), ItemStack.AIR);
        }
    }
}
