package net.swofty.commons.punishment.template;

import lombok.Getter;

@Getter
public enum UnpunishReason {
    WRONG_PLAYER("处罚对象有误", null),
    WRONG_PUNISHMENT("处罚类型有误", null),
    INSUFFICIENT_EVIDENCE("证据不足", "IE"),
    ACCOUNT_SECURED("账号已恢复安全", null),
    SECOND_CHANCE("再给一次机会", "SC"),
    CHARGEBACK_APPEALED("拒付已申诉", null),
    CHARGEBACK_FAILED("拒付失败", null),
    CHARGEBACK_CANCELLED("拒付已取消", null),
    NOT_FRAUD("并非欺诈", null),
    TIME_SERVED_MAINTAIN_WEIGHT("已服满处罚（保留权重）", "MW");

    private final String reason;
    private final String tag;

    UnpunishReason(String reason, String tag) {
        this.reason = reason;
        this.tag = tag;
    }
}