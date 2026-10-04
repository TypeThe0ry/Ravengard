package net.swofty.packer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import team.unnamed.creative.BuiltResourcePack;
import team.unnamed.creative.base.Writable;
import team.unnamed.creative.metadata.pack.FormatVersion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class HypixelPackBuilder {
    private static final FormatVersion FORMAT_VERSION = FormatVersion.of(FormatVersion.FORMAT_26_1);

    private final PackDefinition definition;

    public HypixelPackBuilder(PackDefinition definition) {
        this.definition = definition;
    }

    public BuiltResourcePack build() {
        Path packDirectory = Path.of(definition.getPackDirectory()).toAbsolutePath();

        if (!Files.isDirectory(packDirectory)) {
            throw new IllegalStateException(
                    "Pack directory does not exist: " + packDirectory
            );
        }

        try {
            byte[] bytes = zipLayers(packDirectory);

            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            String hash = HexFormat.of().formatHex(digest.digest(bytes));

            return BuiltResourcePack.of(
                    Writable.bytes(bytes),
                    hash
            );
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build resource pack", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is unavailable", e);
        }
    }

    private byte[] zipLayers(Path packDirectory) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        collect(packDirectory, entries);
        for (String overlay : definition.getOverlayDirectories()) {
            Path overlayDirectory = Path.of(overlay).toAbsolutePath();
            if (!Files.isDirectory(overlayDirectory)) {
                throw new IllegalStateException("Pack overlay directory does not exist: " + overlayDirectory);
            }
            collect(overlayDirectory, entries);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> file : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(0);
                zip.putNextEntry(entry);
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static void collect(Path directory, Map<String, byte[]> entries) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String name = directory.relativize(path).toString().replace('\\', '/');
                byte[] layer = Files.readAllBytes(path);
                byte[] below = entries.get(name);
                entries.put(name, below == null ? layer : stack(name, below, layer));
            }
        }
    }

    /**
     * Fonts and languages combine across stacked packs instead of replacing one another: the
     * client concatenates font providers bottom to top and lets later ones win, and lets upper
     * languages override single keys. Merging those files keeps one pack identical to the stack.
     * A layer's font references are the exception: they go below the existing providers, so a
     * pixel font falls back to the referenced font only for characters it does not draw itself.
     */
    static byte[] stack(String name, byte[] below, byte[] above) {
        boolean font = name.matches("assets/[^/]+/font/.+\\.json");
        boolean language = name.matches("assets/[^/]+/lang/[^/]+\\.json");
        if (!font && !language) return above;
        JsonObject lower = JsonParser.parseString(new String(below, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject upper = JsonParser.parseString(new String(above, StandardCharsets.UTF_8)).getAsJsonObject();
        if (font) {
            JsonArray providers = new JsonArray();
            JsonArray added = upper.getAsJsonArray("providers");
            added.forEach(provider -> {
                if (isReference(provider)) providers.add(provider);
            });
            providers.addAll(lower.getAsJsonArray("providers"));
            added.forEach(provider -> {
                if (!isReference(provider)) providers.add(provider);
            });
            lower.add("providers", providers);
        } else {
            upper.entrySet().forEach(entry -> lower.add(entry.getKey(), entry.getValue()));
        }
        return lower.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static boolean isReference(com.google.gson.JsonElement provider) {
        return provider.isJsonObject() && provider.getAsJsonObject().has("type")
                && "reference".equals(provider.getAsJsonObject().get("type").getAsString());
    }

}
