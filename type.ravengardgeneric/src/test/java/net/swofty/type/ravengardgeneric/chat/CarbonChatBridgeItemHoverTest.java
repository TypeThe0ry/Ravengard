package net.swofty.type.ravengardgeneric.chat;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CarbonChatBridgeItemHoverTest {
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();

    @Test
    void aShownItemFromAnotherServerKeepsItsTextButLosesItsHover() {
        String json = "{\"text\":\"[\",\"extra\":[{\"text\":\"钻石剑\",\"hoverEvent\":{\"action\":\"show_item\","
                + "\"id\":\"minecraft:diamond_sword\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"\\\"x\\\"\"}}},"
                + "{\"text\":\"]\"}]}";
        Component received = GSON.deserialize(json);

        Component cleaned = CarbonChatBridge.withoutItemHovers(received);

        assertEquals("[钻石剑]", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(cleaned));
        assertNull(cleaned.children().get(0).hoverEvent());
    }

    @Test
    void textHoversAreKept() {
        Component line = Component.text("hi").hoverEvent(HoverEvent.showText(Component.text("tip")));
        assertNotNull(CarbonChatBridge.withoutItemHovers(line).hoverEvent());
    }
}
