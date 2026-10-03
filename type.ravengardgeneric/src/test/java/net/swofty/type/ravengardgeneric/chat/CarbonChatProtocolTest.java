package net.swofty.type.ravengardgeneric.chat;

import com.github.luben.zstd.Zstd;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CarbonChatProtocolTest {
    private static final UUID SERVER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID PLAYER = UUID.fromString("5bf97f5c-77ce-331b-a188-c3723097e7ec");

    @Test
    void aChatMessageIsLaidOutLikeCarbonsChatMessagePacket() {
        byte[] payload = CarbonChatProtocol.chat(new CarbonChatProtocol.ChatMessage(
                PLAYER, "carbon:global", "TypeThe0ry", "{\"text\":\"你好\"}"));

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.writeBytes(uuid(PLAYER));
        expected.writeBytes(carbonString("carbon:global"));
        expected.writeBytes(carbonString("TypeThe0ry"));
        expected.writeBytes(carbonString("{\"text\":\"你好\"}"));
        assertArrayEquals(expected.toByteArray(), payload);

        CarbonChatProtocol.ChatMessage read = CarbonChatProtocol.readChat(payload);
        assertEquals(PLAYER, read.userId());
        assertEquals("carbon:global", read.channelKey());
        assertEquals("TypeThe0ry", read.username());
        assertEquals("{\"text\":\"你好\"}", read.componentJson());
    }

    @Test
    void aFrameCarriesTheServerTheMessageIdAndThePacketId() {
        UUID messageId = UUID.randomUUID();
        byte[] frame = CarbonChatProtocol.frame(SERVER, messageId, CarbonChatProtocol.KEEP_ALIVE,
                CarbonChatProtocol.keepAlive(SERVER));

        ByteBuffer data = ByteBuffer.wrap(frame);
        assertEquals(0, data.get());
        assertEquals(SERVER, new UUID(data.getLong(), data.getLong()));
        assertEquals(messageId, new UUID(data.getLong(), data.getLong()));
        assertEquals(CarbonChatProtocol.KEEP_ALIVE, data.get());
        assertEquals(SERVER, new UUID(data.getLong(), data.getLong()));
        assertEquals(0, data.remaining());

        CarbonChatProtocol.Frame decoded = CarbonChatProtocol.decode(frame);
        assertEquals(SERVER, decoded.server());
        assertEquals(1, decoded.packets().size());
        assertEquals(CarbonChatProtocol.KEEP_ALIVE, decoded.packets().getFirst().id());
    }

    @Test
    void aCompressedMultiPacketFromCarbonIsUnpacked() {
        byte[] chat = CarbonChatProtocol.chat(new CarbonChatProtocol.ChatMessage(
                PLAYER, "carbon:global", "TypeThe0ry", "{\"text\":\"" + "a".repeat(400) + "\"}"));
        byte[] version = CarbonChatProtocol.packetVersion(UUID.randomUUID(), SERVER);
        ByteArrayOutputStream multi = new ByteArrayOutputStream();
        for (Object[] part : new Object[][]{{CarbonChatProtocol.PACKET_VERSION_REPLY, version}, {CarbonChatProtocol.CHAT_MESSAGE, chat}}) {
            byte[] body = (byte[]) part[1];
            multi.write((int) part[0]);
            multi.writeBytes(ByteBuffer.allocate(4).putInt(body.length).array());
            multi.writeBytes(body);
        }
        multi.write(0);
        byte[] raw = CarbonChatProtocol.frame(SERVER, UUID.randomUUID(), CarbonChatProtocol.MULTI, multi.toByteArray());
        byte[] uncompressed = new byte[raw.length - 1];
        System.arraycopy(raw, 1, uncompressed, 0, uncompressed.length);
        byte[] compressed = Zstd.compress(uncompressed, 9);
        ByteBuffer wire = ByteBuffer.allocate(compressed.length + 5);
        wire.put((byte) 1).putInt(uncompressed.length).put(compressed);

        CarbonChatProtocol.Frame decoded = CarbonChatProtocol.decode(wire.array());

        assertEquals(2, decoded.packets().size());
        assertEquals(CarbonChatProtocol.PACKET_VERSION_REPLY, decoded.packets().get(0).id());
        assertEquals(CarbonChatProtocol.CHAT_MESSAGE, decoded.packets().get(1).id());
        assertEquals("TypeThe0ry", CarbonChatProtocol.readChat(decoded.packets().get(1).payload()).username());
    }

    @Test
    void onlyVersionRequestsAddressedToUsAreAnswered() {
        UUID self = UUID.randomUUID();
        UUID asking = UUID.randomUUID();
        ByteArrayOutputStream toUs = new ByteArrayOutputStream();
        toUs.writeBytes(uuid(self));
        toUs.writeBytes(uuid(asking));
        ByteArrayOutputStream toOthers = new ByteArrayOutputStream();
        toOthers.writeBytes(uuid(UUID.randomUUID()));
        toOthers.writeBytes(uuid(asking));

        assertEquals(asking, CarbonChatProtocol.versionRequester(toUs.toByteArray(), self));
        assertNull(CarbonChatProtocol.versionRequester(toOthers.toByteArray(), self));
    }

    @Test
    void playerListsAndChangesMatchCarbonsLayout() {
        UUID other = UUID.fromString("0000000a-0000-0000-0000-00000000000b");
        java.util.Map<UUID, String> players = new java.util.LinkedHashMap<>();
        players.put(PLAYER, "TypeThe0ry");
        players.put(other, "zxz");
        byte[] list = CarbonChatProtocol.localPlayers(players);

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.write(2);
        expected.writeBytes(uuid(PLAYER));
        expected.writeBytes(carbonString("TypeThe0ry"));
        expected.writeBytes(uuid(other));
        expected.writeBytes(carbonString("zxz"));
        assertArrayEquals(expected.toByteArray(), list);
        assertEquals(players, CarbonChatProtocol.readLocalPlayers(list));

        byte[] added = CarbonChatProtocol.playerChange(new CarbonChatProtocol.PlayerChange(PLAYER, true, "TypeThe0ry"));
        ByteArrayOutputStream addLayout = new ByteArrayOutputStream();
        addLayout.writeBytes(uuid(PLAYER));
        addLayout.writeBytes(carbonString("ADD"));
        addLayout.writeBytes(carbonString("TypeThe0ry"));
        assertArrayEquals(addLayout.toByteArray(), added);

        byte[] removed = CarbonChatProtocol.playerChange(new CarbonChatProtocol.PlayerChange(PLAYER, false, "TypeThe0ry"));
        ByteArrayOutputStream removeLayout = new ByteArrayOutputStream();
        removeLayout.writeBytes(uuid(PLAYER));
        removeLayout.writeBytes(carbonString("REMOVE"));
        assertArrayEquals(removeLayout.toByteArray(), removed);
        assertEquals(false, CarbonChatProtocol.readPlayerChange(removed).added());
    }

    @Test
    void aWhisperCarriesBothPlayersAndTheMessage() {
        UUID to = UUID.randomUUID();
        byte[] payload = CarbonChatProtocol.whisper(new CarbonChatProtocol.Whisper(PLAYER, to, "{\"text\":\"在吗\"}"));

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.writeBytes(uuid(PLAYER));
        expected.writeBytes(uuid(to));
        expected.writeBytes(carbonString("{\"text\":\"在吗\"}"));
        assertArrayEquals(expected.toByteArray(), payload);
        CarbonChatProtocol.Whisper read = CarbonChatProtocol.readWhisper(payload);
        assertEquals(to, read.to());
        assertEquals("{\"text\":\"在吗\"}", read.componentJson());
    }

    private static byte[] uuid(UUID value) {
        return ByteBuffer.allocate(16).putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()).array();
    }

    private static byte[] carbonString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int length = bytes.length;
        do {
            int part = length & 0x7F;
            length >>>= 7;
            out.write(length != 0 ? part | 0x80 : part);
        } while (length != 0);
        out.writeBytes(bytes);
        return out.toByteArray();
    }
}
