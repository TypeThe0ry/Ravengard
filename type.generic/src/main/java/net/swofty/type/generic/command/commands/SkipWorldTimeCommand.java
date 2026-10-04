package net.swofty.type.generic.command.commands;

import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.number.ArgumentLong;
import net.minestom.server.entity.Player;
import net.swofty.type.generic.command.CommandParameters;
import net.swofty.type.generic.command.HypixelCommand;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.generic.user.categories.Rank;

@CommandParameters(
    allowsConsole = false,
    description = "Skips the time in the current instance",
    permission = Rank.STAFF,
    labels = "worldtime skipworldtime",
    usage = "/worldtime (时间刻)"
)
public class SkipWorldTimeCommand extends HypixelCommand {

    @Override
    public void registerUsage(MinestomCommand command) {
        ArgumentLong timeArg = ArgumentType.Long("tick");
        command.addSyntax((sender, context) -> {
            long time = context.get(timeArg);
            if (time < 0) {
                sender.sendMessage("<c>时间刻不能为负数。");
                return;
            }
            if (time > 24000) {
                sender.sendMessage("<c>时间刻不能超过 24000。");
                return;
            }
            if (sender instanceof Player player) {
                player.getInstance().setTime(time);
                ((HypixelPlayer) player).sendMessage("<a>已将世界时间设为 <e>{}<a> 刻。", time);
            }
        }, timeArg);
    }
}
