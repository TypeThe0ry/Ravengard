package net.swofty.type.generic.redis.service;

import net.swofty.commons.ServerType;
import net.swofty.commons.UnderstandableProxyServer;
import net.swofty.commons.party.FullParty;
import net.swofty.commons.party.PartyBroadcast;
import net.swofty.commons.protocol.RedisProtocol;
import net.swofty.commons.protocol.objects.party.PartyBroadcastPushProtocol;
import net.swofty.commons.protocol.objects.party.PartyBroadcastPushProtocol.Request;
import net.swofty.commons.protocol.objects.party.PartyBroadcastPushProtocol.Response;
import net.swofty.commons.text.Text;
import net.swofty.proxyapi.ProxyPlayer;
import net.swofty.commons.redis.RedisMessageHandler;
import net.swofty.type.generic.HypixelConst;
import net.swofty.type.generic.HypixelGenericLoader;
import net.swofty.type.generic.user.HypixelPlayer;
import org.tinylog.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.swofty.commons.redis.RedisMessageContext;

public class PartyBroadcastHandler implements RedisMessageHandler<Request, Response> {

    private static final PartyBroadcastPushProtocol PROTOCOL = new PartyBroadcastPushProtocol();

    @Override
    public RedisProtocol<Request, Response> protocol() {
        return PROTOCOL;
    }

    @Override
    public Response handle(Request message, RedisMessageContext context) {
        try {
            PartyBroadcast broadcast = message.broadcast();
            List<UUID> handled = new ArrayList<>();
            Map<UUID, String> rejected = new HashMap<>();

            for (UUID participant : broadcast.participants()) {
                HypixelPlayer player = findPlayer(participant);
                if (player == null) continue;
                try {
                    DispatchResult result = render(player, broadcast);
                    if (result.handled) handled.add(participant);
                    if (result.rejection != null) rejected.put(participant, result.rejection);
                } catch (Exception e) {
                    Logger.error(e, "Failed to render party broadcast {} for {}",
                            broadcast.getClass().getSimpleName(), participant);
                }
            }
            return Response.success(handled, rejected);
        } catch (Exception e) {
            Logger.error(e, "Failed to handle party broadcast");
            return Response.failure(e.getMessage());
        }
    }

    private static HypixelPlayer findPlayer(UUID uuid) {
        return HypixelGenericLoader.getLoadedPlayers().stream()
                .filter(p -> p.getUuid().equals(uuid))
                .findFirst().orElse(null);
    }

    private static DispatchResult render(HypixelPlayer player, PartyBroadcast broadcast) {
        return switch (broadcast) {
            case PartyBroadcast.Invited b -> renderInvited(player, b);
            case PartyBroadcast.InviteExpired b -> renderInviteExpired(player, b);
            case PartyBroadcast.MemberJoined b -> renderJoined(player, b);
            case PartyBroadcast.MemberLeft b -> renderLeft(player, b);
            case PartyBroadcast.MemberKicked b -> renderKicked(player, b);
            case PartyBroadcast.LeaderTransferred b -> renderTransferred(player, b);
            case PartyBroadcast.RoleChanged b -> renderRoleChanged(player, b);
            case PartyBroadcast.Disbanded b -> renderDisbanded(player, b);
            case PartyBroadcast.Chat b -> renderChat(player, b);
            case PartyBroadcast.Warp b -> renderWarp(player, b);
            case PartyBroadcast.WarpOverview b -> renderWarpOverview(player, b);
            case PartyBroadcast.MemberSwitchedServer b -> renderSwitchedServer(player, b);
            case PartyBroadcast.MemberDisconnected b -> renderDisconnected(player, b);
            case PartyBroadcast.MemberRejoined b -> renderRejoined(player, b);
            case PartyBroadcast.MemberDisconnectTimedOut b -> renderDisconnectTimedOut(player, b);
        };
    }

    private static DispatchResult renderInvited(HypixelPlayer player, PartyBroadcast.Invited b) {
        UUID inviter = b.party().leader();
        UUID invitee = b.party().invitee();
        if (invitee.equals(player.getUuid())) {
            String inviterName = HypixelPlayer.getRawName(inviter);
            player.sendMessage("<sep>");
            player.sendMessage("{} <e>邀请你加入其队伍！", displayName(inviter));
            player.sendMessage("""
                    <hover:'<e>点击这里加入！'><click:run:'/p accept {}'>\
                    <e>你有 <c>60 <e>秒时间接受。<6>点击这里加入！</click></hover>""",
                    inviterName);
            player.sendMessage("<sep>");
        } else {
            sendBoxed(player, "{} <e>邀请了 {} <e>加入队伍！对方有 <c>60 <e>秒时间接受。",
                    displayName(inviter), displayName(invitee));
        }
        return DispatchResult.ok();
    }

