package net.swofty.commons.punishment;

import lombok.Getter;

@Getter
public enum PunishmentTag {
    PERSONAL_PROOF("个人取证", "P"),
    GOLIATH("通过 Goliath 执行的处罚", "G"),
    PLAYER_REPORT("玩家举报", "R"),
    FORUMS("论坛", "F"),
    SLACK("Slack", "S"),
    WELFARE("出于关怀考虑执行的处罚", "W"),
    ACCOUNT_SECURITY_ALERT(null, "ASA"),
    RANKED_TEAM(null, "RT"),
    CHECK_BEFORE_UNBAN("解封该用户前请先与处罚执行人确认", "U"),
    OVERWRITE("该处罚覆盖了另一条处罚", "O");

    private final String description;
    private final String shortCode;

    PunishmentTag(String description, String shortCode) {
        this.description = description;
        this.shortCode = shortCode;
    }

}