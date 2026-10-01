package net.swofty.type.ravengarddungeon.interactables;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonChestMimicTest {
    @Test
    void mimicRollIsDeterministicAndTierWeighted() {
        String sequence = sequence(90210, "normal");
        assertFalse(sequence.isEmpty());
        assertEquals(sequence, sequence(90210, "normal"));

        int normal = countMimics(44, "normal", 10_000);
        int silver = countMimics(44, "silver", 10_000);
        int gold = countMimics(44, "gold", 10_000);

        assertTrue(normal >= 400 && normal <= 600, "normal=" + normal);
        assertTrue(silver >= 900 && silver <= 1_100, "silver=" + silver);
        assertEquals(0, gold);
    }

    @Test
    void aMimicIsTheChestItWasHidingAs() {
        assertEquals(Set.of("normal_mimic"), typesFor("normal"));
        assertEquals(Set.of("silver_mimic"), typesFor("silver"));
        assertEquals(Set.of(), typesFor("gold"));
    }

    @Test
    void bothMimicsRideTheCapturedSilverMimicRig() throws Exception {
        Path configuration = Path.of("..", "configuration");
        Path mobs = configuration.resolve(Path.of("ravengard", "mobs"));
        Path pack = configuration.resolve(Path.of("resourcepacks", "ravengard"));

        var json = JsonParser.parseString(Files.readString(mobs.resolve("silver_mimic.json"))).getAsJsonObject();
        assertTrue(json.get("health").getAsDouble() > 0);
        assertTrue(json.get("walkSpeed").getAsDouble() > 0);
        assertTrue(json.getAsJsonObject("animations").has("walk"));
        assertTrue(json.getAsJsonObject("animations").has("death"));
        Set<String> rig = new HashSet<>();
        for (var partElement : json.getAsJsonArray("parts")) {
            rig.add(partElement.getAsJsonObject().get("name").getAsString());
            String model = partElement.getAsJsonObject().get("model").getAsString();
            String relative = model.replace("hypixel_ravengard:",
                    "assets/hypixel_ravengard/items/") + ".json";
            assertTrue(Files.isRegularFile(pack.resolve(relative)), relative);
        }
        for (String part : Set.of("bottom", "tongue", "top")) {
            assertTrue(rig.contains(part), part);
            assertTrue(Files.isRegularFile(pack.resolve(Path.of(
                    "assets", "hypixel_ravengard", "items", "entity", "normal_mimic", part + ".json"))), part);
        }
        assertFalse(Files.isRegularFile(mobs.resolve("normal_mimic.json")));
    }

    private static int countMimics(long seed, String tier, int count) {
        Random random = new Random(seed);
        int mimics = 0;
        for (int index = 0; index < count; index++) {
            if (DungeonChest.rollMimic(random, tier) != null) mimics++;
        }
        return mimics;
    }

    private static Set<String> typesFor(String tier) {
        Set<String> types = new HashSet<>();
        Random random = new Random(9917);
        for (int index = 0; index < 100_000; index++) {
            String type = DungeonChest.rollMimic(random, tier);
            if (type != null) types.add(type);
        }
        return types;
    }

    private static String sequence(long seed, String tier) {
        Random random = new Random(seed);
        StringBuilder sequence = new StringBuilder();
        for (int index = 0; index < 1_000; index++) {
            String type = DungeonChest.rollMimic(random, tier);
            if (type != null) sequence.append(index).append(':').append(type).append(';');
        }
        return sequence.toString();
    }
}