    private static DispatchResult renderInviteExpired(HypixelPlayer player, PartyBroadcast.InviteExpired b) {
        if (b.invitee().equals(player.getUuid())) {
            sendBoxed(player, "<e>来自 {} <e>的组队邀请已过期！", displayName(b.inviter()));
        } else if (b.inviter().equals(player.getUuid())) {
            sendBoxed(player, "<e>发给 {} <e>的组队邀请已过期。", displayName(b.invitee()));
        }
        return DispatchResult.ok();
    }

    private static DispatchResult renderJoined(HypixelPlayer player, PartyBroadcast.MemberJoined b) {
        UUID leaderUUID = b.party().getLeader().getUuid();
        if (!b.joiner().equals(player.getUuid())) {
            if (leaderUUID.equals(b.inviter())) {
                sendBoxed(player, "{} <e>加入了队伍。", displayName(b.joiner()));
            } else {
                sendBoxed(player, "{} <e>通过 {} <e>的邀请加入了队伍！",
                        displayName(b.joiner()), displayName(b.inviter()));
            }
        } else if (leaderUUID.equals(b.inviter())) {
            sendBoxed(player, "<e>你加入了 {} <e>的队伍！", displayName(leaderUUID));
        } else {
            sendBoxed(player, "<e>你通过 {1} <e>的邀请加入了 {0} <e>的队伍！",
                    displayName(leaderUUID), displayName(b.inviter()));
        }
        return DispatchResult.ok();
    }

    private static DispatchResult renderLeft(HypixelPlayer player, PartyBroadcast.MemberLeft b) {
        if (b.leaver().equals(player.getUuid())) {
            sendBoxed(player, "<e>你离开了队伍。");
        } else {
            sendBoxed(player, "{} <e>离开了队伍。", displayName(b.leaver()));
        }
        return DispatchResult.ok();
    }

    private static DispatchResult renderKicked(HypixelPlayer player, PartyBroadcast.MemberKicked b) {
        if (b.kicked().equals(player.getUuid())) {
            sendBoxed(player, "<c>你被踢出了队伍！");
        } else {
            sendBoxed(player, "{} <e>将 {} <e>踢出了队伍！",
                    displayName(b.kicker()), displayName(b.kicked()));
        }
        return DispatchResult.ok();
    }

    private static DispatchResult renderTransferred(HypixelPlayer player, PartyBroadcast.LeaderTransferred b) {
        if (b.newLeader().equals(player.getUuid())) {
            sendBoxed(player, "<e>你现在是队长了！");
        } else {
            sendBoxed(player, "<e>队伍已移交给 {}", displayName(b.newLeader()));
        }
        return DispatchResult.ok();
    }

    private static String roleName(FullParty.Role role) {
        return switch (role) {
            case LEADER -> "队长";
            case MODERATOR -> "管理员";
            case MEMBER -> "成员";
        };
    }

    private static DispatchResult renderRoleChanged(HypixelPlayer player, PartyBroadcast.RoleChanged b) {
        boolean isDemotion = b.newRole() == FullParty.Role.MEMBER;
        String roleName = roleName(b.newRole());
        if (b.promoted().equals(player.getUuid())) {
            if (isDemotion) {
                sendBoxed(player, "<c>你被降为成员！");
            } else {
                sendBoxed(player, "<a>你被提升为{}！", roleName);
            }
        } else {
            sendBoxed(player, isDemotion ? "{} <e>将 {} <e>降为{}！" : "{} <e>将 {} <e>提升为{}！",
                    displayName(b.promoter()), displayName(b.promoted()), roleName);
        }
        return DispatchResult.ok();
    }

    private static DispatchResult renderDisbanded(HypixelPlayer player, PartyBroadcast.Disbanded b) {
        sendBoxed(player, "{} <e>解散了队伍！", displayName(b.disbander()));
        return DispatchResult.ok();
    }

    private static DispatchResult renderChat(HypixelPlayer player, PartyBroadcast.Chat b) {
        player.sendMessage("<9>队伍 <8>> {}<f>: {}", displayName(b.sender()), b.message());
        return DispatchResult.ok();
    }

