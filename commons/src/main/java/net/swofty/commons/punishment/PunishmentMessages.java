package net.swofty.commons.punishment;

import net.swofty.commons.text.Text;

public final class PunishmentMessages {
    private PunishmentMessages() {}

    public static Text banMessage(ActivePunishment punishment) {
        long expiresAt = punishment.expiresAt();
        PunishmentReason reason = punishment.reason();
        String banId = punishment.banId();

        Text header = expiresAt <= 0
                ? Text.of("<c>你已被本服务器永久封禁！")
                : Text.of("<c>你已被本服务器暂时封禁 <f>{:time}<c>！",
                        expiresAt - System.currentTimeMillis());

        Text findOutMore = reason.getBanType() != null && reason.getBanType().getUrl() != null
                ? Text.of("\n<7>了解更多：<b>{}", reason.getBanType().getUrl())
                : Text.empty();

        return Text.of("""
                {0}

                <7>原因：<f>{1}{2}

                <7>封禁 ID：<f>{3}
                <7>分享你的封禁 ID 可能会影响申诉的处理！\
                """, header, reason.getReasonString(), findOutMore, banId);
    }

    public static Text muteMessage(ActivePunishment punishment) {
        long expiresAt = punishment.expiresAt();
        PunishmentReason reason = punishment.reason();
        String reasonString = reason.getReasonString();

        Text header;
        Text expiry;
        if (expiresAt <= 0) {
            header = Text.of("<c>你已被本服务器永久禁言！");
            expiry = Text.empty();
        } else {
            long timeLeft = expiresAt - System.currentTimeMillis();
            header = Text.of("<c>你当前被禁言，原因：{}", reasonString);
            expiry = Text.of("\n<7>禁言将在 <c>{:time}<7> 后解除\n", timeLeft);
        }

        String rule = " ".repeat(53);

        return Text.of("""

                <c><m>{0}</m>
                {1}
                <7>原因：<f>{2}{3}
                <7>了解更多：<f>www.hypixel.net/mutes
                <7>禁言 ID：<f>{4}
                <c><m>{0}</m>
                """, rule, header, reasonString, expiry, punishment.banId());
    }
}
