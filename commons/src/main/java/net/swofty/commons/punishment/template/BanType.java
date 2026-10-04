package net.swofty.commons.punishment.template;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum BanType {
    WATCHDOG(PunishmentCategory.ADMIN_ONLY, "WATCHDOG 作弊检测", "WATCHDOG", 4,
            "https://www.hypixel.net/watchdog", true, true),
    BLACKLISTED_MODIFICATIONS(PunishmentCategory.CHEATING, "使用不公平的游戏优势作弊。", "BM", 4,
            null, true, true, Arrays.asList("使用黑名单模组", "作弊/不公平优势", "在游戏中使用不公平优势")),
    CROSS_TEAMING(PunishmentCategory.GAMEPLAY, "跨队合作：你被发现与其他队伍或玩家串通。", "CT", 1, null, false, false),
    TEAM_GRIEFING(PunishmentCategory.GAMEPLAY, "你被发现对队友造成了负面影响。", "TG", 1, null, false, false),
    INAPPROPRIATE_BUILD(PunishmentCategory.INAPPROPRIATE_CONTENT, "在服务器上创作了不恰当的建筑或绘画。", "IB", 1,
            null, false, false, Arrays.asList("不恰当的建筑", "不恰当的绘画")),
    INAPPROPRIATE_ITEM_NAME(PunishmentCategory.INAPPROPRIATE_CONTENT, "创建或使用名称不恰当的物品", "IN", 1, null, false, false),
    INAPPROPRIATE_ITEM_USAGE(PunishmentCategory.INAPPROPRIATE_CONTENT, "以不恰当的方式使用宠物或装饰品。", "IU", 1, null, false, false),
    STAFF_IMPERSONATION(null, "误导他人相信你是主播或工作人员。", "SI", 1, null, false, false),
    SCAMMING(null, "试图从玩家处骗取信息或有价值的物品。", "SC", 2, null, false, false),
    ENCOURAGING_CHEATING_LVL2(PunishmentCategory.CHEATING, "讨论或做出鼓励作弊、违规的行为。", "EC2", 2, null, false, false),
    ENCOURAGING_CHEATING_LVL3(PunishmentCategory.CHEATING, "讨论或做出鼓励作弊、违规的行为。", "EC3", 4, null, false, false),
    EXTREME_USER_DISRESPECT(null, "对社区成员做出极度不尊重的行为。", "EUD", 2,
            null, false, false, List.of("极端负面行为")),
    STATS_BOOSTING(PunishmentCategory.ADMIN_ONLY, "通过刷号提升自己的数据。", "SB", 4,
            null, false, false, List.of("刷数据")),
    INAPPROPRIATE_AESTHETICS(PunishmentCategory.INAPPROPRIATE_CONTENT, "在服务器上使用不恰当的皮肤或披风。", "IA", 2, null, false, false),
    EXPLOITING(PunishmentCategory.ADMIN_ONLY, "利用服务器的漏洞或问题为自己谋利。", "EX", 4,
            null, true, true, List.of("利用漏洞")),
    FALSIFIED_INFORMATION(null, "制造或传播虚假信息。", "FI", 3, null, false, false),
    CHARGEBACK(PunishmentCategory.ADMIN_ONLY, "拒付：了解详情及申诉请前往 support.hypixel.net。", null, -1,
            null, false, false, List.of("拒付")),
    FRAUD(null, null, "FR", 0, null, false, false),
    ACCOUNT_SELLING(null, "试图出售 Minecraft 账号。", "AS", 4, null, false, false),
    COMPROMISED_ACCOUNT(PunishmentCategory.ACCOUNT_SECURITY, "你的账号存在安全警报，请先确保账号安全，再联系申诉渠道。", "CA", -1,
            null, false, false, Arrays.asList("账号被盗", "账号安全警报"), "账号安全警报"),
    ACCOUNT_SECURITY_ALERT_SERVER_ADVERTISING(PunishmentCategory.ACCOUNT_SECURITY, "你的账号存在安全警报，请先确保账号安全，再联系申诉渠道。", "CAS", -1, null, false, false),
    ACCOUNT_SECURITY_ALERT_BLACKLISTED(PunishmentCategory.ACCOUNT_SECURITY, "你的账号存在安全警报，请先确保账号安全，再联系申诉渠道。", "CAB", -1, null, false, false),
    PHISHING_LINK(null, "试图获取其他用户的账号或信息。", "PL", 4, null, false, false),
    UN_INTENTIONALLY_CAUSING_DISTRESS_2(null, "无意或有意地造成他人困扰。", "UIB", 3, null, false, false),
    UN_INTENTIONALLY_CAUSING_DISTRESS_3(PunishmentCategory.ADMIN_ONLY, "无意或有意地造成他人困扰。", null, -1, null, false, false),
    INAPPROPRIATE_CONTENT_LVL2(PunishmentCategory.INAPPROPRIATE_CONTENT, "在服务器上谈论或分享带有成人主题的不恰当内容。", "IC2", 3, null, false, false),
    ACCOUNT_DELETION(PunishmentCategory.ADMIN_ONLY, "应本人要求，该用户的数据已被删除。", null, 0, "https://support.hypixel.net", false, false),
    CREATOR_BAN(PunishmentCategory.ADMIN_ONLY, "请联系工作人员获取帮助。", null, -1,  // Please contact creators@hypixel.net for assistance.
            null, false, false, List.of("创作者封禁"), "创作者封禁"),
    CREATOR_ACCOUNT_SECURITY_ALERT(PunishmentCategory.ADMIN_ONLY, "你的账号存在安全警报，请先确保账号安全，再联系工作人员获取帮助。", // Your account has a security alert, please secure it and contact creators@hypixel.net for assistance
            null, -1, null, false, false, Arrays.asList("创作者账号被盗", "创作者账号安全警报"), "创作者账号安全警报");

    private final PunishmentCategory category;
    private final String reason;
    private final String shortName;
    private final int weight;
    private final String url;
    private final boolean preventRanked;
    private final boolean wipe;
    private final List<String> aliases;
    private final String cleanName;

    BanType(PunishmentCategory category, String reason, String shortName, int weight,
            String url, boolean preventRanked, boolean wipe) {
        this(category, reason, shortName, weight, url, preventRanked, wipe, null, null);
    }

    BanType(PunishmentCategory category, String reason, String shortName, int weight,
            String url, boolean preventRanked, boolean wipe, List<String> aliases) {
        this(category, reason, shortName, weight, url, preventRanked, wipe, aliases, null);
    }

    BanType(PunishmentCategory category, String reason, String shortName, int weight,
            String url, boolean preventRanked, boolean wipe, List<String> aliases, String cleanName) {
        this.category = category;
        this.reason = reason;
        this.shortName = shortName;
        this.weight = weight;
        this.url = url;
        this.preventRanked = preventRanked;
        this.wipe = wipe;
        this.aliases = aliases;
        this.cleanName = cleanName;
    }
}
