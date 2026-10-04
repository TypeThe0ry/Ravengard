package net.swofty.type.generic.collectibles;

public enum CollectibleGamemode {
    BEDWARS("起床战争"),
    SKYWARS("空岛战争"),
    MURDER_MYSTERY("密室杀手"),
    PROTOTYPE("原型测试");

    private final String displayName;

    CollectibleGamemode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
