package net.swofty.type.ravengarddungeon.world;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.SharedInstance;
import net.swofty.type.generic.world.HypixelWorldLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Parts of the dungeon map were captured without their props; the props restored for them are
 * kept beside the map and loaded with it.
 */
public final class RestoredDisplays {
    private static final String SUFFIX = ".restored_displays.nbt";

    private RestoredDisplays() {
    }

    public static SharedInstance loadMap(Path polar, InstanceContainer container) throws IOException {
        SharedInstance instance = HypixelWorldLoader.loadFrom(polar, container, MinecraftServer.getInstanceManager());
        Path restored = besideMap(polar);
        if (Files.isRegularFile(restored)) {
            HypixelWorldLoader.loadDisplayOverlay(restored, instance);
        }
        return instance;
    }

    static Path besideMap(Path polar) {
        String name = polar.getFileName().toString();
        int extension = name.lastIndexOf('.');
        return polar.resolveSibling((extension < 0 ? name : name.substring(0, extension)) + SUFFIX);
    }
}
