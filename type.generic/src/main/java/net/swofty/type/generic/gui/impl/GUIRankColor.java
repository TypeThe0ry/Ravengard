package net.swofty.type.generic.gui.impl;

import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.swofty.type.generic.gui.inventory.ItemStacks;
import net.swofty.type.generic.gui.v2.Components;
import net.swofty.type.generic.gui.v2.DefaultState;
import net.swofty.type.generic.gui.v2.StatelessView;
import net.swofty.type.generic.gui.v2.ViewConfiguration;
import net.swofty.type.generic.gui.v2.ViewLayout;
import net.swofty.type.generic.gui.v2.context.ViewContext;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.generic.user.categories.Rank;
import net.swofty.type.generic.user.categories.RankColor;

public class GUIRankColor extends StatelessView {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    @Override
    public ViewConfiguration<DefaultState> configuration() {
        return new ViewConfiguration<>("会员等级颜色", InventoryType.CHEST_5_ROW);
    }

    @Override
    public void layout(ViewLayout<DefaultState> layout, DefaultState state, ViewContext ctx) {
        Components.close(layout, 40);
        RankColor[] colors = RankColor.values();
        for (int i = 0; i < colors.length; i++) addColor(layout, SLOTS[i], colors[i]);

        if (ctx.player().getRank() == Rank.MVP_PLUS_PLUS) {
            layout.slot(44, (_, c) -> {
                boolean aqua = c.player().isMvpPlusPlusAqua();
                return ItemStacks.item(Material.NETHER_STAR, 1, aqua
                    ? """
                        <a>切换前缀颜色
                        <7>已选择：<b>天蓝色

                        <7>点击将颜色改为<6>金色"""
                    : """
                        <a>切换前缀颜色
                        <7>已选择：<6>金色

                        <7>点击将颜色改为<b>天蓝色""");
            }, (_, c) -> {
                var data = c.player().getDataHandler().get(net.swofty.type.generic.data.HypixelDataHandler.Data.MVP_PLUS_PLUS_AQUA,
                    net.swofty.type.generic.data.datapoints.DatapointBoolean.class);
                data.setValue(!data.getValue());
                c.session().refresh();
            });
        }
    }

    private void addColor(ViewLayout<DefaultState> layout, int slot, RankColor color) {
        layout.slot(slot, (_, ctx) -> render(ctx.player(), color), (_, ctx) -> {
            HypixelPlayer player = ctx.player();
            if (!player.getRank().isEqualOrHigherThan(Rank.MVP_PLUS)) {
                player.sendMessage("<c>你必须拥有 MVP+ 或更高的会员等级才能使用会员等级颜色！");
                return;
            }
            if (!color.isUnlocked(player)) {
                if (color == RankColor.DARK_BLUE) {
                    player.sendMessage("<c>你必须赠送 100 个会员等级才能解锁此会员等级颜色！");
                } else {
                    player.sendMessage("<c>你的 Hypixel 等级必须达到 {} 才能解锁此会员等级颜色！", color.getRequiredLevel());
                }
                return;
            }
            player.setRankColor(color);
            ctx.session().refresh();
        });
    }

    private ItemStack.Builder render(HypixelPlayer player, RankColor color) {
        boolean hasRank = player.getRank().isEqualOrHigherThan(Rank.MVP_PLUS);
        boolean unlocked = hasRank && color.isUnlocked(player);
        boolean selected = player.getRankColor() == color;
        String code = legacyCode(color.getColor());
        String ending = selected ? "<a>当前已选择！"
                : unlocked ? "<e>点击选择！"
                : color == RankColor.DARK_BLUE ? "<6>领取“赠送 100 个会员等级”奖励后解锁！"
                : "<3>Hypixel 等级达到 {} 时解锁";

        return ItemStacks.item(unlocked ? color.getMaterial() : Material.GRAY_DYE, 1,
                (unlocked ? "<a>" : "<c>") + "会员等级颜色：{}\n"
                        + "<7>将 <b>MVP<c>+ <7>中加号的颜色\n"
                        + "<7>改为{}，使其变为 <b>MVP" + code + "+\n"
                        + "\n"
                        + "<7>会显示在 Tab 列表中，聊天和\n"
                        + "<7>进入大厅时也会显示。\n"
                        + "\n"
                        + ending,
                color.getDisplayName(), color.getDisplayName().toLowerCase(), color.getRequiredLevel());
    }

    private String legacyCode(NamedTextColor color) {
        return "<" + Integer.toHexString(color.value() == 0x000000 ? 0 : switch (color.toString()) {
            case "dark_blue" -> 1;
            case "dark_green" -> 2;
            case "dark_aqua" -> 3;
            case "dark_red" -> 4;
            case "dark_purple" -> 5;
            case "gold" -> 6;
            case "gray" -> 7;
            case "dark_gray" -> 8;
            case "blue" -> 9;
            case "green" -> 10;
            case "aqua" -> 11;
            case "red" -> 12;
            case "light_purple" -> 13;
            case "yellow" -> 14;
            default -> 15;
        }) + ">";
    }
}
