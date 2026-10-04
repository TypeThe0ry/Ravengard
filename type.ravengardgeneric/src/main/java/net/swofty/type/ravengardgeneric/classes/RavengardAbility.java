package net.swofty.type.ravengardgeneric.classes;

import lombok.Getter;
import net.swofty.commons.text.Text;
import net.swofty.type.ravengardgeneric.gui.RavengardSprite;

import java.util.ArrayList;
import java.util.List;

@Getter
public enum RavengardAbility implements RavengardSprite {
    // cooldowns marked (capture) come from the live tooltips, which disagree with the wiki
    SENTINEL(RavengardClass.KNIGHT, "哨卫", '\uF106', 100,   // capture; wiki says 1m 28s
            "在 <e>10 秒</e>内提升 <a>30%</a> 物理防御。"),
    PROVOKE(RavengardClass.KNIGHT, "挑衅", '\uF112', 26,
            "嘲讽 <a>7 格</a>范围内的敌人，使其以你为目标。"),
    SHIELD_BASH(RavengardClass.KNIGHT, "盾击", '\uF117', 26,
            "击退 <a>5 格</a>范围内的所有敌人。"),
    RALLY(RavengardClass.KNIGHT, "集结", '\uF10B', 79,
            "暂时提升 <a>6 格</a>范围内\n所有队友 <a>20%</a> 的伤害。"),
    WOUND(RavengardClass.KNIGHT, "创伤", '\uF11B', 17,
            "造成流血效果，<e>5 秒</e>内\n每秒造成 <c>10 HP</c> 伤害。"),
    RECOVERY(RavengardClass.KNIGHT, "恢复", '\uF115', 120,   // capture; wiki says 1m 45s
            "立即恢复 <c>+30 HP</c>。"),
    DIRECT_HIT(RavengardClass.KNIGHT, "直击", '\uF103', 35,
            "<e>5 秒</e>内攻击伤害提高 <a>20%</a>。"),

    WAR_CRY(RavengardClass.WARRIOR, "战吼", '\uF11A', 40,
            "下一次攻击的伤害提高 <a>20%</a>。"),
    ARMOR_BREAK(RavengardClass.WARRIOR, "破甲", '\uF100', 50,
            "<e>5 秒</e>内降低敌人 <a>10%</a> 防御。"),
    BOLSTER(RavengardClass.WARRIOR, "坚守", '\uF102', 60,
            "<e>10 秒</e>内受到的伤害降低 <a>20%</a>。"),
    COOL_OFF(RavengardClass.WARRIOR, "冷静", '\uF101', 80,
            "在 <e>10 秒</e>内恢复 <c>+35 HP</c>。"),
    RUSH(RavengardClass.WARRIOR, "冲锋", '\uF116', 20,
            "<e>5 秒</e>内不消耗体力。"),
    OVERPOWER(RavengardClass.WARRIOR, "压制", '\uF10E', 10,
            "击退 <a>5 格</a>范围内的所有怪物。"),
    CARNAGE(RavengardClass.WARRIOR, "屠戮", '\uF113', 40,
            "<e>5 秒</e>内提高击退力度。"),

    EAGLE_EYE(RavengardClass.HUNTER, "鹰眼", '\uF105', 40,
            "<e>10 秒</e>内箭矢伤害提高 <a>20%</a>。"),
    RAPID_FIRE(RavengardClass.HUNTER, "速射", '\uF114', 40,
            "接下来的 3 支箭瞬间射出。"),
    POISON_ARROW(RavengardClass.HUNTER, "毒箭", '\uF231', 30,
            "下一支箭会使目标中毒 <e>6 秒</e>。"),
    IMMOBILIZE(RavengardClass.HUNTER, "定身", '\uF109', 30,
            "下一支箭会使目标减速 <e>2 秒</e>。"),
    MED_KIT(RavengardClass.HUNTER, "医疗包", '\uF10D', 120,
            "恢复 <c>+30 HP</c>。"),
    DOUBLE_SHOT(RavengardClass.HUNTER, "双重射击", '\uF104', 40,
            "同时射出两支箭。"),
    POWER_SHOT(RavengardClass.HUNTER, "强力射击", '\uF110', 40,
            "下一支箭瞬间射出。"),

    PRECISE_ACCURACY(RavengardClass.ASSASSIN, "精准", '\uF111', 40,
            "<e>10 秒</e>内攻击伤害提高 <a>30%</a>。"),
    SHADOW_STEP(RavengardClass.ASSASSIN, "影步", '\uF118', 25,
            "向前冲刺 <a>6 格</a>。"),
    HEAL_WOUNDS(RavengardClass.ASSASSIN, "疗伤", '\uF108', 120,
            "在 <e>10 秒</e>内恢复 <c>+35 HP</c>。"),
    LACERATE(RavengardClass.ASSASSIN, "撕裂", '\uF10C', 60,
            "下一次攻击造成额外 <a>80%</a> 伤害。"),
    SHADOWS(RavengardClass.ASSASSIN, "暗影", '\uF10A', 100,
            "隐身 <e>25 秒</e>，但移动速度降低。",
            "攻击或受到攻击会解除隐身。"),
    SWIFT(RavengardClass.ASSASSIN, "迅捷", '\uF119', 180,
            "移动速度提高 <a>50%</a> 且不消耗体力。"),
    POISON_DAGGER(RavengardClass.ASSASSIN, "毒匕", '\uF10F', 30,
            "每 <e>6 秒</e>对目标造成 <a>7</a> 点伤害。"),

