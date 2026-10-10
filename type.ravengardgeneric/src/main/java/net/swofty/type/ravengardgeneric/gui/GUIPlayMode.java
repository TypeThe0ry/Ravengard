package net.swofty.type.ravengardgeneric.gui;

import net.swofty.type.generic.gui.v2.DefaultState;
import net.swofty.type.generic.gui.v2.ViewLayout;
import net.swofty.type.generic.gui.v2.context.ViewContext;
import net.swofty.type.ravengardgeneric.queue.RavengardMatchMode;
import net.swofty.type.ravengardgeneric.queue.RavengardQueue;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

public class GUIPlayMode extends RavengardView {
    private static final int SLOT_SOLO = 29;
    private static final int SLOT_TRIOS = 32;

    @Override
    protected String title() {
        return "选择模式";
    }

    @Override
    protected void content(ViewLayout<DefaultState> layout, DefaultState state, ViewContext ctx) {
        interactive(layout, SLOT_SOLO, RavengardItems.button(RavengardButton.STATUE_DEFAULT)
                        .label("<a>单人")
                        .blankLine()
                        .lore("<7>独自一人，与所有人为敌。",
                                "<f> ◦ <7>最多 <e>" + RavengardMatchMode.SOLO.maxPlayers() + "<7> 人",
                                "<f> ◦ <7>1 人即可开始，默认 <e>60<7> 秒倒计时",
                                "<f> ◦ <7>满 <e>" + RavengardMatchMode.SOLO.fastStartPlayers()
                                        + "<7> 人后缩短为 <e>30<7> 秒")
                        .blankLine()
                        .lore("<e>点击加入单人队列！"),
                (click, viewContext) -> join(viewContext, RavengardMatchMode.SOLO));

        interactive(layout, SLOT_TRIOS, RavengardItems.button(RavengardButton.TROPHY)
                        .label("<a>三人组")
                        .blankLine()
                        .lore("<7>三人一队，与其他队伍对抗。",
                                "<f> ◦ <7>最多 <e>3<7> 队共 <e>" + RavengardMatchMode.TRIOS.maxPlayers() + "<7> 人",
                                "<f> ◦ <7>凑齐 <e>" + RavengardMatchMode.TRIOS.minPlayers()
                                        + "<7> 人（2 队）后 <e>30<7> 秒开始",
                                "<f> ◦ <7>队长可带整个队伍一同匹配")
                        .blankLine()
                        .lore("<e>点击加入三人组队列！"),
                (click, viewContext) -> join(viewContext, RavengardMatchMode.TRIOS));

        backButton(layout);
    }

    private static void join(ViewContext viewContext, RavengardMatchMode mode) {
        if (viewContext.player() instanceof RavengardPlayer player) {
            player.closeInventory();
            RavengardQueue.join(player, mode);
        }
    }
}
