package net.swofty.type.ravengardgeneric.queue;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import net.swofty.commons.ServerType;
import net.swofty.commons.ServiceType;
import net.swofty.commons.UnderstandableProxyServer;
import net.swofty.commons.party.FullParty;
import net.swofty.commons.protocol.objects.orchestrator.ChooseGameProtocol;
import net.swofty.commons.protocol.objects.orchestrator.GetServerForMapProtocol;
import net.swofty.proxyapi.ProxyService;
import net.swofty.type.generic.party.PartyManager;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The fight queue as the 0.2 playtest presents it: joining answers with the gray then green
 * queue messages and the ominous drums, the menu button flips to leaving, and a found server
 * transfers the player to a dungeon. A run is either solo or in teams of three; a party leader
 * queues the whole party into one run, where its members share a team.
 */
public final class RavengardQueue {
    public static final String NPC_CLIP = "queue";
    private static final TaskSchedule MATCH_DELAY = TaskSchedule.seconds(3);
    private static final Sound DRUMS = Sound.sound()
            .type(Key.key("hypixel_ravengard", "action.drums"))
            .volume(1.0f)
            .pitch(1.0f)
            .build();

    /** Queued players, each pointing at the party (or lone player) waiting with them. */
    private static final Map<UUID, Ticket> QUEUED = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> SEARCHING = ConcurrentHashMap.newKeySet();
    private static final ProxyService ORCHESTRATOR = new ProxyService(ServiceType.ORCHESTRATOR);
    private static final java.util.concurrent.Executor LOOKUPS = runnable -> Thread.ofVirtual().start(runnable);

    private record Ticket(List<UUID> members, Task task) {
    }

    private RavengardQueue() {
    }

    public static boolean isQueued(HypixelPlayer player) {
        return QUEUED.containsKey(player.getUuid()) || SEARCHING.contains(player.getUuid());
    }

