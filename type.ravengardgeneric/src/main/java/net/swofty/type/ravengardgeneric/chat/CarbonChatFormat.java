package net.swofty.type.ravengardgeneric.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * CarbonChat sends the finished line across servers and shows it as received, so Ravengard's
 * lines are built in the network's global channel style: [server] name › message.
 */
public final class CarbonChatFormat {
    private static final TextColor FRAME = TextColor.color(0x68766E);
    private static final TextColor TEXT = TextColor.color(0xE8EEE9);
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
}
