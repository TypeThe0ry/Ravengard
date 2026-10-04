package net.swofty.type.ravengardgeneric.resourcepack;

import net.swofty.packer.HypixelPackBuilder;
import net.swofty.packer.PackDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RavengardPackTest {
    @TempDir
    Path temp;

    @Test
    void theLocalizationLayerReplacesOfficialFilesInOneDeterministicPack() throws Exception {
        Path base = temp.resolve("official");
        write(base.resolve("pack.mcmeta"), "official");
        write(base.resolve("assets/a.json"), "a");
        List<Path> overlays = List.of(temp.resolve("game/resourcepack"), temp.resolve("packserver/ravengard-zh_CN"));
        for (Path overlay : overlays) {
            write(overlay.resolve("pack.mcmeta"), "ellan");
            write(overlay.resolve("assets/b.json"), "b");
        }

        var onGameServer = new HypixelPackBuilder(layered(base, overlays.get(0))).build();
        var onPackServer = new HypixelPackBuilder(layered(base, overlays.get(1))).build();

        assertEquals(onGameServer.hash(), onPackServer.hash());
        assertEquals(Map.of("assets/a.json", "a", "assets/b.json", "b", "pack.mcmeta", "ellan"),
                entries(onGameServer.data().toByteArray()));
    }

    @Test
    void stackedFontsAndLanguagesCombineLikeSeparatePacksWouldWithReferencesAsFallbacks() throws Exception {
        Path base = temp.resolve("official");
        Path overlay = temp.resolve("overlay");
        write(base.resolve("assets/ns/font/-full.json"), "{\"providers\":[{\"type\":\"space\",\"advances\":{\"a\":-8}}]}");
        write(overlay.resolve("assets/ns/font/-full.json"), "{\"providers\":[{\"type\":\"space\",\"advances\":{\"b\":-9}},"
                + "{\"type\":\"reference\",\"id\":\"minecraft:default\"}]}");
        write(base.resolve("assets/minecraft/lang/zh_cn.json"), "{\"kept\":\"official\",\"shared\":\"official\"}");
        write(overlay.resolve("assets/minecraft/lang/zh_cn.json"), "{\"shared\":\"translated\",\"added\":\"translated\"}");
        write(base.resolve("assets/ns/textures/logo.png"), "official");
        write(overlay.resolve("assets/ns/textures/logo.png"), "translated");

        Map<String, String> entries = entries(new HypixelPackBuilder(layered(base, overlay)).build().data().toByteArray());

        assertEquals("{\"providers\":[{\"type\":\"reference\",\"id\":\"minecraft:default\"},"
                + "{\"type\":\"space\",\"advances\":{\"a\":-8}},"
                + "{\"type\":\"space\",\"advances\":{\"b\":-9}}]}", entries.get("assets/ns/font/-full.json"));
        assertEquals("{\"kept\":\"official\",\"shared\":\"translated\",\"added\":\"translated\"}",
                entries.get("assets/minecraft/lang/zh_cn.json"));
        assertEquals("translated", entries.get("assets/ns/textures/logo.png"));
    }

    @Test
    void theSharedIdIsTheOneCraftEngineDerivesFromTheNetworkPackSha1() throws Exception {
        String sha1 = "69284ef9cbf43264457434a9d5b4b43fa0ba953a";
        Path cache = temp.resolve("gitlab_default.json");
        Files.writeString(cache, "{\"sha1\":\"" + sha1 + "\",\"url\":\"https://example.invalid/pack.zip\"}");

        assertEquals(UUID.nameUUIDFromBytes(sha1.getBytes(StandardCharsets.UTF_8)),
                RavengardPack.sharedPackId(cache.toString()));
        assertNull(RavengardPack.sharedPackId(temp.resolve("missing.json").toString()));
        assertNull(RavengardPack.sharedPackId(null));
    }

    private static PackDefinition layered(Path base, Path overlay) {
        return new PackDefinition() {
            @Override
            public String getPackName() {
                return "ravengard";
            }

            @Override
            public String getPackDirectory() {
                return base.toString();
            }

            @Override
            public List<String> getOverlayDirectories() {
                return List.of(overlay.toString());
            }
        };
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private static Map<String, String> entries(byte[] zip) throws Exception {
        Map<String, String> entries = new TreeMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
