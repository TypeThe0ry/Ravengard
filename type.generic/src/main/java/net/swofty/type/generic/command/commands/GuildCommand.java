package net.swofty.type.generic.command.commands;

import net.minestom.server.command.builder.arguments.ArgumentStringArray;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.swofty.commons.ServiceType;
import net.swofty.commons.guild.GuildData;
import net.swofty.commons.guild.GuildMember;
import net.swofty.commons.guild.GuildRank;
import net.swofty.commons.text.Text;
import net.swofty.proxyapi.ProxyService;
import net.swofty.type.generic.command.CommandParameters;
import net.swofty.type.generic.command.HypixelCommand;
import net.swofty.type.generic.guild.GuildManager;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.generic.user.categories.Rank;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@CommandParameters(labels = "g guild",
    description = "Guild management commands",
    usage = "/guild <subcommand>",
    permission = Rank.DEFAULT,
    allowsConsole = false)
public class GuildCommand extends HypixelCommand {
    private final List<UUID> pendingCommands = new ArrayList<>();

    @Override
    public void registerUsage(MinestomCommand command) {
        ArgumentStringArray arguments = ArgumentType.StringArray("arguments");
        ProxyService guildService = new ProxyService(ServiceType.GUILD);

        command.addSyntax((sender, context) -> {
            showHelp((HypixelPlayer) sender);
        });

        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            HypixelPlayer player = (HypixelPlayer) sender;
            if (pendingCommands.contains(player.getUuid())) return;
            pendingCommands.add(player.getUuid());

            Thread.startVirtualThread(() -> {
                try {
                    if (!guildService.isOnline().join()) {
                        player.sendMessage("<c>公会服务当前离线！");
                        return;
                    }

                    String[] args = context.get(arguments);
                    String sub = args[0].toLowerCase();
                    String argument = args.length > 1 ? args[1] : null;
                    String message = args.length > 1
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                        : "";

                    dispatch(player, sub, argument, message, args);
                } finally {
                    pendingCommands.remove(player.getUuid());
                }
            });
        }, arguments);
    }

    private void dispatch(HypixelPlayer player, String sub, String argument, String message, String[] args) {
        switch (sub) {
            case "help" -> showHelp(player);
            case "log", "history" -> showGuildLog(player);
            case "leave" -> GuildManager.leaveGuild(player);
            case "disband" -> GuildManager.disbandGuild(player);
            case "list", "info" -> showGuildInfo(player);
            case "online", "members" -> showOnlineMembers(player);
            case "member" -> showMember(player, argument);
            case "mypermissions", "permissions" -> showPermissions(player);
            case "top" -> showTop(player);
            case "discord" -> showDiscord(player, message);
            case "join" -> player.sendMessage("<c>该公会目前不接受加入申请。");
            case "menu" -> player.sendMessage("<e>打开你的个人资料并选择<a>公会<e>即可打开公会菜单。");
            case "party" -> player.sendMessage("<c>没有在线的公会成员可以组队。");
            case "quest" -> player.sendMessage("<e>你的公会目前没有进行中的公会任务。");
            case "notifications", "slow", "onlinemode", "toggle" -> GuildManager.changeSetting(player, sub, "toggle");
            case "create" ->
                withArgument(player, argument, "/guild create <名称>", value -> GuildManager.createGuild(player, value));
            case "invite" ->
                withArgument(player, argument, "/guild invite <玩家>", value -> GuildManager.invitePlayer(player, value));
            case "accept" ->
                withArgument(player, argument, "/guild accept <玩家>", value -> GuildManager.acceptInvite(player, value));
            case "promote" ->
                withArgument(player, argument, "/guild promote <玩家>", value -> GuildManager.promotePlayer(player, value));
            case "demote" ->
                withArgument(player, argument, "/guild demote <玩家>", value -> GuildManager.demotePlayer(player, value));
            case "transfer" ->
                withArgument(player, argument, "/guild transfer <玩家>", value -> GuildManager.transferOwnership(player, value));
            case "tag", "tagcolor", "rename", "finder" ->
                withArgument(player, argument, "/guild " + sub + " <值>", value -> GuildManager.changeSetting(player, sub, value));
            case "settings" -> {
                if (args.length < 3) player.sendMessage("<c>用法：{}", "/guild settings <设置项> <值>");
                else
                    GuildManager.changeSetting(player, args[1], String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
            }
            case "chat" ->
                withArgument(player, argument, "/guild chat <消息>", value -> GuildManager.sendChat(player, message, false));
            case "officerchat", "oc" ->
                withArgument(player, argument, "/guild officerchat <消息>", value -> GuildManager.sendChat(player, message, true));
            case "motd" -> handleMotd(player, args);
            case "description" ->
                withArgument(player, argument, "/guild " + sub + " <消息>", value -> GuildManager.changeSetting(player, sub, message));
            case "mute" -> GuildManager.mutePlayer(player, argument == null ? "everyone" : argument,
                args.length > 2 ? parseDuration(args[2]) : 3600000);
            case "unmute" -> GuildManager.unmutePlayer(player, argument == null ? "everyone" : argument);
            case "kick" -> withArgument(player, argument, "/guild kick <玩家> [原因]", value -> {
                String reason = args.length > 2
                    ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
                    : "";
                GuildManager.kickPlayer(player, value, reason);
            });
            case "setrank" -> {
                if (args.length < 3) player.sendMessage("<c>用法：{}", "/guild setrank <玩家> <职位>");
                else GuildManager.setRank(player, args[1], args[2]);
            }
            default -> {
                player.sendMessage("<c>未知的公会命令。使用 /guild 查看帮助。");
            }
        }
    }

    private long parseDuration(String input) {
        try {
            long amount = Long.parseLong(input.substring(0, input.length() - 1));
            return amount * switch (input.charAt(input.length() - 1)) {
                case 's' -> 1000;
                case 'm' -> 60000;
                case 'h' -> 3600000;
                case 'd' -> 86400000;
                default -> 60000;
            };
        } catch (RuntimeException ignored) {
            return 3600000;
        }
    }

    private void showMember(HypixelPlayer player, String name) {
        GuildData guild = GuildManager.getGuildFromPlayer(player);
        UUID uuid = name == null ? player.getUuid() : net.swofty.type.generic.data.HypixelDataHandler.getPotentialUUIDFromName(name);
        GuildMember member = guild == null || uuid == null ? null : guild.getMember(uuid);
        if (member == null) {
            player.sendMessage("<c>该玩家不在你的公会中！");
            return;
        }
        player.sendMessage("<sep>");
        player.sendMessage("<a>{}", HypixelPlayer.getRawName(uuid));
        player.sendMessage("<7>职位：<e>{}", member.getRankName());
        player.sendMessage("<7>本周公会经验：<e>{:,}", member.getWeeklyGexp());
        player.sendMessage("<7>公会经验总计：<e>{:,}", member.getTotalGexp());
        player.sendMessage("<sep>");
    }

    private void showPermissions(HypixelPlayer player) {
        GuildData guild = GuildManager.getGuildFromPlayer(player);
        GuildRank rank = guild == null ? null : guild.getMemberRank(player.getUuid());
        if (rank == null) {
            player.sendMessage("<c>你不在任何公会中！");
            return;
        }
        player.sendMessage("<sep>");
        player.sendMessage("<6><e>{}<6> 的权限", rank.getName());
        rank.getPermissions().forEach(permission -> player.sendMessage("<a>✔ <7>{}", permission.name()));
        player.sendMessage("<sep>");
    }

    private void showTop(HypixelPlayer player) {
        GuildData guild = GuildManager.getGuildFromPlayer(player);
        if (guild == null) {
            player.sendMessage("<c>你不在任何公会中！");
            return;
        }
        player.sendMessage("<sep>");
        player.sendMessage("<6>公会经验排行");
        guild.getMembers().stream().sorted(java.util.Comparator.comparingLong(GuildMember::getTotalGexp).reversed()).limit(10)
            .forEach(member -> player.sendMessage("<e>{} <7>- <a>{:,}",
                HypixelPlayer.getRawName(member.getUuid()), member.getTotalGexp()));
        player.sendMessage("<sep>");
    }

    private void showDiscord(HypixelPlayer player, String value) {
        if (!value.isBlank()) {
            GuildManager.changeSetting(player, "discord", value);
            return;
        }
        GuildData guild = GuildManager.getGuildFromPlayer(player);
        if (guild == null || guild.getDiscordLink().isBlank()) {
            player.sendMessage("<c>你的公会尚未设置 Discord 链接！");
        } else {
            player.sendMessage("<6>公会 Discord：<b>{}", guild.getDiscordLink());
        }
    }

    private void handleMotd(HypixelPlayer player, String[] args) {
        if (args.length == 1 || args[1].equalsIgnoreCase("help")) {
            showMotdHelp(player);
            return;
        }
        GuildData guild = GuildManager.getGuildFromPlayer(player);
        if (guild == null) {
            player.sendMessage("<c>你不在任何公会中！");
            return;
        }
        List<String> lines = new ArrayList<>(guild.getMotd() == null || guild.getMotd().isBlank()
            ? List.of() : List.of(guild.getMotd().split("\\n", -1)));
        switch (args[1].toLowerCase()) {
            case "list", "preview" -> {
                player.sendMessage("<sep>");
                lines.forEach(line -> player.sendMessage("<f>{}", line));
                player.sendMessage("<sep>");
            }
            case "clear" -> GuildManager.changeSetting(player, "motd", "");
            case "add" -> {
                if (args.length < 3) player.sendMessage("<c>用法：{}", "/guild motd add <文本>");
                else {
                    lines.add(String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
                    GuildManager.changeSetting(player, "motd", String.join("\n", lines));
                }
            }
            case "set" -> {
                if (args.length < 4) {
                    player.sendMessage("<c>用法：{}", "/guild motd set <行号> <文本>");
                    return;
                }
                try {
                    int line = Integer.parseInt(args[2]) - 1;
                    if (line < 0 || line >= lines.size()) throw new IndexOutOfBoundsException();
                    lines.set(line, String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)));
                    GuildManager.changeSetting(player, "motd", String.join("\n", lines));
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    player.sendMessage("<c>该公告行不存在！");
                }
            }
            default -> showMotdHelp(player);
        }
    }

    private void showGuildLog(HypixelPlayer player) {
        GuildData guild = GuildManager.getGuildFromPlayer(player);
        player.sendMessage("<sep>");
        player.sendMessage("                            <6>公会日志（第 1 页，共 1 页）");
        player.sendMessage("");
        if (guild == null || guild.getAuditLog().isEmpty()) player.sendMessage("<7>没有公会日志记录。");
        else guild.getAuditLog().stream().limit(10)
                .forEach(player::sendMessage);
        player.sendMessage("<sep>");
    }

    private void withArgument(HypixelPlayer player, String argument, String usage, java.util.function.Consumer<String> action) {
        if (argument == null) {
            player.sendMessage("<c>用法：{}", usage);
            return;
        }
        action.accept(argument);
    }

    private void showGuildInfo(HypixelPlayer player) {
        GuildData guild = GuildManager.getGuildFromPlayer(player);
        if (guild == null) {
            player.sendMessage("<sep>");
            player.sendMessage("<c>你不在任何公会中！");
            player.sendMessage("<sep>");
            return;
        }

        player.sendMessage("<sep>");
        Text name = Text.of("<6><l>{}</l>", guild.getName());
        if (guild.getTag() != null) {
            name = name.append(" <7>[{}]", Text.legacy(guild.getTagColor() + guild.getTag()));
        }
        player.sendMessage(name);
        player.sendMessage("<7>等级：<a>{}", guild.getLevel());
        player.sendMessage("<7>成员：<a>{}/{}", guild.getMembers().size(), GuildData.MAX_MEMBERS);
        player.sendMessage("<7>公会经验：<a>{}", guild.getTotalGexp());
        if (guild.getMotd() != null && !guild.getMotd().isEmpty()) {
            player.sendMessage("<7>公告：<f>{}", guild.getMotd());
        }
        if (guild.getDescription() != null && !guild.getDescription().isEmpty()) {
            player.sendMessage("<7>简介：<f>{}", guild.getDescription());
        }
        player.sendMessage("<sep>");
    }

    private void showOnlineMembers(HypixelPlayer player) {
        GuildData guild = GuildManager.getGuildFromPlayer(player);
        if (guild == null) {
            player.sendMessage("<sep>");
            player.sendMessage("<c>你不在任何公会中！");
            player.sendMessage("<sep>");
            return;
        }

        player.sendMessage("<sep>");
        player.sendMessage("<6>公会成员 - <a>{}", guild.getMembers().size());
        Text memberList = Text.join(Text.of("<7>, "), guild.getMembers().stream()
            .map(m -> HypixelPlayer.getDisplayName(m.getUuid()))
            .toList());
        player.sendMessage(memberList);
        player.sendMessage("<sep>");
    }

    private void showHelp(HypixelPlayer player) {
        player.sendMessage("<sep>");
        player.sendMessage("公会命令：");
        String[][] commands = {
            {"accept", "接受公会邀请"}, {"chat <chat message>", "在公会聊天频道发送消息"},
            {"create <name>", "创建指定名称的公会"}, {"demote <player>", "将该玩家降一级职位"},
            {"disband", "解散公会"}, {"discord", "设置或查看公会的 Discord 链接"}, {"help", "显示本帮助信息"},
            {"history", "查看最近 24 小时的公会事件"}, {"info", "显示你的公会信息"},
            {"invite <player>", "邀请该玩家加入你的公会"}, {"join <guild>", "申请加入指定公会"},
            {"kick <player> <reason>", "将该玩家踢出公会"}, {"leave", "离开当前公会"},
            {"log", "查看审计日志"}, {"member", "显示公会成员的信息"}, {"members", "列出公会中的玩家"},
            {"menu", "打开公会菜单"}, {"motd", "修改公会公告"}, {"mute <player/everyone> <time>", "禁言某位玩家或整个公会"},
            {"mypermissions", "查看你所在职位的权限"}, {"notifications", "开关公会成员上线/下线通知"},
            {"officerchat", "在公会官员聊天频道发送消息"}, {"online", "显示当前在线的公会成员"},
            {"onlinemode", "切换公会列表是否显示离线玩家"}, {"party", "与在线的公会成员组成队伍"},
            {"permissions", "修改某个职位的权限"}, {"promote <player>", "将该玩家升一级职位"},
            {"quest", "显示当前公会任务的信息"}, {"rename <name>", "重命名公会"},
            {"setrank <player> <rank>", "设置玩家的职位"}, {"settings <setting> <value>", "修改公会设置"},
            {"slow", "开关慢速聊天，公会成员每条消息须间隔 10 秒"}, {"tag", "设置公会 [标签]"},
            {"tagcolor", "设置公会标签颜色"}, {"toggle", "开关你自己的公会聊天"}, {"top", "列出获得经验最多的玩家"},
            {"transfer <player>", "将公会转让给另一位玩家"}, {"unmute <player/everyone>", "解除某位玩家或整个公会的禁言"}
        };
        for (String[] entry : commands) sendClickableCommand(player, entry[0], entry[1]);
        player.sendMessage("<sep>");
    }

    private void showMotdHelp(HypixelPlayer player) {
        player.sendMessage("<sep>");
        player.sendMessage("公会公告命令：");
        sendClickableCommand(player, "motd add <text>", "在公告中添加一行");
        sendClickableCommand(player, "motd clear", "清空公告");
        sendClickableCommand(player, "motd help", "显示本帮助信息");
        sendClickableCommand(player, "motd list", "列出公告的各行");
        sendClickableCommand(player, "motd preview", "预览玩家看到的公告效果");
        sendClickableCommand(player, "motd set <line> <text>", "设置公告中的某一行");
        player.sendMessage("<sep>");
    }

    private void sendClickableCommand(HypixelPlayer player, String command, String description) {
        String fullCommand = "/guild " + command;
        player.sendMessage("<click:suggest:'{0}'><hover:'<7>点击将命令填入聊天栏'><e>{0}<7> - <b>{1}</hover></click>",
                fullCommand, description);
    }
}
