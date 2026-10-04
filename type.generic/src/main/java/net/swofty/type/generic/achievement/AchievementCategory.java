package net.swofty.type.generic.achievement;

import lombok.Getter;
import net.minestom.server.item.Material;
import net.swofty.type.generic.gui.inventory.item.GUIMaterial;

@Getter
public enum AchievementCategory {
    BEDWARS("起床战争", "bedwars", new GUIMaterial(Material.RED_BED)),
    VAMPIREZ("吸血鬼", "vampirez", new GUIMaterial(Material.WITHER_SKELETON_SKULL)),
    SKYWARS("空岛战争", "skywars", new GUIMaterial(Material.ENDER_EYE)),
    BLITZ_SG("闪电饥饿游戏", "blitzsg", new GUIMaterial(Material.DIAMOND_SWORD)),
    THE_WALLS("战墙", "walls", new GUIMaterial(Material.SAND)),
    PAINTBALL("彩弹射击", "paintball", new GUIMaterial(Material.SNOWBALL)),
    ARCADE("街机游戏", "arcade", new GUIMaterial(Material.SLIME_BALL)),
    QUAKECRAFT("未来射击", "quakecraft", new GUIMaterial(Material.FIREWORK_ROCKET)),
    MEGA_WALLS("超级战墙", "megawalls", new GUIMaterial(Material.SOUL_SAND)),
    TNT_GAMES("TNT 游戏", "tntgames", new GUIMaterial(Material.TNT)),
    ARENA_BRAWL("竞技场乱斗", "arena", new GUIMaterial(Material.BLAZE_POWDER)),
    UHC_CHAMPIONS("极限生存冠军", "uhc", new GUIMaterial(Material.GOLDEN_APPLE)),
    WARLORDS("战争领主", "warlords", new GUIMaterial(Material.STONE_AXE)),
    TURBO_KART_RACERS("方块赛车", "gingerbread", new GUIMaterial(Material.MINECART)),
    SMASH_HEROES("英雄乱斗", "supersmash", new GUIMaterial("d29a9f57267ed342a13e3ad3a240c4c5af5a1a36ab2de0d6c2a31af0e3cdde")),
    SPEED_UHC("速战极限生存", "speeduhc", new GUIMaterial(Material.GOLDEN_CARROT)),
    MURDER_MYSTERY("密室杀手", "murdermystery", new GUIMaterial(Material.BOW)),
    BUILD_BATTLE("建筑大师", "buildbattle", new GUIMaterial(Material.CRAFTING_TABLE)),
    DUELS("决斗", "duels", new GUIMaterial(Material.FISHING_ROD)),
    PIT("天坑乱斗", "pit", new GUIMaterial(Material.DIRT)),
    WOOL_GAMES("羊毛战争", "woolgames", new GUIMaterial(Material.WHITE_WOOL)),
    COPS_AND_CRIMS("警匪大战", "copsandcrims", new GUIMaterial(Material.IRON_BARS)),
    GENERAL("综合", "general", new GUIMaterial(Material.NETHER_STAR)),
    SKYBLOCK("空岛生存", "skyblock", new GUIMaterial("d7cc6687423d0570d556ac53e0676cb563bbdd9717cd8269bdebed6f6d4e7bf8")),
    HOUSING("家园", "housing", new GUIMaterial(Material.DARK_OAK_DOOR)),
    ;

    private final String displayName;
    private final String configKey;
    private final GUIMaterial material;

    AchievementCategory(String displayName, String configKey, GUIMaterial material) {
        this.displayName = displayName;
        this.configKey = configKey;
        this.material = material;
    }

    public static AchievementCategory fromConfigKey(String key) {
        for (AchievementCategory category : values()) {
            if (category.configKey.equalsIgnoreCase(key)) {
                return category;
            }
        }
        return null;
    }
}