    /** Offers the two modes as clickable choices in chat. */
    public static void chooseMode(RavengardPlayer player) {
        Component line = Component.text("选择匹配模式：", NamedTextColor.GRAY);
        for (RavengardMatchMode mode : RavengardMatchMode.values()) {
            line = line.append(Component.text("[" + mode.displayName() + "]", NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/play " + mode.name().toLowerCase()))
                            .hoverEvent(HoverEvent.showText(Component.text(mode.teamSize() == 1
                                    ? "独自一人，与所有人为敌"
                                    : "三人一队，可与队伍成员一同匹配", NamedTextColor.YELLOW))))
                    .append(Component.text(" "));
        }
        player.sendMessage(line);
    }

    public static void join(RavengardPlayer player) {
        join(player, RavengardMatchMode.TRIOS);
    }

    public static void join(RavengardPlayer player, RavengardMatchMode mode) {
        if (!canQueue(player)) return;
        CompletableFuture.supplyAsync(() -> partyOf(player), LOOKUPS).thenAccept(party ->
                MinecraftServer.getSchedulerManager().scheduleNextTick(() -> queueWith(player, mode, party)));
    }

    private static boolean canQueue(RavengardPlayer player) {
        if (isQueued(player)) {
            player.sendMessage("<c>{} 已经在排队了！", player.getUsername());
            return false;
        }
        if (net.swofty.type.generic.HypixelConst.getTypeLoader().getType() == ServerType.RAVENGARD_DUNGEON) {
            player.sendMessage("<c>你已经在地牢中了！");
            return false;
        }
        if (player.isTutorial() || player.getRavengardClass() == null) {
            player.sendMessage("<c>加入战斗前请先选择职业！");
            return false;
        }
        return true;
    }

    private static FullParty partyOf(RavengardPlayer player) {
        try {
            FullParty party = PartyManager.getPartyFromPlayer(player);
            return party != null && party.getParticipants().size() > 1 ? party : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static void queueWith(RavengardPlayer player, RavengardMatchMode mode, FullParty party) {
        if (!player.isOnline() || !canQueue(player)) return;
        List<RavengardPlayer> members = new ArrayList<>();
        if (party == null) {
            members.add(player);
        } else {
            if (!party.getLeader().getUuid().equals(player.getUuid())) {
                player.sendMessage("<c>你在队伍中，只有队长可以开始匹配！");
                return;
            }
            if (party.getParticipants().size() > mode.teamSize()) {
                player.sendMessage(mode.teamSize() == 1
                        ? "<c>单人模式不能组队匹配，请离开队伍或选择三人组！"
                        : "<c>队伍最多 {} 人，当前有 {} 人！", mode.teamSize(), party.getParticipants().size());
                return;
            }
            for (UUID uuid : party.getParticipants()) {
                RavengardPlayer member = uuid.equals(player.getUuid()) ? player
                        : MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) instanceof RavengardPlayer online
                        ? online : null;
                if (member == null) {
                    player.sendMessage(net.swofty.commons.text.Text.of("<c>队伍成员 {}<c> 不在天穹余烬大厅，无法一同匹配！",
                            HypixelPlayer.getDisplayName(uuid)));
                    return;
                }
                if (member != player && (isQueued(member) || member.isTutorial() || member.getRavengardClass() == null)) {
                    player.sendMessage("<c>队伍成员 {} 还不能加入战斗（正在排队或尚未选择职业）！", member.getUsername());
                    return;
                }
                members.add(member);
            }
        }

        List<UUID> uuids = members.stream().map(RavengardPlayer::getUuid).toList();
        for (RavengardPlayer member : members) {
            member.sendMessage("<7>正在加入战斗……");
            member.sendMessage("<a>已成功加入{}队列！", mode.queueName());
            if (members.size() > 1) {
                member.sendMessage("<7>与队伍成员一同匹配：<e>{}", String.join("<7>、<e>",
                        members.stream().map(RavengardPlayer::getUsername).toList()));
            }
            member.playSound(DRUMS);
        }
        Task task = MinecraftServer.getSchedulerManager().buildTask(() -> search(members, mode))
                .delay(MATCH_DELAY).schedule();
        Ticket ticket = new Ticket(uuids, task);
        uuids.forEach(uuid -> QUEUED.put(uuid, ticket));
    }

    private static void search(List<RavengardPlayer> members, RavengardMatchMode mode) {
        members.forEach(member -> QUEUED.remove(member.getUuid()));
        List<RavengardPlayer> online = members.stream().filter(RavengardPlayer::isOnline).toList();
        if (online.size() != members.size()) {
            online.forEach(member -> member.sendMessage("<c>有队伍成员离线，匹配已取消。"));
            return;
        }
        online.forEach(member -> member.sendMessage("<7>已找到服务器，正在传送到天穹余烬地牢……"));
        online.forEach(member -> SEARCHING.add(member.getUuid()));
        GetServerForMapProtocol.GetServerForMapMessage request = new GetServerForMapProtocol.GetServerForMapMessage(
                ServerType.RAVENGARD_DUNGEON, null, mode.name(), online.size());
        ORCHESTRATOR.handleRequest(request, 90, TimeUnit.SECONDS).thenCompose(response -> {
            if (!(response instanceof GetServerForMapProtocol.GetServerForMapResponse(
                    UnderstandableProxyServer server, String gameId, boolean success, String error))
                    || !success || server == null || gameId == null) {
                String reason = response instanceof GetServerForMapProtocol.GetServerForMapResponse r
                        && r.error() != null ? r.error() : "没有可用的地牢服务器";
                online.forEach(member -> member.sendMessage("<c>无法开启地牢：{}", reason));
                return CompletableFuture.<Void>completedFuture(null);
            }
            List<CompletableFuture<Void>> sends = new ArrayList<>();
            for (RavengardPlayer member : online) {
                sends.add(send(member, server, gameId));
            }
            return CompletableFuture.allOf(sends.toArray(new CompletableFuture[0]));
        }).exceptionally(throwable -> {
            online.forEach(member -> member.sendMessage("<c>地牢搜索失败：{}", throwable.getMessage()));
            return null;
        }).whenComplete((ignored, throwable) -> online.forEach(member -> SEARCHING.remove(member.getUuid())));
    }

    private static CompletableFuture<Void> send(RavengardPlayer player, UnderstandableProxyServer server, String gameId) {
        if (!player.isOnline()) return CompletableFuture.completedFuture(null);
        return ORCHESTRATOR.handleRequest(new ChooseGameProtocol.ChooseGameMessage(
                player.getUuid(), server, gameId), 90, TimeUnit.SECONDS).thenAccept(choice -> {
            if (!(choice instanceof ChooseGameProtocol.ChooseGameResponse result)) {
                player.sendMessage("<c>地牢登记失败：无响应");
                return;
            }
            if (!result.success()) {
                player.sendMessage("<c>地牢登记失败：{}", result.error() != null ? result.error() : "地牢登记失败");
                return;
            }
            if (!player.isOnline()) return;
            player.sendMessage("<a>正在把你送往 <f>{}<a>！", server.shortName());
            player.asProxyPlayer().transferToWithIndication(server.uuid());
        }).exceptionally(throwable -> {
            player.sendMessage("<c>地牢登记失败：{}", throwable.getMessage());
            return null;
        });
    }

    public static void leave(RavengardPlayer player) {
        Ticket ticket = QUEUED.get(player.getUuid());
        if (ticket == null) {
            if (SEARCHING.contains(player.getUuid())) {
                player.sendMessage("<c>已经在为你准备地牢了！");
            }
            return;
        }
        ticket.task().cancel();
        for (UUID uuid : ticket.members()) {
            QUEUED.remove(uuid);
            if (MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) instanceof RavengardPlayer member) {
                member.sendMessage(member == player ? "<c>你已离开队列。" : "<c>{} 离开了队列，匹配已取消。", player.getUsername());
            }
        }
    }

    public static void forget(UUID uuid) {
        SEARCHING.remove(uuid);
        Ticket ticket = QUEUED.remove(uuid);
        if (ticket != null) {
            ticket.task().cancel();
            ticket.members().forEach(QUEUED::remove);
        }
    }
}
