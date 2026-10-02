package net.swofty.velocity.upstream;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.LoginPhaseConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.util.GameProfile;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class UpstreamForwarding {
    public static final String SECRET_ENVIRONMENT = "UPSTREAM_FORWARDING_SECRET";
    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("velocity", "player_info");
    private static final int MODERN_DEFAULT = 1;
    private static final int SIGNATURE_LENGTH = 32;

    private final byte[] secret;
    private final Map<InboundConnection, GameProfile> forwarded = Collections.synchronizedMap(new WeakHashMap<>());
    private final Set<UUID> verified = ConcurrentHashMap.newKeySet();

    public UpstreamForwarding(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public static @Nullable UpstreamForwarding fromEnvironment() {
        String secret = System.getenv(SECRET_ENVIRONMENT);
        return secret == null || secret.isBlank() ? null : new UpstreamForwarding(secret.strip());
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        if (!(event.getConnection() instanceof LoginPhaseConnection connection)) {
            return;
        }
        connection.sendLoginPluginMessage(CHANNEL, new byte[]{MODERN_DEFAULT}, response -> {
            GameProfile profile = response == null ? null : verify(response);
            if (profile != null) {
                forwarded.put(connection, profile);
            }
        });
    }

    @Subscribe
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        GameProfile profile = forwarded.remove(event.getConnection());
        if (profile != null) {
            event.setGameProfile(profile);
            verified.add(profile.getId());
        }
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (!verified.remove(event.getPlayer().getUniqueId())) {
            event.setResult(ResultedEvent.ComponentResult.denied(
                Component.text("This server only accepts connections through the network proxy.")));
        }
    }

    @Nullable GameProfile verify(byte[] response) {
        if (response.length <= SIGNATURE_LENGTH) {
            return null;
        }
        byte[] signature = Arrays.copyOfRange(response, 0, SIGNATURE_LENGTH);
        byte[] data = Arrays.copyOfRange(response, SIGNATURE_LENGTH, response.length);
        if (!MessageDigest.isEqual(signature, sign(data))) {
            return null;
        }
        try {
            ForwardingReader reader = new ForwardingReader(data);
            if (reader.readVarInt() < MODERN_DEFAULT) {
                return null;
            }
            reader.readString();
            UUID id = new UUID(reader.readLong(), reader.readLong());
            String name = reader.readString();
            int count = reader.readVarInt();
            List<GameProfile.Property> properties = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String propertyName = reader.readString();
                String value = reader.readString();
                String propertySignature = reader.readBoolean() ? reader.readString() : "";
                properties.add(new GameProfile.Property(propertyName, value, propertySignature));
            }
            return new GameProfile(id, name, properties);
        } catch (IndexOutOfBoundsException malformed) {
            return null;
        }
    }

    byte[] sign(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class ForwardingReader {
        private final byte[] data;
        private int position;

        private ForwardingReader(byte[] data) {
            this.data = data;
        }

        private int readVarInt() {
            int value = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                byte current = data[position++];
                value |= (current & 0x7F) << shift;
                if ((current & 0x80) == 0) {
                    return value;
                }
            }
            throw new IndexOutOfBoundsException("VarInt too long");
        }

        private String readString() {
            int length = readVarInt();
            if (length < 0 || position + length > data.length) {
                throw new IndexOutOfBoundsException("String length " + length);
            }
            String value = new String(data, position, length, StandardCharsets.UTF_8);
            position += length;
            return value;
        }

        private long readLong() {
            long value = 0;
            for (int i = 0; i < Long.BYTES; i++) {
                value = (value << 8) | (data[position++] & 0xFF);
            }
            return value;
        }

        private boolean readBoolean() {
            return data[position++] != 0;
        }
    }
}
