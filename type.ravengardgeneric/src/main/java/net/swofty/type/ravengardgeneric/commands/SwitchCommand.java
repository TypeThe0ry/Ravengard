package net.swofty.type.ravengardgeneric.commands;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.swofty.type.generic.command.CommandParameters;
import net.swofty.type.generic.command.HypixelCommand;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.generic.user.categories.Rank;
import net.swofty.type.generic.world.HypixelWorldLoader;
import org.tinylog.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CommandParameters(
        labels = "switch",
        description = "在录制的 polar 世界之间切换",
        usage = "/switch <世界>",
        permission = Rank.STAFF,
        allowsConsole = false
)
public class SwitchCommand extends HypixelCommand {
    private static final Path CAPTURES = Path.of("configuration/world/captures");
    private static final String EXTENSION = ".polar";

    // captures are several MB each, so a world is read once and reused on later switches
    private static final Map<String, Instance> loaded = new ConcurrentHashMap<>();

    @Override
    public void registerUsage(MinestomCommand command) {
        var worldArg = ArgumentType.Word("world").setSuggestionCallback((sender, context, suggestion) -> {
            for (String name : available()) {
                suggestion.addEntry(new SuggestionEntry(name));
            }
        });

        command.addSyntax((sender, context) -> {
            HypixelPlayer player = (HypixelPlayer) sender;
            String name = context.get(worldArg);

            if (!available().contains(name)) {
                String options = available().stream()
                        .map(option -> "<f>" + option)
                        .collect(Collectors.joining("<7>, "));
                player.sendMessage("<c>没有名为 <f>{}<c> 的录制片段。可用：" + options, name);
                return;
            }

            Instance instance = loaded.get(name);
            if (instance == null) {
                player.sendMessage("<7>正在加载 <f>{}<7>……", name);
                try {
                    InstanceContainer source = MinecraftServer.getInstanceManager().createInstanceContainer();
                    instance = HypixelWorldLoader.loadFrom(CAPTURES.resolve(name + EXTENSION),
                            source, MinecraftServer.getInstanceManager());
                    loaded.put(name, instance);
                } catch (IOException exception) {
                    Logger.error(exception, "Failed to load capture {}", name);
                    player.sendMessage("<c>加载 <f>{}<c> 失败：{}", name, exception.getMessage());
                    return;
                }
            }

            if (player.getInstance() == instance) {
                player.sendMessage("<7>你已经在 <f>{}<7> 中了。", name);
                return;
            }

            player.setInstance(instance, player.getPosition()).thenRun(() ->
                    player.sendMessage("<a>已切换到 <f>{}<a>。", name));
        }, worldArg);

        command.addSyntax((sender, context) -> {
            HypixelPlayer player = (HypixelPlayer) sender;
            List<String> names = available();
            if (names.isEmpty()) {
                player.sendMessage("<c>在 {} 中没有找到录制片段。", CAPTURES);
                return;
            }
            String list = names.stream()
                    .map(option -> "<f>" + option)
                    .collect(Collectors.joining("<7>, "));
            player.sendMessage("<7>录制片段：" + list);
        });
    }

    private static List<String> available() {
        if (!Files.isDirectory(CAPTURES)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(CAPTURES)) {
            List<String> names = new ArrayList<>();
            files.filter(path -> path.getFileName().toString().endsWith(EXTENSION))
                    .forEach(path -> {
                        String file = path.getFileName().toString();
                        names.add(file.substring(0, file.length() - EXTENSION.length()));
                    });
            names.sort(String::compareTo);
            return names;
        } catch (IOException exception) {
            Logger.error(exception, "Failed to list captures in {}", CAPTURES);
            return List.of();
        }
    }
}
