package net.swofty.type.ravengardgeneric.entity;

import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.instance.Instance;
import net.swofty.commons.text.TextParser;
import net.swofty.type.generic.entity.InteractionEntity;
import net.swofty.type.generic.world.HypixelWorldLoader;

/**
 * The playtest notice board on the Nevermore's deck, captured from the live server: a fixed text
 * display with a wide, flat interaction volume in front of it. The live board offers a "CLICK
 * HERE" line whose click response has not been captured yet, so clicking does nothing here.
 */
public final class NevermoreNoticeBoard {
    private static final Pos POSITION = new Pos(33.0, 61.5, 74.05);
    private static final int LINE_WIDTH = 240;
    private static final float CLICK_WIDTH = 3.0f;
    private static final float CLICK_HEIGHT = 0.5f;

    private NevermoreNoticeBoard() {
    }

    public static void spawn(Instance instance) {
        LivingEntity display = new LivingEntity(EntityType.TEXT_DISPLAY);
        display.editEntityMeta(TextDisplayMeta.class, meta -> {
            meta.setText(text());
            meta.setLineWidth(LINE_WIDTH);
            meta.setBackgroundColor(0);
            meta.setShadow(true);
        });
        spawn(display, instance);

        spawn(new InteractionEntity(CLICK_WIDTH, CLICK_HEIGHT, (player, event) -> {
        }), instance);
    }

    private static void spawn(Entity entity, Instance instance) {
        entity.setNoGravity(true);
        entity.setHasPhysics(false);
        entity.updateViewableRule(HypixelWorldLoader.LOADED_ONLY);
        entity.setInstance(instance, POSITION);
    }

    private static Component text() {
        return TextParser.parse("<6>欢迎来到<l>天穹余烬</l>！\n"
                + "<8><o>仍在开发中，可能会出现问题。</o>\n\n"
                + "<6><l>> </l><a>PvPvE <7>撤离玩法，<2>生存<7>就是一切！\n"
                + "<6><l>> </l><7>进入<c>地牢<7>，带着<6>战利品<3>撤离<7>。\n"
                + "<6><l>> </l><7>躲开<5>死亡之墙<7>，它会向随机地点收缩。\n"
                + "<6><l>> </l><7>如果你<c>死亡<7>，将失去身上携带的<4>一切<7>。\n"
                + "<6><l>> </l><7>把物品存进你的<d>保险箱<7>。\n"
                + "<6><l>> </l><c>击败<7>敌人以<6>升级<7>并解锁技能！\n\n"
                + "<7>测试结束日期：<6>8 月 31 日\n"
                + "<7>玩得开心，祝你好运！\n\n"
                + "<e><l><n>点击这里</n></l>了解更多！\n");
    }
}
