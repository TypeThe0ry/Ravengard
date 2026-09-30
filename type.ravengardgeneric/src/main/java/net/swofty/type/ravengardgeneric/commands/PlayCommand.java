package net.swofty.type.ravengardgeneric.commands;

import net.minestom.server.command.builder.arguments.ArgumentType;
import net.swofty.type.generic.command.CommandParameters;
import net.swofty.type.generic.command.HypixelCommand;
import net.swofty.type.generic.user.categories.Rank;
import net.swofty.type.ravengardgeneric.queue.RavengardQueue;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

@CommandParameters(labels = "play", description = "Join Ravengard matchmaking", usage = "/play ravengard", permission = Rank.DEFAULT, allowsConsole = false)
public final class PlayCommand extends HypixelCommand {
    @Override
    public void registerUsage(MinestomCommand command) {
        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            RavengardQueue.join((RavengardPlayer) sender);
        }, ArgumentType.Literal("ravengard"));
    }
}
