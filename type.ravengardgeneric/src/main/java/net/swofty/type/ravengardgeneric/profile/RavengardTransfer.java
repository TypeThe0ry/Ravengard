package net.swofty.type.ravengardgeneric.profile;

import net.minestom.server.MinecraftServer;
import net.swofty.proxyapi.ProxyPlayer;
import net.swofty.type.generic.gui.v2.ViewNavigator;
import net.swofty.type.ravengardgeneric.user.RavengardPlayer;

import java.util.function.Consumer;

/**
 * A transfer saves the profile before the player disconnects, so whatever has to be settled in the
 * inventory first -- items held by an open view, an abandoned run -- is settled here.
 */
public final class RavengardTransfer {
    private RavengardTransfer() {
    }

    public static void install(Consumer<RavengardPlayer> beforeLeave) {
        var preparation = ProxyPlayer.getTransferPreparation();
        ProxyPlayer.setTransferPreparation((playerUuid, targetServer) -> {
            if (MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerUuid)
                    instanceof RavengardPlayer player) {
                ViewNavigator.remove(player);
                beforeLeave.accept(player);
            }
            return preparation.apply(playerUuid, targetServer);
        });
    }
}
