package net.swofty.type.ravengardgeneric.sound;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RavengardSoundsTest {
    @Test
    void everyRuntimeCueExistsInTheOfficialPlaytestPack() throws IOException {
        Path pack = Path.of("..", "configuration", "resourcepacks", "ravengard",
                "assets", "hypixel_ravengard");
        JsonObject sounds = JsonParser.parseString(Files.readString(pack.resolve("sounds.json")))
                .getAsJsonObject();

        for (RavengardSounds.Cue cue : RavengardSounds.Cue.values()) {
            assertTrue(sounds.has(cue.key()), () -> "Missing official sound key " + cue.key());
            sounds.getAsJsonObject(cue.key()).getAsJsonArray("sounds").forEach(entry -> {
                String registered = entry.isJsonPrimitive()
                        ? entry.getAsString()
                        : entry.getAsJsonObject().get("name").getAsString();
                String path = registered.substring("hypixel_ravengard:".length());
                assertTrue(Files.isRegularFile(pack.resolve("sounds").resolve(path + ".ogg")),
                        () -> "Missing official sound file for " + cue.key());
            });
        }
    }
}