    private static DispatchResult renderWarp(HypixelPlayer player, PartyBroadcast.Warp b) {
        if (b.warper().equals(player.getUuid())) {
            player.sendMessage("<7>正在传送队伍……");
            return DispatchResult.ok();
        }

        UUID warper = b.warper();
        Text warperName = displayName(warper);
        FullParty.Member warperMember = b.party().getFromUuid(warper);

        sendBoxed(player, "<e>队伍{} {}<e> 把你召唤到了其所在服务器。",
                roleName(warperMember.getRole()), warperName);

        ProxyPlayer warperProxy = new ProxyPlayer(warper);
        if (!warperProxy.isOnline().join()) {
            player.sendMessage("<c>找不到 {} 所在的代理！", warperName);
            return DispatchResult.rejected("发起传送的玩家已离线");
        }

        UnderstandableProxyServer warperServer = warperProxy.getServer().join();
        if (warperServer.uuid().equals(HypixelConst.getServerUUID())) {
            return DispatchResult.ok();
        }

        new ProxyPlayer(player.getUuid()).transferToWithIndication(warperServer.uuid())
                .orTimeout(2, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    if (player.isOnline()) {
                        throw new RuntimeException(throwable);
                    }
                    return null;
                }).join();
        return DispatchResult.ok();
    }

    private static DispatchResult renderWarpOverview(HypixelPlayer player, PartyBroadcast.WarpOverview b) {
        if (!b.warper().equals(player.getUuid())) return DispatchResult.ok();
        int total = b.warped().size() + b.failed().size();
        player.sendMessage("<sep>");
        player.sendMessage("<e>队伍传送<7>（{} 名玩家）", total);
        for (UUID uuid : b.warped()) {
            player.sendMessage("<a><l>✔ </l>{} <a>已传送到你的服务器", displayName(uuid));
        }
        for (UUID uuid : b.failed()) {
            String reason = b.failureReasons() != null
                    ? b.failureReasons().getOrDefault(uuid, "无法传送")
                    : "无法传送";
            player.sendMessage("<c><l>✖ </l>{} <c>- {}", displayName(uuid), reason);
        }
        player.sendMessage("<sep>");
        return DispatchResult.ok();
    }

    private static DispatchResult renderSwitchedServer(HypixelPlayer player, PartyBroadcast.MemberSwitchedServer b) {
        if (b.mover().equals(player.getUuid())) return DispatchResult.ok();

        ProxyPlayer mover = new ProxyPlayer(b.mover());
        if (!mover.isOnline().join()) return DispatchResult.ok();

        UnderstandableProxyServer moverServer = mover.getServer().join();
        ServerType moverServerType = moverServer.type();
        Text hover = Text.of("""
                <e>{}
                <9>队伍成员
                \s
                <e>点击跟随！""",
                "Hypixel 传送");

        player.sendMessage("""
                <hover:'{2}'><click:run:'/p movetoserver {3}'>\
                <9><l>» </l>{0} <e>正在前往 <a>{1} <e><l>跟随""",
                displayName(b.mover()), moverServerType.formatName(), hover, moverServer.uuid());
        return DispatchResult.ok();
    }

    private static DispatchResult renderDisconnected(HypixelPlayer player, PartyBroadcast.MemberDisconnected b) {
        if (b.disconnectedPlayer().equals(player.getUuid())) return DispatchResult.ok();
        int minutes = (int) (b.timeoutSeconds() / 60);
        sendBoxed(player, "{} <e>已断开连接。对方有 <c>{} 分钟<e>时间重新加入，否则将被移出队伍。",
                displayName(b.disconnectedPlayer()), minutes);
        return DispatchResult.ok();
    }

    private static DispatchResult renderRejoined(HypixelPlayer player, PartyBroadcast.MemberRejoined b) {
        if (b.rejoinedPlayer().equals(player.getUuid())) return DispatchResult.ok();
        sendBoxed(player, "{} <e>已重新连接到队伍。", displayName(b.rejoinedPlayer()));
        return DispatchResult.ok();
    }

    private static DispatchResult renderDisconnectTimedOut(HypixelPlayer player, PartyBroadcast.MemberDisconnectTimedOut b) {
        Text name = displayName(b.timedOutPlayer());
        if (b.wasLeader()) {
            sendBoxed(player, "<c>队长 {} <c>连接超时，队伍已解散。", name);
        } else if (!b.timedOutPlayer().equals(player.getUuid())) {
            sendBoxed(player, "{} <e>因断线超时被移出队伍。", name);
        }
        return DispatchResult.ok();
    }

    private static Text displayName(UUID uuid) {
        return HypixelPlayer.getDisplayName(uuid);
    }

    private static void sendBoxed(HypixelPlayer player, String markup, Object... arguments) {
        player.sendMessage("<sep>");
        player.sendMessage(markup, arguments);
        player.sendMessage("<sep>");
    }

    private record DispatchResult(boolean handled, String rejection) {
        static DispatchResult ok() { return new DispatchResult(true, null); }
        static DispatchResult rejected(String reason) { return new DispatchResult(false, reason); }
    }
}
