package net.swofty.type.generic.redis.service;

import net.swofty.commons.StringUtility;
import net.swofty.commons.text.Text;
import net.swofty.commons.friend.FriendEvent;
import net.swofty.commons.friend.FriendSettingType;
import net.swofty.commons.friend.events.response.*;
import net.swofty.commons.protocol.RedisProtocol;
import net.swofty.commons.protocol.objects.friend.FriendEventPushProtocol;
import net.swofty.commons.protocol.objects.friend.FriendEventPushProtocol.Request;
import net.swofty.commons.protocol.objects.friend.FriendEventPushProtocol.Response;
import net.swofty.commons.redis.RedisMessageHandler;
import net.swofty.type.generic.HypixelGenericLoader;
import net.swofty.type.generic.user.HypixelPlayer;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.swofty.commons.redis.RedisMessageContext;

public class FriendEventHandler implements RedisMessageHandler<Request, Response> {

    private static final FriendEventPushProtocol PROTOCOL = new FriendEventPushProtocol();

    @Override
    public RedisProtocol<Request, Response> protocol() {
        return PROTOCOL;
    }

    @Override
    public Response handle(Request message, RedisMessageContext context) {
        try {
            FriendEvent event = parseEvent(message.eventType(), message.eventData());
            if (event == null) {
                Logger.error("Failed to parse friend event of type: " + message.eventType());
                return Response.failure("Failed to parse event of type: " + message.eventType());
            }

            List<UUID> playersHandled = handleEventForPlayers(event, message.participants());
            return Response.success(playersHandled.size(), playersHandled);
        } catch (Exception e) {
            Logger.error("Failed to handle friend event: " + e.getMessage());
            return Response.failure("Exception occurred: " + e.getMessage());
        }
    }

    private FriendEvent parseEvent(String eventType, String eventData) {
        try {
            FriendEvent templateEvent = FriendEvent.findFromType(eventType);
            return (FriendEvent) templateEvent.getSerializer().deserialize(eventData);
        } catch (Exception e) {
            Logger.error(e, "Failed to parse friend event of type: {}", eventType);
            return null;
        }
    }

    private List<UUID> handleEventForPlayers(FriendEvent event, List<UUID> participants) {
        List<UUID> playersHandled = new ArrayList<>();

        for (UUID participantUUID : participants) {
            HypixelPlayer player = HypixelGenericLoader.getLoadedPlayers().stream()
                    .filter(p -> p.getUuid().equals(participantUUID))
                    .findFirst()
                    .orElse(null);

            if (player != null) {
                try {
                    handleEventForPlayer(player, event);
                    playersHandled.add(participantUUID);
                } catch (Exception e) {
                    Logger.error("Failed to handle friend event for player " + participantUUID + ": " + e.getMessage());
                }
            }
        }

        return playersHandled;
    }

    private void handleEventForPlayer(HypixelPlayer player, FriendEvent event) {
        switch (event) {
            case FriendRequestSentResponseEvent e -> handleRequestSent(player, e);
            case FriendRequestReceivedResponseEvent e -> handleRequestReceived(player, e);
            case FriendAddedResponseEvent e -> handleFriendAdded(player, e);
            case FriendDeniedResponseEvent e -> handleRequestDenied(player, e);
            case FriendRemovedResponseEvent e -> handleFriendRemoved(player, e);
            case FriendRemoveAllResponseEvent e -> handleRemoveAll(player, e);
            case FriendBestToggledResponseEvent e -> handleBestToggled(player, e);
            case FriendNicknameSetResponseEvent e -> handleNicknameSet(player, e);
            case FriendSettingToggledResponseEvent e -> handleSettingToggled(player, e);
            case FriendJoinNotificationEvent e -> handleJoinNotification(player, e);
            case FriendLeaveNotificationEvent e -> handleLeaveNotification(player, e);
            case FriendRequestExpiredResponseEvent e -> handleRequestExpired(player, e);
            case FriendListResponseEvent e -> handleFriendList(player, e);
            case FriendRequestsListResponseEvent e -> handleRequestsList(player, e);
            default -> Logger.warn("Unhandled friend event type: " + event.getClass().getSimpleName());
        }
    }

