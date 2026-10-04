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
     * Fonts and languages combine across stacked packs instead of replacing one another: upper
     * font providers win over lower ones and upper languages override single keys. A character
     * the layer draws is taken out of the lower providers outright, so no client or font mod can
     * pick the lower glyph by loading providers in another order. A layer's font references go
     * below the existing providers, so a pixel font falls back to the referenced font only for
     * characters it does not draw itself.
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
            java.util.Set<Integer> redrawn = drawnCharacters(added);
            for (com.google.gson.JsonElement provider : lower.getAsJsonArray("providers")) {
                com.google.gson.JsonElement kept = withoutCharacters(provider, redrawn);
                if (kept != null) providers.add(kept);
            }
            added.forEach(provider -> {
                if (!isReference(provider)) providers.add(provider);
            });
            lower.add("providers", providers);
        } else {
            upper.entrySet().forEach(entry -> lower.add(entry.getKey(), entry.getValue()));
        }
        return lower.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static java.util.Set<Integer> drawnCharacters(JsonArray providers) {
        java.util.Set<Integer> drawn = new java.util.HashSet<>();
        for (com.google.gson.JsonElement element : providers) {
            if (!element.isJsonObject()) continue;
            JsonObject provider = element.getAsJsonObject();
            if (provider.has("chars")) {
                provider.getAsJsonArray("chars").forEach(row -> row.getAsString().codePoints()
                        .filter(codePoint -> codePoint != 0).forEach(drawn::add));
            }
            if (provider.has("advances")) {
                provider.getAsJsonObject("advances").keySet()
                        .forEach(key -> key.codePoints().forEach(drawn::add));
            }
        }
        return drawn;
    }

    /** The provider with the given characters blanked out, or null when nothing of it is left. */
    private static com.google.gson.JsonElement withoutCharacters(com.google.gson.JsonElement element,
                                                                 java.util.Set<Integer> removed) {
        if (removed.isEmpty() || !element.isJsonObject()) return element;
        JsonObject provider = element.getAsJsonObject().deepCopy();
        if (provider.has("chars")) {
            JsonArray rows = new JsonArray();
            boolean anyLeft = false;
            for (com.google.gson.JsonElement row : provider.getAsJsonArray("chars")) {
                StringBuilder kept = new StringBuilder();
                for (int codePoint : row.getAsString().codePoints().toArray()) {
                    boolean blank = codePoint == 0 || removed.contains(codePoint);
                    kept.appendCodePoint(blank ? 0 : codePoint);
                    anyLeft |= !blank;
                }
                rows.add(kept.toString());
            }
            if (!anyLeft) return null;
            provider.add("chars", rows);
        }
        if (provider.has("advances")) {
            JsonObject advances = provider.getAsJsonObject("advances");
            advances.keySet().stream()
                    .filter(key -> key.codePoints().anyMatch(removed::contains))
                    .toList()
                    .forEach(advances::remove);
            if (advances.size() == 0) return null;
        }
        return provider;
    }

    private static boolean isReference(com.google.gson.JsonElement provider) {
        return provider.isJsonObject() && provider.getAsJsonObject().has("type")
                && "reference".equals(provider.getAsJsonObject().get("type").getAsString());
    }

}
