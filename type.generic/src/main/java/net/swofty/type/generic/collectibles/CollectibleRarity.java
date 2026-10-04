package net.swofty.type.generic.collectibles;

import lombok.Getter;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.swofty.commons.text.Text;

import java.util.Locale;

@Getter
public enum CollectibleRarity {
    COMMON("普通", NamedTextColor.GREEN, 1),
    RARE("稀有", NamedTextColor.BLUE, 2),
    EPIC("史诗", NamedTextColor.DARK_PURPLE, 3),
    LEGENDARY("传说", NamedTextColor.GOLD, 4),
    MYTHIC("神话", NamedTextColor.LIGHT_PURPLE, 5);

    private final String displayName;
    private final TextColor color;
    private final int weight;

    CollectibleRarity(String displayName, TextColor color, int weight) {
        this.displayName = displayName;
        this.color = color;
        this.weight = weight;
    }

    public Text formattedName() {
        return Text.of("<color:{}>{}", color, displayName);
    }

    public static CollectibleRarity fromString(String value, CollectibleRarity fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return CollectibleRarity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