    private void handleRequestSent(HypixelPlayer player, FriendRequestSentResponseEvent event) {
        sendMessage(player, "<a>已向 <e>{}<a> 发送好友请求！", event.getTargetName());
    }

    private void handleRequestReceived(HypixelPlayer player, FriendRequestReceivedResponseEvent event) {
        String senderName = event.getSenderName();

        player.sendMessage("<sep>");
        player.sendMessage("<a>来自 <e>{} <a>的好友请求", senderName);
        player.sendMessage("""
                <e>点击 <6><l>接受 </l><e>或 <c><l>拒绝</l>\
                <a><l><hover:'<e>点击接受好友请求'><click:run:'/f accept {0}'> [接受]</click></hover></l>\
                <c><l><hover:'<e>点击拒绝好友请求'><click:run:'/f deny {0}'> [拒绝]</click></hover>""",
                senderName);
        player.sendMessage("<sep>");
    }

    private void handleFriendAdded(HypixelPlayer player, FriendAddedResponseEvent event) {
        String otherName = player.getUuid().equals(event.getPlayer1())
                ? event.getPlayer2Name()
                : event.getPlayer1Name();
        sendMessage(player, "<a>你与 <e>{}<a> 成为好友了！", otherName);
    }

    private void handleRequestDenied(HypixelPlayer player, FriendDeniedResponseEvent event) {
        sendMessage(player, "<e>{} <c>拒绝了你的好友请求。", event.getDenierName());
    }

    private void handleFriendRemoved(HypixelPlayer player, FriendRemovedResponseEvent event) {
        if (player.getUuid().equals(event.getRemover())) {
            sendMessage(player, "<c>已将 <e>{} <c>从好友列表中移除。",
                    HypixelPlayer.getDisplayName(event.getRemoved()));
        } else {
            sendMessage(player, "<e>{} <c>将你从其好友列表中移除了。", event.getRemoverName());
        }
    }

    private void handleRemoveAll(HypixelPlayer player, FriendRemoveAllResponseEvent event) {
        if (event.getRemovedCount() == 0) {
            sendMessage(player, "<c>没有可移除的好友！（挚友会保留）");
        } else {
            sendMessage(player, "<c>已从列表中移除 <e>{} <c>位好友，挚友已保留。",
                    event.getRemovedCount());
        }
    }

    private void handleBestToggled(HypixelPlayer player, FriendBestToggledResponseEvent event) {
        if (event.isBest()) {
            sendMessage(player, "<e>{} <a>现在是你的挚友了！", event.getTargetName());
        } else {
            sendMessage(player, "<e>{} <c>不再是你的挚友。", event.getTargetName());
        }
    }

    private void handleNicknameSet(HypixelPlayer player, FriendNicknameSetResponseEvent event) {
        if (event.getNickname() == null || event.getNickname().isEmpty()) {
            sendMessage(player, "<a>已清除 <e>{}<a> 的备注。", event.getTargetName());
        } else {
            sendMessage(player, "<a>已将 <e>{} <a>的备注设为 <e>{}<a>。",
                    event.getTargetName(), event.getNickname());
        }
    }

    private void handleSettingToggled(HypixelPlayer player, FriendSettingToggledResponseEvent event) {
        String settingName = event.getSettingType() == FriendSettingType.ACCEPTING_REQUESTS
                ? "好友请求"
                : "好友上线/下线通知";
        sendMessage(player, event.isNewValue()
                ? "<e>{}<7>现已<a>开启<7>。"
                : "<e>{}<7>现已<c>关闭<7>。", settingName);
    }

    private void handleJoinNotification(HypixelPlayer player, FriendJoinNotificationEvent event) {
        player.sendMessage("<a>好友 > {} <7>上线了。", HypixelPlayer.getDisplayName(event.getFriend()));
    }

    private void handleLeaveNotification(HypixelPlayer player, FriendLeaveNotificationEvent event) {
        player.sendMessage("<a>好友 > {} <7>下线了。", HypixelPlayer.getDisplayName(event.getFriend()));
    }

    private void handleRequestExpired(HypixelPlayer player, FriendRequestExpiredResponseEvent event) {
        if (player.getUuid().equals(event.getSender())) {
            sendMessage(player, "<e>你发给 {} <e>的好友请求已过期。",
                    HypixelPlayer.getDisplayName(event.getTarget()));
        } else {
            sendMessage(player, "<e>来自 {} <e>的好友请求已过期。",
                    HypixelPlayer.getDisplayName(event.getSender()));
        }
    }

