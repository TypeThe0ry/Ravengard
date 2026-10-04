package net.swofty.type.ravengardgeneric.commands;

import net.swofty.type.generic.command.CommandParameters;
import net.swofty.type.generic.command.HypixelCommand;
import net.swofty.type.generic.user.categories.Rank;
import net.swofty.type.ravengardgeneric.network.NetworkHome;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

@CommandParameters(labels = "spawn zhucheng", description = "回到主城",
        usage = "/spawn", permission = Rank.DEFAULT, allowsConsole = false)
public class HomeCommand extends HypixelCommand {
    @Override
    public void registerUsage(MinestomCommand command) {
        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            NetworkHome.send((RavengardPlayer) sender);
        });
    }
}
