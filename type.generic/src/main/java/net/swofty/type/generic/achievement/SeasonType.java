package net.swofty.type.generic.achievement;

import lombok.Getter;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.swofty.commons.text.Text;

@Getter
public enum SeasonType {
    HOLIDAY("节日", NamedTextColor.RED, "圣诞/冬季活动"),
    EASTER("复活节", NamedTextColor.AQUA, "复活节/春季活动"),
    SUMMER("夏日", NamedTextColor.YELLOW, "夏日活动"),
    HALLOWEEN("万圣节", NamedTextColor.GOLD, "万圣节活动");

    private final String displayName;
    private final TextColor color;
    private final String description;

    SeasonType(String displayName, TextColor color, String description) {
        this.displayName = displayName;
        this.color = color;
        this.description = description;
    }

    public static SeasonType fromString(String name) {
        for (SeasonType season : values()) {
            if (season.name().equalsIgnoreCase(name)) {
                return season;
            }
        }
        return null;
    }

    public Text getFormattedName() {
        return Text.of("<color:{}>{}成就", color, displayName);
    }
}
