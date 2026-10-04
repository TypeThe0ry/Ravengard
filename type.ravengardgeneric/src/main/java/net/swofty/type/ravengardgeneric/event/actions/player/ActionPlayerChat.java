package net.swofty.type.ravengardgeneric.event.actions.player;

import net.kyori.adventure.text.Component;
import net.minestom.server.event.player.PlayerChatEvent;
import net.swofty.type.generic.HypixelGenericLoader;
import net.swofty.type.generic.chat.StaffChat;
import net.swofty.type.generic.data.HypixelDataHandler;
import net.swofty.type.generic.data.datapoints.DatapointChatType;
import net.swofty.type.generic.event.EventNodes;
import net.swofty.type.generic.event.HypixelEventClass;
import net.swofty.type.generic.event.phase.EventPhase;
import net.swofty.type.generic.event.phase.PhasedEvent;
import net.swofty.type.generic.party.PartyManager;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.generic.user.categories.Rank;
import net.swofty.type.ravengardgeneric.chat.CarbonChatBridge;
import net.swofty.type.ravengardgeneric.chat.CarbonChatFormat;
import net.swofty.type.ravengardgeneric.i18n.RavengardLocalization;

import java.util.List;

public class ActionPlayerChat implements HypixelEventClass {

    @PhasedEvent(node = EventNodes.PLAYER, requireDataLoaded = false, phase = EventPhase.GAMEPLAY)
    public void run(PlayerChatEvent event) {
        if (event.isCancelled()) return;
        final HypixelPlayer player = (HypixelPlayer) event.getPlayer();
        event.setCancelled(true);

        HypixelDataHandler dataHandler = player.getDataHandler();
        if (dataHandler == null) return;

        String message = event.getRawMessage();
        Rank rank = player.getRank();

        if (!rank.isStaff() && !RavengardLocalization.isActive() && !CarbonChatBridge.isEnabled())
            message = message.replaceAll("[^\\x00-\\x7F]", "");

        String finalMessage = message;

        DatapointChatType.Chats chatType = player.getChatType().currentChatType;
        if (chatType == DatapointChatType.Chats.STAFF) {
            if (!rank.isStaff()) {
                player.sendMessage("<c>未知的聊天类型。");
                player.getChatType().switchTo(DatapointChatType.Chats.ALL);
                return;
            }
            StaffChat.sendMessage(player, finalMessage);
            return;
        }

        if (chatType == DatapointChatType.Chats.PARTY) {
            if (!PartyManager.isInParty(player)) {
                player.sendMessage("<c>你不在队伍中，已切换到全体频道。");
                player.getChatType().switchTo(DatapointChatType.Chats.ALL);
                return;
            }

            PartyManager.sendChat(player, message);
            return;
        }

        Component line = CarbonChatFormat.line(player.getUsername(), finalMessage);
        RavengardLocalization.verbatim(line);
        List<HypixelPlayer> receivers = HypixelGenericLoader.getLoadedPlayers();
        receivers.forEach(onlinePlayer -> onlinePlayer.sendMessage(line));
        CarbonChatBridge.sendGlobal(player, finalMessage);
    }
}
