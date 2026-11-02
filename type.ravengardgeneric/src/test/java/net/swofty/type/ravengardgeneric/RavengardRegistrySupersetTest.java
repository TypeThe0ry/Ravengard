package net.swofty.type.ravengardgeneric;

import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.registry.RegistryKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RavengardRegistrySupersetTest {
    @BeforeAll
    static void initializeRegistrySuperset() throws Exception {
        MinecraftServer.init();
        Path worldDirectory = Path.of("../configuration/world");
        net.swofty.type.generic.world.HypixelWorldLoader.registerCustomBiomes(
                worldDirectory.resolve("hypixel_ravengard_lobby.polar"));
        RavengardGenericLoader.registerRegistrySuperset(
                worldDirectory.resolve("hypixel_ravengard_lobby.polar"),
                worldDirectory.resolve("hypixel_ravengard_tutorial.polar"),
                Path.of("../configuration/ravengard/dungeon_1.polar"));
    }

    @AfterAll
    static void stopMinestom() {
        MinecraftServer.stopCleanly();
    }

    @Test
    void lobbyRegistryContainsDungeonBiome() {
        assertNotEquals(-1, MinecraftServer.getBiomeRegistry()
                .getId(RegistryKey.of("hypixel:sewer")));
    }

    @Test
    void lobbyRegistryContainsDungeonDimension() {
        assertNotNull(MinecraftServer.getDimensionTypeRegistry()
                .getKey(Key.key("ravengard:dungeon")));
    }
}
