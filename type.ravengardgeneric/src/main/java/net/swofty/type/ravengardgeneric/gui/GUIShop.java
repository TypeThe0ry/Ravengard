package net.swofty.type.ravengardgeneric.gui;

import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.swofty.commons.text.Text;
import net.swofty.type.generic.gui.inventory.ItemStacks;
import net.swofty.type.generic.gui.v2.DefaultState;
import net.swofty.type.generic.gui.v2.ViewLayout;
import net.swofty.type.generic.gui.v2.ViewNavigator;
import net.swofty.type.generic.gui.v2.context.ViewContext;
import net.swofty.type.ravengardgeneric.item.RavengardItem;
import net.swofty.type.ravengardgeneric.item.RavengardItemRegistry;
import net.swofty.type.ravengardgeneric.item.RavengardItemType;
import net.swofty.type.ravengardgeneric.profile.RavengardProfiles;
import net.swofty.type.ravengardgeneric.shop.RavengardShop;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The buy side of a shop, rebuilt from captures: banner top left, stock in the grid, the stock
 * refresh hourglass, and the buy and sell tabs down the left edge.
 */
public class GUIShop extends RavengardView {
    private static final int PANEL_ICON = 0xE23B;
    static final int SLOT_BANNER = 0;
    static final int SLOT_HOURGLASS = 19;
    static final int SLOT_BUY = 27;
    static final int SLOT_SELL = 36;
    private final RavengardShop shop;

    public GUIShop(RavengardShop shop) {
        this.shop = shop;
    }

    @Override
    protected String title() {
        return shop.title();
    }

    @Override
    protected int panelIcon() {
        return PANEL_ICON;
    }

    @Override
    protected void content(ViewLayout<DefaultState> layout, DefaultState state, ViewContext ctx) {
        place(layout, SLOT_BANNER, RavengardItems.button(banner(shop))
                .label(Text.literal(shop.title()))
                .lore("<7>买卖物品，助你",
                        "<7>一路冒险！"));

        for (int slot : shop.shelfSlots()) {
            placeStock(layout, slot);
        }

        layout.autoUpdating(SLOT_HOURGLASS,
                (state2, ctx2) -> RavengardItems.button(RavengardButton.HOURGLASS)
                        .label("库存刷新")
                        .lore("<7>此商店的库存将在以下时间后刷新：")
                        .lore(Text.of("<e>{}", net.swofty.type.ravengardgeneric.shop.RavengardShopStock.refreshText()))
                        .origin(SLOT_HOURGLASS)
                        .toBuilder(),
                java.time.Duration.ofSeconds(1));

        place(layout, SLOT_BUY, RavengardItems.button(RavengardButton.BUY)
                .label("购买")
                .lore("<7>从此商店购买物品，",
                        "<7>助你一路冒险！")
                .blankLine()
                .lore("<a>你在这里！"));

        interactive(layout, SLOT_SELL, RavengardItems.button(RavengardButton.SELL)
                        .label("出售")
                        .lore("<7>出售背包里的物品，",
                                "<7>赚点外快！")
                        .blankLine()
                        .lore("<e>点击出售！"),
                (click, viewContext) -> ViewNavigator.get(viewContext.player()).push(new GUIShopSell(shop)));
    }

    private void placeStock(ViewLayout<DefaultState> layout, int slot) {
        layout.autoUpdating(slot,
                (state2, ctx2) -> renderStock(slot, ctx2),
                (click, viewContext) -> buy(slot, viewContext),
                java.time.Duration.ofSeconds(1));
    }

    private ItemStack.Builder renderStock(int slot, ViewContext ctx) {
        var entry = net.swofty.type.ravengardgeneric.shop.RavengardShopStock.shelf(shop).at(slot);
        RavengardItemType type = entry == null ? null : RavengardItemRegistry.get(entry.item());
        if (type == null) {
            return ItemStack.builder(net.minestom.server.item.Material.AIR);
        }

        boolean wrongClass = ctx.player() instanceof RavengardPlayer viewer
                && !type.usableBy(viewer.getRavengardClass());

        ItemStack.Builder display = RavengardItem.displayBuilder(type);
        List<Text> lore = new ArrayList<>(RavengardItem.loreOf(type, true));
        lore.add(Text.empty());
        if (!entry.inStock()) {
            display.set(DataComponents.ITEM_MODEL, type.getItemModel() + "_greyed");
            lore.add(Text.of("<c>缺货！"));
        } else if (wrongClass) {
            display.set(DataComponents.ITEM_MODEL, type.getItemModel() + "_greyed");
            lore.add(Text.of("<c>你的职业无法使用该物品！"));
        } else if (entry.price() == 0) {
            lore.add(Text.of("<e>点击免费领取！"));
        } else {
            lore.add(Text.of("<e>Click to buy for <f>\uD83D\uDC51<#FFCE47>{}<e>!", entry.price()));
        }
        ItemStacks.lines(display, lore);
        return display;
    }

    private void buy(int slot, ViewContext viewContext) {
        if (!(viewContext.player() instanceof RavengardPlayer player)) {
            return;
        }
        var entry = net.swofty.type.ravengardgeneric.shop.RavengardShopStock.shelf(shop).at(slot);
        RavengardItemType type = entry == null ? null : RavengardItemRegistry.get(entry.item());
        if (type == null) {
            return;
        }
        if (!entry.inStock()) {
            player.sendMessage("<c>该物品已缺货！");
            return;
        }
        if (!type.usableBy(player.getRavengardClass())) {
            player.sendMessage("<c>你的职业无法使用该物品！");
            return;
        }
        if (!RavengardItems.hasFreeSlot(player)) {
            player.sendMessage("<c>你的背包已满！");
            return;
        }
        if (!RavengardProfiles.tryPurchase(player, entry.price())) {
            player.sendMessage("<c>你的王冠不足！");
            return;
        }
        if (!entry.take()) {
            player.setCrowns(player.getCrowns() + entry.price());
            player.sendMessage("<c>该物品已缺货！");
            return;
        }
        player.getInventory().addItemStack(RavengardItem.of(type, player, entry.boost()));
        RavengardProfiles.saveActive(player);
        player.sendMessage(Text.of("<a>你花费 {} 购买了 <f>{}<a>！",
                RavengardItem.crowns(entry.price(), ""), RavengardItem.displayName(type)));
    }

    static RavengardButton banner(RavengardShop shop) {
        return switch (shop.banner()) {
            case "armorer" -> RavengardButton.BANNER_ARMORER;
            case "alchemist" -> RavengardButton.BANNER_ALCHEMIST;
            case "free_gear" -> RavengardButton.BANNER_FREE_GEAR;
            default -> RavengardButton.BANNER_BLACKSMITH;
        };
    }
}