    private void handleFriendList(HypixelPlayer player, FriendListResponseEvent event) {
        player.sendMessage("<sep>");
        player.sendMessage("<6>{} <7>（第 {}/{} 页）", event.isBestOnly() ? "挚友" : "好友",
                event.getPage(), event.getTotalPages());

        if (event.getFriends().isEmpty()) {
            player.sendMessage(event.isBestOnly() ? "<7>没有可显示的挚友。" : "<7>没有可显示的好友。");
        } else {
            for (FriendListResponseEvent.FriendListEntry friend : event.getFriends()) {
                Text line = Text.of(friend.isOnline() ? "<a>● " : "<c>● ")
                        .appendIf(friend.isBest(), "<6>✦ ")
                        .append(displayNameOf(friend));

                if (friend.getNickname() != null && !friend.getNickname().isEmpty()) {
                    line = line.append(" <7>({})", friend.getNickname());
                }
                if (friend.isOnline() && friend.getServer() != null && !friend.getServer().isEmpty()) {
                    line = line.append(" <7>- <e>{}", StringUtility.toNormalCase(friend.getServer()));
                }

                Text friendsSince = friend.getFriendSince() > 0
                        ? Text.of("<7>已成为好友 {}", formatDuration(secondsSince(friend.getFriendSince())))
                        : Text.of("<7>成为好友时间：未知");

                Text hover;
                if (friend.isOnline()) {
                    hover = friendsSince;
                } else {
                    Text lastSeen = friend.getLastSeen() > 0
                            ? Text.of("<7>最后上线：{} 前", formatDuration(secondsSince(friend.getLastSeen())))
                            : Text.of("<7>最后上线：未知");
                    hover = Text.of("{}\n{}", lastSeen, friendsSince);
                }

                player.sendMessage("<hover:'{1}'>{0}", line, hover);
            }
        }

        if (event.getTotalPages() > 1) {
            player.sendMessage("<7>使用 <e>/f list {}\\<页码> <7>翻页。", event.isBestOnly() ? "best " : "");
        }

        player.sendMessage("<sep>");
    }

    private void handleRequestsList(HypixelPlayer player, FriendRequestsListResponseEvent event) {
        player.sendMessage("<sep>");
        player.sendMessage("<6>待处理的好友请求<7>（第 {}/{} 页）", event.getPage(), event.getTotalPages());

        if (event.getRequests().isEmpty()) {
            player.sendMessage("<7>没有待处理的好友请求。");
        } else {
            for (FriendRequestsListResponseEvent.FriendRequestEntry request : event.getRequests()) {
                player.sendMessage("""
                        <e>{0} \
                        <a><hover:'<e>点击接受'><click:run:'/f accept {0}'>[接受]</click></hover>\
                        <c><hover:'<e>点击拒绝'><click:run:'/f deny {0}'> [拒绝]</click></hover>""",
                        request.getSenderName());
            }
        }

        if (event.getTotalPages() > 1) {
            player.sendMessage("<7>使用 <e>/f requests \\<页码> <7>翻页。");
        }

        player.sendMessage("<sep>");
    }

    private Text displayNameOf(FriendListResponseEvent.FriendListEntry friend) {
        Text displayName;
        try {
            displayName = HypixelPlayer.getDisplayName(friend.getUuid());
        } catch (Exception e) {
            displayName = null;
        }
        return displayName == null || displayName.isEmpty()
                ? Text.of("<e>{}", friend.getName())
                : displayName;
    }

    private long secondsSince(long timestamp) {
        return Math.max(0, (System.currentTimeMillis() - timestamp) / 1000);
    }

    private void sendMessage(HypixelPlayer player, String markup, Object... arguments) {
        player.sendMessage("<sep>");
        player.sendMessage(markup, arguments);
        player.sendMessage("<sep>");
    }

    private String formatDuration(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        if (days > 0) {
            return days + "天" + hours + "小时";
        }
        if (hours > 0) {
            return hours + "小时" + minutes + "分钟";
        }
        if (minutes > 0) {
            return minutes + "分钟";
        }
        return seconds + "秒";
    }
}
