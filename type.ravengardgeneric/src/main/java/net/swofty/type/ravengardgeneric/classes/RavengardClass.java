package net.swofty.type.ravengardgeneric.classes;

import lombok.Getter;
import net.swofty.commons.text.Text;

import java.util.Arrays;
import java.util.List;

@Getter
public enum RavengardClass {
    KNIGHT("骑士", '\uE226'),
    WARRIOR("战士", '\uE228'),
    HUNTER("猎人", '\uE227'),
    ASSASSIN("刺客", '\uE225'),
    SORCERER("术士", '\uE229');

    private final String displayName;
    private final char icon;

    RavengardClass(String displayName, char icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    /** The class's small tag glyph from the pack's font/tags textures, used on item tooltips. */
    public int getTagGlyph() {
        return switch (this) {
            case KNIGHT -> 0xE210;
            case WARRIOR -> 0xE21E;
            case HUNTER -> 0xE221;
            case ASSASSIN -> 0xE200;
            case SORCERER -> 0xF23D;
        };
    }

    /** Class description and primary weapon, verbatim from the captured Select Class menu. */
    public List<Text> selectLore() {
        return switch (this) {
            case KNIGHT -> List.of(
                    Text.of("<7>专精防御的坦克职业。"),
                    Text.of("<7>骑士能够保护自己与队友"),
                    Text.of("<7>免受伤害。"),
                    Text.empty(),
                    Text.of("<7>主武器：<f>剑与盾"));
            case WARRIOR -> List.of(
                    Text.of("<7>高伤害输出职业。"),
                    Text.of("<7>使用双手武器"),
                    Text.of("<7>造成毁灭性伤害。"),
                    Text.empty(),
                    Text.of("<7>主武器：<f>巨斧"));
            case HUNTER -> List.of(
                    Text.of("<7>专精远程战斗。"),
                    Text.of("<7>猎人擅长用陷阱"),
                    Text.of("<7>让敌人无法近身。"),
                    Text.empty(),
                    Text.of("<7>主武器：<f>弓"));
            case ASSASSIN -> List.of(
                    Text.of("<7>专注潜行的职业，"),
                    Text.of("<7>擅长隐匿。"),
                    Text.empty(),
                    Text.of("<7>主武器：<f>匕首"));
            case SORCERER -> List.of(
                    Text.of("<7>专注技能伤害的"),
                    Text.of("<7>施法者。"),
                    Text.of("<7>术士释放元素魔法"),
                    Text.of("<7>远距离轰击敌人。"),
                    Text.empty(),
                    Text.of("<7>主武器：<f>法杖与宝珠"));
        };
    }

    /**
     * Base stats as the captured profile tooltips list them: health, protection, damage. Only the
     * Assassin and Sorcerer screens were captured, so the rest return null and their stats block
     * is left out rather than invented.
     */
    public int[] baseStats() {
        return switch (this) {
            case ASSASSIN -> new int[]{160, 0, 20};
            case SORCERER -> new int[]{170, 0, 20};
            default -> null;
        };
    }

    /**
     * The two abilities unlocked by default, bound to the drop and swap-hand keys.
     *
     * <p>Knight, Warrior and Assassin come from captures. Hunter has not been captured; every
     * captured pair is one offensive/utility ability plus one heal, so it follows that shape.
     */
    public List<RavengardAbility> defaultAbilities() {
        return switch (this) {
            case KNIGHT -> List.of(RavengardAbility.SENTINEL, RavengardAbility.RECOVERY);
            case WARRIOR -> List.of(RavengardAbility.WAR_CRY, RavengardAbility.COOL_OFF);
            case ASSASSIN -> List.of(RavengardAbility.SHADOWS, RavengardAbility.HEAL_WOUNDS);
            case HUNTER -> List.of(RavengardAbility.EAGLE_EYE, RavengardAbility.MED_KIT);
            case SORCERER -> List.of(RavengardAbility.FIREBALL, RavengardAbility.SPRING_HEALING);
        };
    }

    public List<RavengardAbility> getAbilities() {
        return Arrays.stream(RavengardAbility.values())
                .filter(ability -> ability.getOwner() == this)
                .toList();
    }

    public static RavengardClass fromKey(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        for (RavengardClass value : values()) {
            if (value.name().equalsIgnoreCase(key)) {
                return value;
            }
        }
        return null;
    }
}
