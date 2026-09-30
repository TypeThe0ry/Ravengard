package net.swofty.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.swofty.commons.text.Text;
import net.swofty.commons.ServerType;
import net.swofty.velocity.gamemanager.TransferHandler;

public class LimboCommand implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!(source instanceof Player player)) {
            return;
        }
        player.sendMessage(Text.of("<7>Sending to Ravengard Lobby..."));
        new TransferHandler(player).transferTo(ServerType.RAVENGARD_LOBBY);
    }
}
