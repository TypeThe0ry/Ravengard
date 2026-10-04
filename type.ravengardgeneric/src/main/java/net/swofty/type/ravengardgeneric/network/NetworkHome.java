package net.swofty.type.ravengardgeneric.network;

import net.swofty.type.generic.user.HypixelPlayer;
import org.tinylog.Logger;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * The main city of the network this server sits behind. Ravengard runs behind the network's front
 * proxy, so leaving for the city is a BungeeCord "Connect" message that our own proxy passes on to
 * the front proxy, which owns the city server. Unset {@value #ENV_SERVER} means there is no city.
 */
public final class NetworkHome {
    public static final String ENV_SERVER = "RAVENGARD_HOME_SERVER";
    static final String CHANNEL = "bungeecord:main";

    private static final String SERVER = System.getenv(ENV_SERVER);

    private NetworkHome() {
    }

    public static boolean isEnabled() {
        return SERVER != null && !SERVER.isBlank();
    }

    public static void send(HypixelPlayer player) {
        if (!isEnabled()) {
            player.sendMessage("<c>这个服务器没有可以返回的主城。");
            return;
        }
        player.sendMessage("<7>正在送你回主城……");
        player.sendPluginMessage(CHANNEL, connect(SERVER.trim()));
        Logger.info("Sent {} to the network's main city server '{}'", player.getUsername(), SERVER.trim());
    }

    static byte[] connect(String server) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF(server);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
