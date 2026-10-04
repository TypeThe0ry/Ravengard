package net.swofty.type.ravengardgeneric.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentStringArray;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;

import java.util.Optional;
import java.util.UUID;

/** CarbonChat-compatible private messages: /whisper and /reply reach players on any server. */
final class CarbonWhisperCommands {
    private CarbonWhisperCommands() {
    }

    static void register() {
        CommandManager commands = MinecraftServer.getCommandManager();
        for (String name : new String[]{"msg", "message", "whipser", "whisper", "tell", "w", "m", "reply", "r"}) {
            Command existing = commands.getCommand(name);
            if (existing != null) commands.unregister(existing);
        }
        commands.register(whisper());
        commands.register(reply());
    }

    private static Command whisper() {
        Command command = new Command("whisper", "msg", "message", "tell", "w", "m");
        ArgumentWord target = ArgumentType.Word("player");
        target.setSuggestionCallback((sender, context, suggestion) -> {
            String typed = suggestion.getInput().substring(suggestion.getStart() - 1).trim().toLowerCase(java.util.Locale.ROOT);
            CarbonChatBridge bridge = CarbonChatBridge.get();
            java.util.stream.Stream.concat(
                            MinecraftServer.getConnectionManager().getOnlinePlayers().stream().map(Player::getUsername),
                            bridge == null ? java.util.stream.Stream.<String>empty() : bridge.remoteNames().stream())
                    .distinct()
                    .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(typed))
                    .forEach(name -> suggestion.addEntry(new SuggestionEntry(name)));
        });
        ArgumentStringArray message = ArgumentType.StringArray("message");
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(
                Component.text("用法：/whisper <玩家> <消息>", NamedTextColor.RED)));
        command.addSyntax((sender, context) -> {
            if (sender instanceof Player player) {
                send(player, context.get(target), String.join(" ", context.get(message)));
            }
        }, target, message);
        return command;
    }

    private static Command reply() {
        Command command = new Command("reply", "r");
        ArgumentStringArray message = ArgumentType.StringArray("message");
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(
                Component.text("用法：/reply <消息>", NamedTextColor.RED)));
        command.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            CarbonChatBridge bridge = CarbonChatBridge.get();
            UUID target = bridge == null ? null : bridge.replyTarget(player.getUuid());
            if (target == null) {
                tell(player, Component.text("没有可以回复的人。", NamedTextColor.RED));
                return;
            }
            Player local = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(target);
            Optional<CarbonChatBridge.NetworkPlayer> remote = bridge.findRemote(target);
            String name = local != null ? local.getUsername() : remote.map(CarbonChatBridge.NetworkPlayer::name).orElse(null);
            if (name == null) {
                tell(player, Component.text("对方已经不在线了。", NamedTextColor.RED));
                return;
            }
            send(player, name, String.join(" ", context.get(message)));
        }, message);
        return command;
    }

    private static void send(Player sender, String targetName, String text) {
        CarbonChatBridge bridge = CarbonChatBridge.get();
        if (bridge == null || text.isBlank()) return;
        if (sender.getUsername().equalsIgnoreCase(targetName)) {
            tell(sender, Component.text("你不能给自己发私信。", NamedTextColor.RED));
            return;
        }
        Component message = Component.text(text);
        Player local = MinecraftServer.getConnectionManager().getOnlinePlayers().stream()
                .filter(player -> player.getUsername().equalsIgnoreCase(targetName))
                .findFirst().orElse(null);
        if (local != null) {
            tell(sender, CarbonChatFormat.whisperTo(local.getUsername(), message));
            tell(local, CarbonChatFormat.whisperFrom(sender.getUsername(), message));
            bridge.rememberConversation(sender.getUuid(), local.getUuid());
            bridge.rememberConversation(local.getUuid(), sender.getUuid());
            return;
        }
        Optional<CarbonChatBridge.NetworkPlayer> remote = bridge.findRemote(targetName);
        if (remote.isEmpty()) {
            tell(sender, Component.text("玩家 " + targetName + " 不在线。", NamedTextColor.RED));
            return;
        }
        tell(sender, CarbonChatFormat.whisperTo(remote.get().name(), message));
        bridge.whisperRemote(sender, remote.get(), text);
    }

    private static void tell(Player player, Component line) {
        player.sendMessage(line);
    }
}
