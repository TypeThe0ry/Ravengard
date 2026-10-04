package net.swofty.type.ravengardgeneric.network;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkHomeTest {
    @Test
    void theCityRequestIsABungeeCordConnectToTheNamedServer() throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(NetworkHome.connect("spawn")));

        assertEquals("Connect", in.readUTF());
        assertEquals("spawn", in.readUTF());
        assertEquals(-1, in.read());
        assertEquals("bungeecord:main", NetworkHome.CHANNEL);
    }
}
