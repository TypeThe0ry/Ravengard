package net.swofty.type.ravengardgeneric.gui;

import net.swofty.commons.text.Text;
import net.swofty.type.generic.gui.v2.DefaultState;
import net.swofty.type.generic.gui.v2.ViewLayout;
import net.swofty.type.generic.gui.v2.context.ViewContext;
import net.swofty.type.ravengardgeneric.classes.RavengardAbility;
import net.swofty.type.ravengardgeneric.classes.RavengardClass;
import net.swofty.type.ravengardgeneric.classes.RavengardKit;
import net.swofty.type.ravengardgeneric.data.RavengardProfileStorage;
import net.swofty.type.ravengardgeneric.profile.RavengardProfile;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

import java.util.List;

/**
 * The per-slot ability page, rebuilt from a 0.2 capture: the class's seven abilities laid out
 * down the panel, equipped ones on their own sprite and locked ones on the shared locked sprite,
 * with the ability points counter beside the back button.
 */
public class GUIAbilityPage extends RavengardView {
    private static final int PANEL_ICON = 0xE23D;
    private static final int[] ABILITY_SLOTS = {4, 11, 15, 22, 29, 33, 40};
    private static final int SLOT_POINTS = 44;

    private final int abilitySlot;

    public GUIAbilityPage(int abilitySlot) {
        this.abilitySlot = abilitySlot;
    }

    @Override
    protected String title() {
        return abilitySlot == 1 ? "第一技能" : "第二技能";
    }

    @Override
    protected int panelIcon() {
        return PANEL_ICON;
    }

    @Override
    protected void content(ViewLayout<DefaultState> layout, DefaultState state, ViewContext ctx) {
        if (!(ctx.player() instanceof RavengardPlayer player)) {
            return;
        }
        RavengardClass playerClass = player.getRavengardClass();
        if (playerClass == null) {
            return;
        }

        List<RavengardAbility> abilities = playerClass.getAbilities();
        for (int index = 0; index < ABILITY_SLOTS.length && index < abilities.size(); index++) {
            RavengardAbility ability = abilities.get(index);
            int equippedSlot = RavengardKit.equippedAbility(player, 0) == ability ? 1
                    : RavengardKit.equippedAbility(player, 1) == ability ? 2 : 0;

            RavengardItems.Builder button = equippedSlot > 0
                    ? RavengardItems.button(ability)
                            .label(Text.of("<a>{}", ability.getDisplayName()))
                            .lore("<a><l>已装备")
                    : RavengardItems.button(RavengardButton.ABILITY_LOCKED)
                            .label(Text.of("<c>{}", ability.getDisplayName()))
                            .lore("<c><l>未解锁");

            button.blankLine()
                    .lore(ability.getWrappedDescription())
                    .blankLine()
                    .lore(Text.of("<7>冷却：<e>{}", ability.getCooldownText()))
                    .blankLine();

            if (equippedSlot > 0) {
                button.lore(Text.of("<c>已装备在<e>第 {} 槽<c>！", equippedSlot));
            } else {
                button.lore("<7>花费：<e>1<b> 技能点")
                        .blankLine()
                        .lore("<c>技能点不足，无法购买该技能！");
            }

            place(layout, ABILITY_SLOTS[index], button);
        }

        RavengardProfile active = RavengardProfileStorage.byId(player.getSelectedProfile());
        int points = active == null ? 0 : active.getAbilityPoints();
        interactive(layout, SLOT_POINTS, RavengardItems.button(RavengardButton.LEVEL)
                .label(Text.of("<b>技能点<7>（<e>{}<7>）", points))
                .lore("<7>在此菜单中花费<b>技能点<7>",
                        "<7>即可解锁新的<a>技能<7>。")
                .blankLine()
                .lore("<7>这些点数来自于",
                        "<7>获得<e>经验<7>并让你的职业",
                        "<7><6>升级<7>。")
                .blankLine()
                .lore(Text.of("<e>经验<7>的获取途径："),
                        Text.of("<7> <f>◦<7> 击败怪物与首领。"),
                        Text.of("<7> <f>◦<7> 击杀其他玩家。"),
                        Text.of("<7> <f>◦<7> 通过传送门撤离。")),
                (click, viewContext) -> click.player().playSound(
                        net.kyori.adventure.sound.Sound.sound()
                                .type(net.kyori.adventure.key.Key.key(
                                        "hypixel_ravengard", "ambience.town.raven"))
                                .volume(1.0f)
                                .pitch(1.0f)
                                .build()));

        backButton(layout);
    }
}
