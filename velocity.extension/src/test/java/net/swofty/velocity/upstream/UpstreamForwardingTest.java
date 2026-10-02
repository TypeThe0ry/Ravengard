package net.swofty.velocity.upstream;

import com.velocitypowered.api.util.GameProfile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class UpstreamForwardingTest {
    private static final UUID ID = UUID.fromString("6c2b3f7e-0d1a-4b8e-9f00-1234567890ab");

    @Test
    void aProfileSignedByTheUpstreamProxyIsAccepted() {
        UpstreamForwarding forwarding = new UpstreamForwarding("shared-secret");

        GameProfile profile = forwarding.verify(signed(forwarding, payload()));

        assertNotNull(profile);
        assertEquals(ID, profile.getId());
        assertEquals("TypeTheory", profile.getName());
        assertEquals(1, profile.getProperties().size());
        assertEquals("textures", profile.getProperties().getFirst().getName());
        assertEquals("skin", profile.getProperties().getFirst().getValue());
        assertEquals("sig", profile.getProperties().getFirst().getSignature());
    }

    @Test
    void aProfileSignedWithAnotherSecretIsRejected() {
        UpstreamForwarding forwarding = new UpstreamForwarding("shared-secret");
        UpstreamForwarding stranger = new UpstreamForwarding("other-secret");

        assertNull(forwarding.verify(signed(stranger, payload())));
    }

    @Test
    void aTruncatedProfileIsRejected() {
        UpstreamForwarding forwarding = new UpstreamForwarding("shared-secret");
        byte[] payload = payload();
        byte[] truncated = java.util.Arrays.copyOf(payload, payload.length - 4);

        assertNull(forwarding.verify(signed(forwarding, truncated)));
    }

    private static byte[] signed(UpstreamForwarding signer, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(signer.sign(payload));
        out.writeBytes(payload);
        return out.toByteArray();
    }

    private static byte[] payload() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarInt(out, 1);
        writeString(out, "203.0.113.7");
        writeLong(out, ID.getMostSignificantBits());
        writeLong(out, ID.getLeastSignificantBits());
        writeString(out, "TypeTheory");
        writeVarInt(out, 1);
        writeString(out, "textures");
        writeString(out, "skin");
        out.write(1);
        writeString(out, "sig");
        return out.toByteArray();
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static void writeString(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void writeLong(ByteArrayOutputStream out, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            out.write((int) (value >>> shift) & 0xFF);
        }
    }
}
