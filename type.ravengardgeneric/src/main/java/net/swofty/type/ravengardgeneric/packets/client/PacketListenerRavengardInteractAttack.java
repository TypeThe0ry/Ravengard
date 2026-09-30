package net.swofty.type.ravengardgeneric.packets.client;

import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientInteractEntityPacket;
import net.swofty.type.generic.packet.HypixelPacketClientListener;
import net.swofty.type.generic.user.HypixelPlayer;
import org.tinylog.Logger;

public final class PacketListenerRavengardInteractAttack extends HypixelPacketClientListener {
    @Override
    public Class<? extends ClientPacket> getPacket() {
        return ClientInteractEntityPacket.class;
    }

    @Override
    public void run(PlayerPacketEvent event, ClientPacket packet, HypixelPlayer player) {
        Logger.debug("Ravengard interact packet observed: packet={} player={}",
                packet.getClass().getSimpleName(), player.getClass().getSimpleName());

    }

    @Override
    public boolean overrideMinestomProcessing() {
        return false;
    }
}
