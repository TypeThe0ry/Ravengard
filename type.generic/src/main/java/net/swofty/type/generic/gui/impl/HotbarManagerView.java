package net.swofty.type.generic.gui.impl;

import net.minestom.server.component.DataComponents;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.swofty.commons.StringUtility;
import net.swofty.type.generic.data.datapoints.DatapointBedWarsHotbar;
import net.swofty.type.generic.data.handlers.BedWarsDataHandler;
import net.swofty.type.generic.gui.inventory.ItemStacks;
import net.swofty.type.generic.gui.v2.Components;
import net.swofty.type.generic.gui.v2.DefaultState;
import net.swofty.type.generic.gui.v2.Layouts;
import net.swofty.type.generic.gui.v2.StatelessView;
import net.swofty.type.generic.gui.v2.ViewConfiguration;
import net.swofty.type.generic.gui.v2.ViewLayout;
import net.swofty.type.generic.gui.v2.context.ViewContext;
import net.swofty.type.generic.user.HypixelPlayer;

import java.util.HashMap;
import java.util.Map;

public class HotbarManagerView extends StatelessView {

    @Override
    public ViewConfiguration<DefaultState> configuration() {
        return ViewConfiguration.withString((_, _) -> "快捷栏管理", InventoryType.CHEST_6_ROW);
    }

    private final int[] SELECTABLE_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16, 17};
    private final int[] DROPPABLE_SLOTS = new int[]{27, 28, 29, 30, 31, 32, 33, 34, 35};

    @Override
    public void layout(ViewLayout<DefaultState> layout, DefaultState state, ViewContext ctx) {
        Components.back(layout, 48, ctx);
        layout.slot(
            50,
            (s, c) -> ItemStacks.item(Material.BARRIER, 1, """
                <c>恢复默认
                <7>将快捷栏恢复为默认布局。"""),
            (clickContext, c) -> {
                BedWarsDataHandler dataHandler = BedWarsDataHandler.getUser(c.player());
                if (dataHandler == null) {
                    c.backOrClose();
                    c.player().sendMessage("<c>重置快捷栏布局时发生错误，请稍后再试。");
                    return;
                }
                dataHandler.get(BedWarsDataHandler.Data.HOTBAR_LAYOUT, DatapointBedWarsHotbar.class).setValue(DatapointBedWarsHotbar.defaultHotbar);
                c.player().getInventory().setCursorItem(ItemStack.AIR);
                c.session(DefaultState.class).refresh();
            }
        );
        layout.slots(
            Layouts.rectangle(18, 26),
            (_, _) -> ItemStacks.item(Material.GRAY_STAINED_GLASS_PANE, 1, """
                <8>⬆ <7>类别
                <8>⬇ <7>快捷栏""")
        );


        for (int slot : SELECTABLE_SLOTS) {
            DatapointBedWarsHotbar.HotbarItemType itemType = DatapointBedWarsHotbar.HotbarItemType.fromOrdinal(slot - 10);
            if (itemType == null) {
                itemType = DatapointBedWarsHotbar.HotbarItemType.BLOCKS;
            }
            DatapointBedWarsHotbar.HotbarItemType finalItemType = itemType;
            layout.slot(
                slot,
                (s, c) -> ItemStacks.item(finalItemType.getMaterial(), 1, """
                    <a>{}
                    <7>将其拖到下方的快捷栏格子，
                    <7>购买该类别的物品或重生时
                    <7>会优先放入该格子。

                    <e>点击拖动！""", finalItemType.pretty()), (clickContext, context) -> {
                    final HypixelPlayer player = context.player();
                    ItemStack cursorItem = player.getInventory().getCursorItem();
                    if (!cursorItem.isAir() && getItemTypeFromCursor(cursorItem) == null) {
                        return;
                    }

                    player.getInventory().setCursorItem(buildCursorItem(finalItemType).build());
                });
        }

        for (int slot : DROPPABLE_SLOTS) {
            final byte hotbarSlot = (byte) (slot - 27);
            layout.slot(slot,
                (_, c) -> {
                    final HypixelPlayer player = c.player();
                    Map<Byte, DatapointBedWarsHotbar.HotbarItemType> currentLayout = getHotbarLayout(player);
                    DatapointBedWarsHotbar.HotbarItemType type = currentLayout.get(hotbarSlot);

                    if (type == null) {
                        return ItemStacks.item(Material.AIR, 1, """
                            <7>空快捷栏格子
                            <7>将类别物品拖到这里，
                            <7>即可优先使用此快捷栏格子。""");
                    }

                    return ItemStacks.item(type.getMaterial(), 1, """
                        <a>{}
                        <7>{}物品将优先放入此格子！

                        <e>点击移除！""", type.pretty(), type.pretty());
                },
                (clickContext, c) -> {
                    final HypixelPlayer player = c.player();
                    Map<Byte, DatapointBedWarsHotbar.HotbarItemType> currentLayout = getHotbarLayout(player);
                    DatapointBedWarsHotbar.HotbarItemType cursorType = getItemTypeFromCursor(player.getInventory().getCursorItem());

                    if (cursorType != null) {
                        currentLayout.put(hotbarSlot, cursorType);
                        setHotbarLayout(player, currentLayout);
                        player.getInventory().setCursorItem(ItemStack.AIR);
                        c.session(DefaultState.class).refresh();
                        return;
                    }

                    DatapointBedWarsHotbar.HotbarItemType existingType = currentLayout.get(hotbarSlot);
                    if (existingType == null) {
                        return;
                    }

                    currentLayout.remove(hotbarSlot);
                    setHotbarLayout(player, currentLayout);
                    c.session(DefaultState.class).refresh();
                }
            );
        }
    }

    private Map<Byte, DatapointBedWarsHotbar.HotbarItemType> getHotbarLayout(HypixelPlayer player) {
        return new HashMap<>(
            BedWarsDataHandler.getUser(player)
                .get(BedWarsDataHandler.Data.HOTBAR_LAYOUT, DatapointBedWarsHotbar.class)
                .getValue()
        );
    }

    private void setHotbarLayout(HypixelPlayer player, Map<Byte, DatapointBedWarsHotbar.HotbarItemType> layout) {
        BedWarsDataHandler.getUser(player)
            .get(BedWarsDataHandler.Data.HOTBAR_LAYOUT, DatapointBedWarsHotbar.class)
            .setValue(new HashMap<>(layout));
    }

    private ItemStack.Builder buildCursorItem(DatapointBedWarsHotbar.HotbarItemType itemType) {
        return ItemStacks.item(itemType.getMaterial(), 1, """
            <a>{}
            <7>将其拖到你希望优先放置{}物品的快捷栏格子！""", itemType.pretty(), itemType.pretty());
    }

    private DatapointBedWarsHotbar.HotbarItemType getItemTypeFromCursor(ItemStack cursorItem) {
        if (cursorItem == null || cursorItem.isAir()) {
            return null;
        }

        var customName = cursorItem.get(DataComponents.CUSTOM_NAME);
        if (customName == null) {
            return null;
        }

        String plainName = StringUtility.stripColor(StringUtility.getTextFromComponent(customName));
        for (DatapointBedWarsHotbar.HotbarItemType itemType : DatapointBedWarsHotbar.HotbarItemType.values()) {
            if (itemType.pretty().equalsIgnoreCase(plainName)) {
                return itemType;
            }
        }

        return null;
    }

}
