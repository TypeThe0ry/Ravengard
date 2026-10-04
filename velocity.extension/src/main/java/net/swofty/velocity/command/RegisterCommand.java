package net.swofty.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.swofty.commons.config.ConfigProvider;
import net.swofty.commons.text.Text;
import net.swofty.velocity.SkyBlockVelocity;
import net.swofty.velocity.data.AuthenticationDatabase;

public class RegisterCommand implements SimpleCommand {

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

        AuthenticationDatabase.AuthenticationData data = new AuthenticationDatabase(player.getUniqueId()).getAuthenticationData();
        if (data != null) {
            player.sendMessage(Text.of("<c>你的账号已经注册过了！"));
        }

        String[] args = invocation.arguments();
        if (args.length != 2) {
            player.sendMessage(Text.of("<c>你必须先注册才能在本服务器游玩！"));
            player.sendMessage(Text.of("<c>请在 Minecraft 聊天栏中输入 <6>/register \\<密码> \\<密码><c>。"));
            return;
        }

        if (!args[0].equals(args[1])) {
            player.sendMessage(Text.of("<c>两次输入的密码不一致。"));
            return;
        }

        AuthenticationDatabase.AuthenticationData newData = AuthenticationDatabase.makeFromPassword(args[1]);
        new AuthenticationDatabase(player.getUniqueId()).setAuthenticationData(newData);

        player.sendMessage(Text.of("<a>账号注册成功！"));
        player.sendMessage(Text.of("<a>现在请在 Minecraft 聊天栏中输入 <6>/login \\<密码><a>。"));
    }
}
