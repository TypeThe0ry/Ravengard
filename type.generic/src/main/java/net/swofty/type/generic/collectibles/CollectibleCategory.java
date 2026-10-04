package net.swofty.type.generic.collectibles;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Getter
public enum CollectibleCategory {
    PROJECTILE_TRAILS("弹射物轨迹"),
    VICTORY_DANCES("胜利之舞"),
    FINAL_KILL_EFFECTS("最终击杀特效"),
    SPRAYS("喷漆"),
    ISLAND_TOPPERS("岛屿装饰"),
    DEATH_CRIES("死亡哀嚎"),
    SHOPKEEPER_SKINS("商人皮肤"),
    KILL_MESSAGES("击杀消息"),
    GLYPHS("符文"),
    BED_DESTROYS("破坏床特效"),
    WOOD_SKINS("木材皮肤"),
    FIGURINES("手办"),
    PRESTIGE_SCHEMES("声望配色"),
    PRESTIGE_STARS("声望星标"),
    PRESTIGE_BRACKETS("声望括号");

    private static final EnumSet<CollectibleCategory> BEDWARS_CATEGORIES = EnumSet.of(
        PROJECTILE_TRAILS,
        VICTORY_DANCES,
        FINAL_KILL_EFFECTS,
        SPRAYS,
        ISLAND_TOPPERS,
        DEATH_CRIES,
        SHOPKEEPER_SKINS,
        KILL_MESSAGES,
        GLYPHS,
        BED_DESTROYS,
        WOOD_SKINS,
        FIGURINES,
        PRESTIGE_SCHEMES,
        PRESTIGE_STARS,
        PRESTIGE_BRACKETS
    );

    private final String displayName;

    CollectibleCategory(String displayName) {
        this.displayName = displayName;
    }

    public static Optional<CollectibleCategory> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(CollectibleCategory.valueOf(key.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static Set<CollectibleCategory> bedWarsCategories() {
        return EnumSet.copyOf(BEDWARS_CATEGORIES);
    }
}
