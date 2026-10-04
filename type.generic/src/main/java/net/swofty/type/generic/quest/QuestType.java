package net.swofty.type.generic.quest;

import lombok.Getter;

@Getter
public enum QuestType {
    DAILY("每日", "每天 UTC 零点重置"),
    WEEKLY("每周", "每周四 UTC 零点重置"),
    SPECIAL_DAILY("特殊每日", "带有独特奖励的特殊每日任务"),
    CHALLENGE("挑战", "每天可完成多次，每局限一次");

    private final String displayName;
    private final String description;

    QuestType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
