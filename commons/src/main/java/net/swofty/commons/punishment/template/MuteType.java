package net.swofty.commons.punishment.template;

import lombok.Getter;
import java.util.List;

@Getter
public enum MuteType {
    NEGATIVE_REFERENCE("以负面方式讨论重要人物或世界事件。", "NR", 4),
    USER_DISRESPECT("对社区成员做出不尊重的行为。", "UD", 4),
    STAFF_DISRESPECT("对工作人员做出不尊重的行为。", "SD", 4),
    INAPPROPRIATE_CONTENT_LVL1("在服务器公共聊天中使用成人话题。", "IC1", 3),
    DISCRIMINATION("歧视某位玩家或某个群体。", "DI", 3),
    EXCESSIVE_SWEARING("在聊天中过度使用脏话。", "ES", 2),
    UN_INTENTIONALLY_CAUSING_DISTRESS("无意或有意地造成他人困扰。", "UI", 2),
    ENCOURAGING_CHEATING_LVL1("讨论或积极宣传在服务器上作弊、违规。", "EC1", 2),
    MEDIA_ADVERTISING("媒体广告", "MA", 1),
    PUBLIC_SHAMING("公开泄露玩家的信息。", "PS", 1),
    RUDE("言行粗鲁或不恰当。", "RU", 1),
    EXCESSIVE_SPAMMING("反复发送无意义的消息或内容。", "SP", 1),
    MISLEADING_INFORMATION("误导其他玩家做出干扰其游戏的行为。", "MI", 1, List.of("恶意捣乱")),
    UNNECESSARY_SPOILERS("剧透：泄露热门电影、电视剧的重要剧情。", "US", 1),
    ESCALATION("你因聊天违规被禁言，目前正在审核中。", "ESC", 0);

    private final String reason;
    private final String shortName;
    private final int weight;
    private final List<String> aliases;

    MuteType(String reason, String shortName, int weight) {
        this(reason, shortName, weight, null);
    }

    MuteType(String reason, String shortName, int weight, List<String> aliases) {
        this.reason = reason;
        this.shortName = shortName;
        this.weight = weight;
        this.aliases = aliases;
    }
}