package net.swofty.type.ravengardgeneric.commands;

import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.swofty.type.generic.command.CommandParameters;
import net.swofty.type.generic.command.HypixelCommand;
import net.swofty.type.generic.gui.v2.ViewNavigator;
import net.swofty.type.generic.user.categories.Rank;
import net.swofty.type.ravengardgeneric.entity.animation.AnimReviewService;
import net.swofty.type.ravengardgeneric.entity.animation.RavengardReviewClip;
import net.swofty.type.ravengardgeneric.gui.GUIAnimReview;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

@CommandParameters(
        labels = "animreview",
        description = "审查录制的动画并标记片段",
        usage = "/animreview [start <片段>|stop|goto <刻>|markstart|mark <名称>|marks|unmark <名称>]",
        permission = Rank.STAFF,
        allowsConsole = false)
public class AnimReviewCommand extends HypixelCommand {
    @Override
    public void registerUsage(MinestomCommand command) {
        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            RavengardPlayer player = (RavengardPlayer) sender;
            ViewNavigator.get(player).push(new GUIAnimReview(null));
        });

        var clipArg = ArgumentType.Word("clip").setSuggestionCallback((sender, context, suggestion) ->
                RavengardReviewClip.available().forEach(name -> suggestion.addEntry(new SuggestionEntry(name))));
        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            AnimReviewService.start((RavengardPlayer) sender, context.get(clipArg));
        }, ArgumentType.Literal("start"), clipArg);

        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            AnimReviewService.stop((RavengardPlayer) sender);
            ((RavengardPlayer) sender).sendMessage("<a>已停止审阅。");
        }, ArgumentType.Literal("stop"));

        var tickArg = ArgumentType.Integer("tick").min(0);
        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            withSession((RavengardPlayer) sender, session -> session.gotoTick(context.get(tickArg)));
        }, ArgumentType.Literal("goto"), tickArg);

        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            withSession((RavengardPlayer) sender, session -> session.control("markstart"));
        }, ArgumentType.Literal("markstart"));

        var nameArg = ArgumentType.Word("name").setSuggestionCallback((sender, context, suggestion) -> {
            for (String suggested : new String[]{"idle", "walk", "attack_1", "attack_2",
                    "attack_3", "charge", "hurt", "death"}) {
                suggestion.addEntry(new SuggestionEntry(suggested));
            }
        });
        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            withSession((RavengardPlayer) sender, session -> session.mark(context.get(nameArg)));
        }, ArgumentType.Literal("mark"), nameArg);

        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            withSession((RavengardPlayer) sender, AnimReviewService.Session::listMarks);
        }, ArgumentType.Literal("marks"));

        command.addSyntax((sender, context) -> {
            if (!permissionCheck(sender)) return;
            withSession((RavengardPlayer) sender, session -> session.unmark(context.get(nameArg)));
        }, ArgumentType.Literal("unmark"), nameArg);
    }

    private static void withSession(RavengardPlayer player,
                                    java.util.function.Consumer<AnimReviewService.Session> action) {
        AnimReviewService.Session session = AnimReviewService.session(player);
        if (session == null) {
            player.sendMessage("<c>当前没有进行中的审查。使用 /animreview 选择一个抓包。");
            return;
        }
        action.accept(session);
    }
}
