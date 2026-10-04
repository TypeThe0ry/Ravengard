package net.swofty.type.ravengardgeneric.gui;

import net.minestom.server.inventory.click.Click;
import net.swofty.commons.text.Text;
import net.swofty.type.generic.gui.v2.DefaultState;
import net.swofty.type.generic.gui.v2.ViewLayout;
import net.swofty.type.generic.gui.v2.ViewNavigator;
import net.swofty.type.generic.gui.v2.context.ViewContext;
import net.swofty.type.ravengardgeneric.classes.RavengardClass;
import net.swofty.type.ravengardgeneric.profile.RavengardProfile;
import net.swofty.type.ravengardgeneric.profile.RavengardProfiles;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

import java.util.List;
import java.util.UUID;

public class GUIProfiles extends RavengardView {
    private static final int PANEL_ICON = 0xE238;
    /** The five profile columns of the captured menu, statues spanning rows two to four. */
    private static final int[] COLUMN_ORIGINS = {18, 20, 22, 24, 26};

    @Override
    protected String title() {
        return "存档";
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

        List<RavengardProfile> profiles = RavengardProfiles.list(player);
        UUID selected = player.getSelectedProfile();

        for (int column = 0; column < COLUMN_ORIGINS.length; column++) {
            int origin = COLUMN_ORIGINS[column];
            if (column < profiles.size()) {
                placeProfile(layout, origin, profiles.get(column), selected);
            } else {
                placeCreate(layout, origin);
            }
        }

        backButton(layout);
    }

    private void placeProfile(ViewLayout<DefaultState> layout, int origin, RavengardProfile profile,
                              UUID selected) {
        RavengardClass profileClass = profile.getProfileClass();
        RavengardButton statue = profileClass == null
                ? RavengardButton.STATUE_KNIGHT
                : RavengardButton.statueFor(profileClass);
        boolean isSelected = profile.getId().equals(selected);

        RavengardItems.Builder button = RavengardItems.button(statue)
                .label(Text.literal(profileClass == null ? "未使用" : profileClass.getDisplayName()))
                .lore(Text.of("<8>{}", profile.getId()))
                .blankLine()
                .lore(Text.of("<7>职业：<e>{}", profileClass == null ? "无" : profileClass.getDisplayName()),
                        Text.of("<7>游玩时长：<e>{}", profile.playtimeText()),
                        Text.of("<7>王冠：<e>{}", profile.getCrowns()),
                        Text.of("<7>技能点<e>：{}", profile.getAbilityPoints()),
                        Text.of("<7>等级：<e>{}", profile.getLevel()),
                        Text.of("<7>经验：<e>{}/{}", profile.getExperience(), profile.experienceForNextLevel()))
                .blankLine()
                .origin(origin);

        if (isSelected) {
            button.lore("<a>已选择！");
        } else {
            button.lore("<e>左键选择！", "<c>右键删除！");
        }

        interactive(layout, origin, button, (click, viewContext) -> {
                if (!(viewContext.player() instanceof RavengardPlayer target)) {
                    return;
                }
                if (click.click() instanceof Click.Right || click.click() instanceof Click.RightShift) {
                    ViewNavigator.get(target).push(new GUIDeleteProfile(profile.getId()));
                    return;
                }
                if (!isSelected) {
                    target.closeInventory();
                    RavengardProfiles.select(target, profile.getId());
                }
            });
    }

    private void placeCreate(ViewLayout<DefaultState> layout, int origin) {
        RavengardItems.Builder button = RavengardItems.button(RavengardButton.ADD)
                .label("<a>创建存档")
                .blankLine()
                .lore("<e>点击创建！")
                .origin(origin);

        interactive(layout, origin, button, (click, viewContext) -> {
            if (viewContext.player() instanceof RavengardPlayer target) {
                target.closeInventory();
                RavengardProfiles.create(target);
            }
        });
    }
}
