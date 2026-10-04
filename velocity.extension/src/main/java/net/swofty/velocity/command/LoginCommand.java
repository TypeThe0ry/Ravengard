package net.swofty.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.swofty.commons.ServerType;
import net.swofty.commons.config.ConfigProvider;
import net.swofty.commons.text.Text;
import net.swofty.velocity.SkyBlockVelocity;
import net.swofty.velocity.data.AuthenticationDatabase;
import net.swofty.velocity.gamemanager.TransferHandler;

public class LoginCommand implements SimpleCommand {

    @Override
    public boolean hasPermission(Invocation invocation) {
        return ConfigProvider.settings().isRequireAuth();
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!(source instanceof Player player)) {
            return;
        }

        if (!ConfigProvider.settings().isRequireAuth()) return;

        if (!SkyBlockVelocity.getUnauthenticated().contains(player.getUniqueId())) return;

        String[] args = invocation.arguments();
        if (args.length != 1) {
            player.sendMessage(Text.of("<c>请在 Minecraft 聊天栏中输入 <6>/login \\<密码><c>。"));
            player.sendMessage(Text.of("<c>这不是命令，只是一条消息，其他人看不到。"));
            return;
        }

        AuthenticationDatabase.AuthenticationData data = new AuthenticationDatabase(player.getUniqueId()).getAuthenticationData();
        if (data == null) {
            player.sendMessage(Text.of("<c>你必须先执行 <6>/register \\<密码> \\<密码><c>！"));
            return;
        }

        if (data.matches(args[0])) {
            SkyBlockVelocity.getUnauthenticated().remove(player.getUniqueId());
            player.sendMessage(Text.of("<a>登录成功！"));
            new TransferHandler(player).transferTo(ServerType.RAVENGARD_LOBBY);
        } else {
            player.sendMessage(Text.of("<c>密码错误。"));
        }
    }
}
