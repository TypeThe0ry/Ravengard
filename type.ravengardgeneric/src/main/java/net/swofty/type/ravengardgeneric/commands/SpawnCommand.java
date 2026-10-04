package net.swofty.type.ravengardgeneric.commands;

import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.swofty.type.generic.command.CommandParameters;
import net.swofty.type.generic.command.HypixelCommand;
import net.swofty.type.generic.user.categories.Rank;
import net.swofty.type.ravengardgeneric.entity.animation.RavengardMobClip;
import net.swofty.type.ravengardgeneric.entity.mob.RavengardMob;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

import java.util.List;

@CommandParameters(
        labels = "spawnmob",
        description = "在你的位置生成一只天穹余烬怪物",
        usage = "/spawnmob <怪物>",
        permission = Rank.STAFF,
        allowsConsole = false)
public class SpawnCommand extends HypixelCommand {
    @Override
    public void registerUsage(MinestomCommand command) {
        var mobArg = ArgumentType.Word("mob").setSuggestionCallback((sender, context, suggestion) ->
                RavengardMobClip.available().forEach(name -> suggestion.addEntry(new SuggestionEntry(name))));

        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            RavengardPlayer player = (RavengardPlayer) sender;
            String name = context.get(mobArg);
            List<String> available = RavengardMobClip.available();
            if (!available.contains(name)) {
                player.sendMessage("<c>未知怪物。可选：{}", String.join(", ", available));
                return;
            }
            RavengardMobClip clip = RavengardMobClip.load(name);
            RavengardMob mob = new RavengardMob(clip, player.getPosition());
            mob.spawnMob(player.getInstance());
            player.sendMessage("<a>已生成 <f>{}<a>。", name);
        }, mobArg);

        var appearanceArg = ArgumentType.Word("appearance");
        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            RavengardPlayer player = (RavengardPlayer) sender;
            String name = context.get(mobArg);
            List<String> available = RavengardMobClip.available();
            if (!available.contains(name)) {
                player.sendMessage("<c>未知怪物。可选：{}", String.join(", ", available));
                return;
            }
            String appearance = context.get(appearanceArg);
            // inspection aid: the mob appears a few blocks ahead, turned to face the admin
            var view = player.getPosition();
            double radians = Math.toRadians(view.yaw());
            var ahead = view.add(-Math.sin(radians) * 4, 0, Math.cos(radians) * 4)
                    .withView(view.yaw() + 180, 0);
            new RavengardMob(RavengardMobClip.load(name), ahead, appearance)
                    .spawnMob(player.getInstance());
            player.sendMessage("<a>已生成 <f>{}<a>。", appearance);
        }, mobArg, appearanceArg);
    }
}
