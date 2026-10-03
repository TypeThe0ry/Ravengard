package net.swofty.type.ravengardgeneric.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;

/**
 * CarbonChat sends the finished line across servers and shows it as received, so Ravengard's
 * lines are built in the network's global channel style: [server] name › message.
 */
public final class CarbonChatFormat {
    private static final TextColor FRAME = TextColor.color(0x68766E);
    private static final TextColor TEXT = TextColor.color(0xE8EEE9);
    private static final TextColor WHISPER = TextColor.color(0xD9BC7C);
    private static final String LABEL = System.getenv().getOrDefault("CARBON_SERVER_LABEL", "天穹余烬");
    private static final TextColor LABEL_COLOR = TextColor.fromHexString(
            System.getenv().getOrDefault("CARBON_SERVER_LABEL_COLOR", "#E07A3F"));

    private CarbonChatFormat() {
    }

    public static Component line(String username, String message) {
        return Component.text()
                .append(Component.text("[", FRAME))
                .append(Component.text(LABEL, LABEL_COLOR == null ? TEXT : LABEL_COLOR))
                .append(Component.text("]", FRAME))
                .append(Component.text(" "))
                .append(Component.text(username, TEXT))
                .append(Component.text(" "))
                .append(Component.text("›", FRAME))
                .append(Component.text(" "))
                .append(Component.text(message, TEXT))
                .build();
    }

    public static Component whisperTo(String recipient, Component message) {
        return whisper(Component.text("你", WHISPER), Component.text(recipient, TEXT), message)
                .clickEvent(ClickEvent.suggestCommand("/whisper " + recipient + " "))
                .hoverEvent(HoverEvent.showText(Component.text("点击继续私信")));
    }

    public static Component whisperFrom(String sender, Component message) {
        return whisper(Component.text(sender, TEXT), Component.text("你", WHISPER), message)
                .clickEvent(ClickEvent.suggestCommand("/whisper " + sender + " "))
                .hoverEvent(HoverEvent.showText(Component.text("点击回复")));
    }

    private static Component whisper(Component from, Component to, Component message) {
        return Component.text()
                .append(Component.text("[", FRAME))
                .append(Component.text("私信", WHISPER))
                .append(Component.text("]", FRAME))
                .append(Component.text(" "))
                .append(from)
                .append(Component.text(" "))
                .append(Component.text("→", FRAME))
                .append(Component.text(" "))
                .append(to)
                .append(Component.text(" "))
                .append(Component.text("›", FRAME))
                .append(Component.text(" "))
                .append(message.colorIfAbsent(TEXT))
                .build();
    }
}
