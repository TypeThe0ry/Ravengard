package net.swofty.type.ravengardgeneric.commands;

import net.minestom.server.command.builder.arguments.ArgumentType;
import net.swofty.type.generic.command.CommandParameters;
import net.swofty.type.generic.command.HypixelCommand;
import net.swofty.type.generic.user.categories.Rank;
import net.swofty.type.ravengardgeneric.classes.RavengardAbility;
import net.swofty.type.ravengardgeneric.classes.RavengardClass;
import net.swofty.type.ravengardgeneric.classes.RavengardSelection;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

import java.util.Arrays;
import java.util.stream.Collectors;

@CommandParameters(
        labels = "class",
        description = "设置你的天穹余烬职业",
        usage = "/class <knight|warrior|hunter|assassin>",
        permission = Rank.DEFAULT,
        allowsConsole = false
)
public class ClassCommand extends HypixelCommand {

    @Override
    public void registerUsage(MinestomCommand command) {
        var classArg = ArgumentType.String("class");

        command.addSyntax((sender, context) -> {
            RavengardPlayer player = (RavengardPlayer) sender;
            if (net.swofty.type.generic.HypixelConst.getTypeLoader().getType()
                    == net.swofty.commons.ServerType.RAVENGARD_DUNGEON) {
                player.sendMessage("<c>地牢内不能更换职业！");
                return;
            }
            RavengardClass chosen = RavengardClass.fromKey(context.get(classArg));

            if (chosen == null) {
                player.sendMessage("<c>未知职业。可选：{}", Arrays.stream(RavengardClass.values())
                        .map(RavengardClass::getDisplayName)
                        .collect(Collectors.joining(", ")));
                return;
            }

            boolean fromTutorial = RavengardSelection.inTutorialWorld(player);
            player.setRavengardClass(chosen);
            player.setTutorial(false);
            RavengardSelection.giveKit(player, chosen);
            net.swofty.type.ravengardgeneric.profile.RavengardProfiles.saveActive(player);
            if (fromTutorial) RavengardSelection.leaveTutorial(player);
            player.sendMessage("{} <f>你现在是<e>{}<f>了。", chosen.getIcon(), chosen.getDisplayName());
            for (RavengardAbility ability : chosen.getAbilities()) {
                player.sendMessage("  {} <7>{} <8>({})", ability.getIcon(), ability.getDisplayName(),
                        ability.getCooldownText());
            }
        }, classArg);

        command.addSyntax((sender, context) -> {
            RavengardPlayer player = (RavengardPlayer) sender;
            RavengardClass current = player.getRavengardClass();
            if (current == null) {
                player.sendMessage("<7>你还没有选择职业。");
            } else {
                player.sendMessage("{} <f>你是<e>{}<f>。", current.getIcon(), current.getDisplayName());
            }
        });
    }
}
