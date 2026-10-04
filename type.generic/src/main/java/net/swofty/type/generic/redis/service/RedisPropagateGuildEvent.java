package net.swofty.type.generic.redis.service;

import net.swofty.commons.guild.GuildEvent;
import net.swofty.commons.guild.events.response.*;
import net.swofty.commons.protocol.objects.guild.GuildEventPushProtocol;
import net.swofty.commons.redis.RedisMessageHandler;
import net.swofty.commons.redis.RedisMessageContext;
import net.swofty.commons.protocol.RedisProtocol;
import net.swofty.commons.text.Text;
import net.swofty.type.generic.HypixelGenericLoader;
import net.swofty.type.generic.user.HypixelPlayer;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RedisPropagateGuildEvent implements RedisMessageHandler<GuildEventPushProtocol.Request, GuildEventPushProtocol.Response> {
    private static final GuildEventPushProtocol PROTOCOL = new GuildEventPushProtocol();

    @Override
    public RedisProtocol<GuildEventPushProtocol.Request, GuildEventPushProtocol.Response> protocol() {
        return PROTOCOL;
    }

    @Override
    public GuildEventPushProtocol.Response handle(GuildEventPushProtocol.Request message, RedisMessageContext context) {
        try {
            GuildEvent event = parseEvent(message.eventType(), message.eventData());
            List<UUID> playersHandled = handleEventForPlayers(event, message.participants());
            return GuildEventPushProtocol.Response.success(playersHandled);
        } catch (Exception e) {
            Logger.error(e, "Failed to handle guild event");
            return GuildEventPushProtocol.Response.failure(e.getMessage());
        }
    }

    private GuildEvent parseEvent(String eventType, String eventData) {
        GuildEvent template = GuildEvent.findFromType(eventType);
        return (GuildEvent) template.getSerializer().deserialize(eventData);
    }

    private List<UUID> handleEventForPlayers(GuildEvent event, List<UUID> participants) {
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
                    Logger.error("Failed to handle guild event for player " + participantUUID + ": " + e.getMessage());
                }
            }
        }

        return playersHandled;
    }

    private void handleEventForPlayer(HypixelPlayer player, GuildEvent event) {
        switch (event) {
            case GuildCreatedResponseEvent e -> handleCreated(player, e);
            case GuildInviteSentResponseEvent e -> handleInviteSent(player, e);
            case GuildMemberJoinedResponseEvent e -> handleMemberJoined(player, e);
            case GuildMemberLeftResponseEvent e -> handleMemberLeft(player, e);
            case GuildMemberKickedResponseEvent e -> handleMemberKicked(player, e);
            case GuildDisbandedResponseEvent e -> handleDisbanded(player, e);
            case GuildRankChangedResponseEvent e -> handleRankChanged(player, e);
            case GuildTransferredResponseEvent e -> handleTransferred(player, e);
            case GuildChatResponseEvent e -> handleChat(player, e);
            case GuildSettingChangedResponseEvent e -> handleSettingChanged(player, e);
            case GuildMuteChangedResponseEvent e -> handleMuteChanged(player, e);
            case GuildInviteExpiredResponseEvent e -> handleInviteExpired(player, e);
            default -> Logger.warn("Unhandled guild event type: " + event.getClass().getSimpleName());
        }
    }

    private void handleCreated(HypixelPlayer player, GuildCreatedResponseEvent event) {
        sendMessage(player, "<a>你创建了公会 <6>{}<a>！", event.getGuild().getName());
    }

    private void handleInviteSent(HypixelPlayer player, GuildInviteSentResponseEvent event) {
        if (event.getInvitee().equals(player.getUuid())) {
            sendMessage(player, "{} <e>邀请你加入其公会 <6>{}<e>！",
                displayName(event.getInviter()), event.getGuild().getName());
            player.sendMessage("""
                <hover:'<e>点击接受！'><click:run:'/guild accept {}'>\
                <e>你有 <c>60 <e>秒时间接受。<6>点击这里加入！</click></hover>""",
                HypixelPlayer.getRawName(event.getInviter()));
        } else if (event.getInviter().equals(player.getUuid())) {
            sendMessage(player, "<e>你邀请了 {} <e>加入你的公会。对方有 <c>60 <e>秒时间接受。",
                displayName(event.getInvitee()));
        }
    }

    private void handleMemberJoined(HypixelPlayer player, GuildMemberJoinedResponseEvent event) {
        if (event.getJoiner().equals(player.getUuid())) {
            sendMessage(player, "<a>你加入了公会 <6>{}<a>！", event.getGuild().getName());
        } else {
            sendMessage(player, "{} <e>加入了公会！", displayName(event.getJoiner()));
        }
    }

    private void handleMemberLeft(HypixelPlayer player, GuildMemberLeftResponseEvent event) {
        if (event.getLeaver().equals(player.getUuid())) {
            sendMessage(player, "<e>你离开了公会。");
        } else {
            sendMessage(player, "{} <e>离开了公会。", displayName(event.getLeaver()));
        }
    }

    private void handleMemberKicked(HypixelPlayer player, GuildMemberKickedResponseEvent event) {
        if (event.getKicked().equals(player.getUuid())) {
            if (event.getReason() != null && !event.getReason().isEmpty()) {
                sendMessage(player, "<c>你被踢出了公会！<7>原因：<f>{}", event.getReason());
            } else {
                sendMessage(player, "<c>你被踢出了公会！");
            }
        } else {
            sendMessage(player, "{} <e>将 {} <e>踢出了公会！",
                displayName(event.getKicker()), displayName(event.getKicked()));
        }
    }

    private void handleDisbanded(HypixelPlayer player, GuildDisbandedResponseEvent event) {
        sendMessage(player, "{} <e>解散了公会！", displayName(event.getDisbander()));
    }

    private void handleRankChanged(HypixelPlayer player, GuildRankChangedResponseEvent event) {
        if (event.getTarget().equals(player.getUuid())) {
            sendMessage(player, isPrioritized(event.getFromRank(), event.getToRank())
                ? "<e>你被<a>提升<e>为 <6>{}<e>！"
                : "<e>你被<c>降级<e>为 <6>{}<e>！", event.getToRank());
        } else {
            sendMessage(player, "{} <e>将 {} <e>的职位从 <6>{} <e>改为 <6>{}<e>。",
                displayName(event.getChanger()), displayName(event.getTarget()),
                event.getFromRank(), event.getToRank());
        }
    }

    private void handleTransferred(HypixelPlayer player, GuildTransferredResponseEvent event) {
        if (event.getNewOwner().equals(player.getUuid())) {
            sendMessage(player, "<a>你现在是公会会长了！");
        } else {
            sendMessage(player, "{} <e>将公会所有权转让给了 {}<e>！",
                displayName(event.getOldOwner()), displayName(event.getNewOwner()));
        }
    }

    private void handleChat(HypixelPlayer player, GuildChatResponseEvent event) {
        player.sendMessage(event.isOfficerChat()
                ? "<3>官员 > {}<f>: {}"
                : "<2>公会 > {}<f>: {}",
            displayName(event.getSender()), event.getMessage());
    }

    private void handleSettingChanged(HypixelPlayer player, GuildSettingChangedResponseEvent event) {
        Text settingDisplay = switch (event.getSetting().toLowerCase()) {
            case "tag" -> Text.of("公会标签：<6>{}", event.getValue());
            case "tagcolor" -> Text.of("标签颜色：<6>{}", event.getValue());
            case "motd" -> Text.of("公会公告");
            case "description" -> Text.of("公会简介");
            case "discord" -> Text.of("Discord 链接");
            case "rename" -> Text.of("公会名称：<6>{}", event.getValue());
            case "slow" -> Text.of("慢速聊天：<6>{}", event.getValue());
            case "finder" -> Text.of("公会查找：<6>{}", event.getValue());
            default -> Text.of("{}：{}", event.getSetting(), event.getValue());
        };
        sendMessage(player, "{} <e>更新了{}<e>。", displayName(event.getChanger()), settingDisplay);
    }

    private void handleMuteChanged(HypixelPlayer player, GuildMuteChangedResponseEvent event) {
        Text muterName = displayName(event.getMuter());
        boolean everyone = event.getTarget().equalsIgnoreCase("everyone");
        if (event.isUnmute()) {
            if (everyone) {
                sendMessage(player, "{} <e>解除了公会聊天的禁言。", muterName);
            } else {
                sendMessage(player, "{} <e>解除了 {} <e>的禁言。",
                    muterName, displayName(UUID.fromString(event.getTarget())));
            }
        } else {
            long minutes = event.getDuration() / 60000;
            if (everyone) {
                sendMessage(player, "{} <e>将公会聊天禁言了 <c>{} 分钟<e>。", muterName, minutes);
            } else {
                sendMessage(player, "{} <e>将 {} <e>禁言了 <c>{} 分钟<e>。",
                    muterName, displayName(UUID.fromString(event.getTarget())), minutes);
            }
        }
    }

    private void handleInviteExpired(HypixelPlayer player, GuildInviteExpiredResponseEvent event) {
        if (event.getInvitee().equals(player.getUuid())) {
            sendMessage(player, "<e>来自 {} <e>的公会邀请已过期。", displayName(event.getInviter()));
        } else if (event.getInviter().equals(player.getUuid())) {
            sendMessage(player, "<e>发给 {} <e>的公会邀请已过期。", displayName(event.getInvitee()));
        }
    }

    private boolean isPrioritized(String fromRank, String toRank) {
        return switch (toRank) {
            case "Guild Master" -> true;
            case "Officer" -> fromRank.equals("Member");
            default -> false;
        };
    }

    private Text displayName(UUID uuid) {
        return HypixelPlayer.getDisplayName(uuid);
    }

    private void sendMessage(HypixelPlayer player, String markup, Object... arguments) {
        player.sendMessage("<sep>");
        player.sendMessage(markup, arguments);
        player.sendMessage("<sep>");
    }

}
