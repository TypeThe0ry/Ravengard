package net.swofty.type.ravengardgeneric.commands;

import net.minestom.server.command.builder.arguments.ArgumentType;
import net.swofty.type.generic.command.CommandParameters;
import net.swofty.type.generic.command.HypixelCommand;
import net.swofty.type.generic.user.categories.Rank;
import net.swofty.type.ravengardgeneric.queue.RavengardMatchMode;
import net.swofty.type.ravengardgeneric.queue.RavengardQueue;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

@CommandParameters(labels = "play", description = "加入天穹余烬匹配", usage = "/play <solo|trios>", permission = Rank.DEFAULT, allowsConsole = false)
public final class PlayCommand extends HypixelCommand {
    @Override
    public void registerUsage(MinestomCommand command) {
        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            RavengardQueue.chooseMode((RavengardPlayer) sender);
        });
        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            RavengardQueue.join((RavengardPlayer) sender, RavengardMatchMode.TRIOS);
        }, ArgumentType.Literal("ravengard"));
        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            RavengardQueue.join((RavengardPlayer) sender, RavengardMatchMode.SOLO);
        }, ArgumentType.Literal("solo"));
        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            RavengardQueue.join((RavengardPlayer) sender, RavengardMatchMode.TRIOS);
        }, ArgumentType.Literal("trios"));
    }
}
