package net.swofty.type.ravengardgeneric.packets.client;

import net.minestom.server.entity.Entity;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.listener.UseEntityListener;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientAttackPacket;
import net.swofty.type.generic.packet.HypixelPacketClientListener;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.ravengardgeneric.entity.mob.RavengardMob;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;
import org.tinylog.Logger;

public final class PacketListenerRavengardCombat extends HypixelPacketClientListener {
    public static void bridge(RavengardPlayer ravengardPlayer, int targetId) {
        if (ravengardPlayer.getInstance() == null) {
            return;
        }
        Entity target = ravengardPlayer.getInstance().getEntityById(targetId);
        if (target == null) {
            return;
        }

        Logger.debug("Ravengard combat packet: player={} target={} entityType={} mob={}",
                ravengardPlayer.getUsername(), target.getEntityId(), target.getEntityType(),
                RavengardMob.combatTarget(target) != null);
        UseEntityListener.attackEntityListener(new ClientAttackPacket(targetId), ravengardPlayer);
    }

    @Override
    public Class<? extends ClientPacket> getPacket() {
        return ClientAttackPacket.class;
    }

    @Override
    public void run(PlayerPacketEvent event, ClientPacket packet, HypixelPlayer player) {
        Logger.debug("Ravengard attack packet observed: packet={} player={}",
                packet.getClass().getSimpleName(), player.getClass().getSimpleName());
        if (!(packet instanceof ClientAttackPacket attack)
                || !(player instanceof RavengardPlayer ravengardPlayer)
                ) {
            return;
        }
        bridge(ravengardPlayer, attack.targetId());
    }

    @Override
    public boolean overrideMinestomProcessing() {
        return true;
    }
}