    LIGHTNING_STRIKE(RavengardClass.SORCERER, "雷击", '\uF240', 40,
            "向玩家射出一道电击，命中时\n对目标造成 <a>40</a> 点伤害并波及周围。"),
    FIREBALL(RavengardClass.SORCERER, "火球", '\uF241', 25,
            "发射一枚小型火球，\n命中时造成 <a>60</a> 点伤害。"),
    ICE_SHARDS(RavengardClass.SORCERER, "冰刺", '\uF242', 40,
            "持续射出冰流，每次命中\n造成 <a>10</a> 点伤害并击退。"),
    RUMBLE(RavengardClass.SORCERER, "地裂", '\uF243', 30,
            "撕裂周围的大地，对范围内\n所有敌人造成 <a>20</a> 点伤害。"),
    SPRING_HEALING(RavengardClass.SORCERER, "泉愈", '\uF244', 40,
            "在 <e>10 秒</e>内恢复 <c>+45 HP</c>。"),
    WIND_CUTTER(RavengardClass.SORCERER, "风刃", '\uF245', 60,
            "释放 3 道强力风刃，\n造成 <a>50</a> 点伤害并击退敌人。"),
    ACCELERATE(RavengardClass.SORCERER, "加速", '\uF23F', 40,
            "暂时将<d>魔法物品</d>的冷却缩短 <a>50%</a>！");

    private final RavengardClass owner;
    private final String displayName;
    private final char icon;
    private final int cooldownSeconds;
    private final String[] description;

    RavengardAbility(RavengardClass owner, String displayName, char icon, int cooldownSeconds,
                     String... description) {
        this.owner = owner;
        this.displayName = displayName;
        this.icon = icon;
        this.cooldownSeconds = cooldownSeconds;
        this.description = description;
    }

    private static final String MODEL_ROOT = "hypixel_ravengard:ui/menu/button/";

    // every ability button shares one sprite size, so one base pair covers all of them
    private static final int HOVER_BASE_X = 39;
    private static final int HOVER_BASE_Y = 25;

    @Override
    public String itemModel() {
        return MODEL_ROOT + name().toLowerCase();
    }

    @Override
    public int iconCodePoint() {
        return icon;
    }

    @Override
    public int hoverBaseX() {
        return HOVER_BASE_X;
    }

    @Override
    public int hoverBaseY() {
        return HOVER_BASE_Y;
    }

    @Override
    public int slotWidth() {
        return 1;
    }

    @Override
    public int slotHeight() {
        return 1;
    }

    /**
     * The live tooltips highlight the numbers in a description rather than printing it flat:
     * percentages green, HP amounts red, durations yellow. The descriptions carry that markup.
     */
    public List<Text> getHighlightedDescription() {
        List<Text> lines = new ArrayList<>(description.length);
        for (String line : description) {
            for (String part : line.split("\n")) {
                lines.add(Text.of("<7>" + part));
            }
        }
        return lines;
    }

    /** Glyph of this ability's ui/spell texture, drawn in the bottom hud's two spell slots. */
    public int getHudCodePoint() {
        return switch (this) {
            case SENTINEL -> 0xE034; case PROVOKE -> 0xE032; case SHIELD_BASH -> 0xE035;
            case RALLY -> 0xE031; case WOUND -> 0xE036; case RECOVERY -> 0xE033;
            case DIRECT_HIT -> 0xE030;
            case WAR_CRY -> 0xE04D; case ARMOR_BREAK -> 0xE037; case BOLSTER -> 0xE038;
            case COOL_OFF -> 0xE039; case RUSH -> 0xE04C; case OVERPOWER -> 0xE04A;
            case CARNAGE -> 0xE04B;
            case EAGLE_EYE -> 0xE02A; case RAPID_FIRE -> 0xE02F; case POISON_ARROW -> 0xE050;
            case IMMOBILIZE -> 0xE02C; case MED_KIT -> 0xE02D; case DOUBLE_SHOT -> 0xE029;
            case POWER_SHOT -> 0xE02E;
            case PRECISE_ACCURACY -> 0xE025; case SHADOW_STEP -> 0xE026; case HEAL_WOUNDS -> 0xE021;
            case LACERATE -> 0xE023; case SHADOWS -> 0xE022; case SWIFT -> 0xE027;
            case POISON_DAGGER -> 0xE024;
            case LIGHTNING_STRIKE -> 0xF247; case FIREBALL -> 0xF248; case ICE_SHARDS -> 0xF249;
            case RUMBLE -> 0xF24A; case SPRING_HEALING -> 0xF24B; case WIND_CUTTER -> 0xF24C;
            case ACCELERATE -> 0xF246;
        };
    }

    /**
     * The highlighted description wrapped the way the captured tooltips wrap, roughly
     * thirty-six visible characters to a line, each continuation reopening in gray.
     */
    public List<Text> getWrappedDescription() {
        Text joined = Text.join(Text.literal("\n"), getHighlightedDescription());
        return Text.of("<wrap:36>{}</wrap>", joined).lines();
    }

    public int getCooldownTicks() {
        return cooldownSeconds * 20;
    }

    public String getCooldownText() {
        int minutes = cooldownSeconds / 60;
        int seconds = cooldownSeconds % 60;
        if (minutes == 0) {
            return seconds + " 秒";
        }
        return seconds == 0 ? minutes + " 分钟" : minutes + " 分 " + seconds + " 秒";
    }

    public static RavengardAbility fromKey(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        for (RavengardAbility value : values()) {
            if (value.name().equalsIgnoreCase(key)) {
                return value;
            }
        }
        return null;
    }
}
