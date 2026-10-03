package net.swofty.type.ravengardgeneric.chat;

import com.github.luben.zstd.Zstd;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The wire format CarbonChat's cross-server messenger publishes on Redis: a compression flag,
 * the sending server, a message id and one packet, whose id is its registration order in Carbon.
 */
public final class CarbonChatProtocol {
    public static final String CHANNEL = "carbon-data";
    public static final byte PACKET_VERSION = 1;

    public static final int MULTI = 1;
    public static final int KEEP_ALIVE = 2;
    public static final int INITIALIZATION = 3;
    public static final int PACKET_VERSION_REPLY = 4;
    public static final int PACKET_VERSION_REQUEST = 5;
    public static final int CHAT_MESSAGE = 7;
    public static final int LOCAL_PLAYERS = 9;
    public static final int LOCAL_PLAYER_CHANGE = 10;
    public static final int WHISPER = 11;

    private static final int MAX_STRING_BYTES = 32767;

    private CarbonChatProtocol() {
    }

    public record Packet(int id, byte[] payload) {
    }

    public record Frame(UUID server, List<Packet> packets) {
    }

    public record ChatMessage(UUID userId, String channelKey, String username, String componentJson) {
    }

    public record PlayerChange(UUID playerId, boolean added, String playerName) {
    }

    public record Whisper(UUID from, UUID to, String componentJson) {
    }

    public static byte[] frame(UUID server, UUID messageId, int packetId, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 34);
        out.write(0);
        writeUuid(out, server);
        writeUuid(out, messageId);
        out.write(packetId);
        out.writeBytes(payload);
        return out.toByteArray();
    }

    public static Frame decode(byte[] body) {
        ByteBuffer data = ByteBuffer.wrap(inflate(body));
        UUID server = readUuid(data);
        readUuid(data);
        int id = Byte.toUnsignedInt(data.get());
        byte[] payload = remaining(data);
        List<Packet> packets = new ArrayList<>();
        if (id == MULTI) {
            ByteBuffer multi = ByteBuffer.wrap(payload);
            while (multi.hasRemaining()) {
                int next = Byte.toUnsignedInt(multi.get());
                if (next == 0) break;
                byte[] part = new byte[multi.getInt()];
                multi.get(part);
                packets.add(new Packet(next, part));
            }
        } else {
            packets.add(new Packet(id, payload));
        }
        return new Frame(server, packets);
    }

    public static byte[] initialization(UUID server) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeUuid(out, server);
        out.write(PACKET_VERSION);
        return out.toByteArray();
    }

    public static byte[] keepAlive(UUID server) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeUuid(out, server);
        return out.toByteArray();
    }

    public static byte[] packetVersion(UUID intendedRecipient, UUID server) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeUuid(out, intendedRecipient);
        writeUuid(out, server);
        out.write(PACKET_VERSION);
        return out.toByteArray();
    }

    /** Returns the server asking, when the request is addressed to {@code self}. */
    public static UUID versionRequester(byte[] payload, UUID self) {
        ByteBuffer data = ByteBuffer.wrap(payload);
        UUID recipient = readUuid(data);
        UUID requester = readUuid(data);
        return recipient.equals(self) ? requester : null;
    }

    public static byte[] chat(ChatMessage message) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeUuid(out, message.userId());
        writeString(out, message.channelKey());
        writeString(out, message.username());
        writeString(out, message.componentJson());
        return out.toByteArray();
    }

    public static ChatMessage readChat(byte[] payload) {
        ByteBuffer data = ByteBuffer.wrap(payload);
        UUID userId = readUuid(data);
        String channel = readString(data);
        String username = readString(data);
        String component = readString(data);
        if (data.hasRemaining()) {
            throw new IllegalArgumentException(data.remaining() + " bytes left after a chat message");
        }
        return new ChatMessage(userId, channel, username, component);
    }

    public static byte[] localPlayers(java.util.Map<UUID, String> players) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarInt(out, players.size());
        players.forEach((id, name) -> {
            writeUuid(out, id);
            writeString(out, name);
        });
        return out.toByteArray();
    }

    public static java.util.Map<UUID, String> readLocalPlayers(byte[] payload) {
        ByteBuffer data = ByteBuffer.wrap(payload);
        int size = readVarInt(data);
        java.util.Map<UUID, String> players = new java.util.HashMap<>();
        for (int i = 0; i < size; i++) {
            players.put(readUuid(data), readString(data));
        }
        return players;
    }

    public static byte[] playerChange(PlayerChange change) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeUuid(out, change.playerId());
        writeString(out, change.added() ? "ADD" : "REMOVE");
        if (change.added()) {
            writeString(out, change.playerName());
        }
        return out.toByteArray();
    }

    public static PlayerChange readPlayerChange(byte[] payload) {
        ByteBuffer data = ByteBuffer.wrap(payload);
        UUID id = readUuid(data);
        boolean added = "ADD".equals(readString(data));
        return new PlayerChange(id, added, added ? readString(data) : null);
    }

    public static byte[] whisper(Whisper whisper) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeUuid(out, whisper.from());
        writeUuid(out, whisper.to());
        writeString(out, whisper.componentJson());
        return out.toByteArray();
    }

    public static Whisper readWhisper(byte[] payload) {
        ByteBuffer data = ByteBuffer.wrap(payload);
        return new Whisper(readUuid(data), readUuid(data), readString(data));
    }

    private static byte[] inflate(byte[] body) {
        if (body.length == 0) {
            throw new IllegalArgumentException("Empty frame");
        }
        if (body[0] == 0) {
            byte[] raw = new byte[body.length - 1];
            System.arraycopy(body, 1, raw, 0, raw.length);
            return raw;
        }
        ByteBuffer header = ByteBuffer.wrap(body, 1, 4);
        int size = header.getInt();
        byte[] compressed = new byte[body.length - 5];
        System.arraycopy(body, 5, compressed, 0, compressed.length);
        return Zstd.decompress(compressed, size);
    }

    private static byte[] remaining(ByteBuffer data) {
        byte[] rest = new byte[data.remaining()];
        data.get(rest);
        return rest;
    }

    private static void writeUuid(ByteArrayOutputStream out, UUID value) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits());
        out.writeBytes(buffer.array());
    }

    private static UUID readUuid(ByteBuffer data) {
        return new UUID(data.getLong(), data.getLong());
    }

    private static void writeString(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("String too long for CarbonChat: " + bytes.length + " bytes");
        }
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        do {
            int part = value & 0x7F;
            value >>>= 7;
            out.write(value != 0 ? part | 0x80 : part);
        } while (value != 0);
    }

    private static int readVarInt(ByteBuffer data) {
        int value = 0;
        int shift = 0;
        byte current;
        do {
            current = data.get();
            value |= (current & 0x7F) << shift;
            shift += 7;
            if (shift > 35) throw new IllegalArgumentException("VarInt too big");
        } while ((current & 0x80) != 0);
        return value;
    }

    private static String readString(ByteBuffer data) {
        int length = readVarInt(data);
        if (length > MAX_STRING_BYTES || length > data.remaining()) {
            throw new IllegalArgumentException("Bad string length " + length);
        }
        byte[] bytes = new byte[length];
        data.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
