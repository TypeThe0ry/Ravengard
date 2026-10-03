package net.swofty.type.ravengardgeneric.entity.animation;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavengardQueueNpcTest {
    private static final Path CLIPS = Path.of("..", "configuration", "ravengard", "npc_animations");

    @Test
    void theQueueKnightJoinsTheFightQueue() throws IOException {
        assertTrue(clip("queue").joinsQueue());
    }

    @Test
    void otherTownNpcsKeepTheirDialogueAndShops() throws IOException {
        for (String name : new String[]{"alchemist", "tavern_master", "harbor_master", "ravenport_guard", "villager_3"}) {
            assertFalse(clip(name).joinsQueue(), name);
        }
    }

    private static RavengardAnimationClip clip(String name) throws IOException {
        return new Gson().fromJson(Files.readString(CLIPS.resolve(name + ".json"), StandardCharsets.UTF_8),
                RavengardAnimationClip.class);
    }
}
