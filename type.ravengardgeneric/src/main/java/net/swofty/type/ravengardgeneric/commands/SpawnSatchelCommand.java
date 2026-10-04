package net.swofty.type.ravengardgeneric.commands;

import net.minestom.server.entity.Player;
import net.swofty.type.generic.command.CommandParameters;
import net.swofty.type.generic.command.HypixelCommand;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.generic.user.categories.Rank;

import java.util.function.Consumer;

@CommandParameters(
        labels = "spawnsatchel",
        description = "在你的位置放置一个战利品袋",
        usage = "/spawnsatchel",
        permission = Rank.STAFF,
        allowsConsole = false)
public class SpawnSatchelCommand extends HypixelCommand {
    public static volatile Consumer<Player> spawner;

    @Override
    public void registerUsage(MinestomCommand command) {
        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            HypixelPlayer player = (HypixelPlayer) sender;
            Consumer<Player> hook = spawner;
            if (hook == null) {
                player.sendMessage("<c>战利品袋只存在于地牢服务器。");
                return;
            }
            hook.accept(player);
            player.sendMessage("<a>已放置战利品袋。");
        });
    }
}
