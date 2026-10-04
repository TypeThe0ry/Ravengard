package net.swofty.type.ravengardgeneric.i18n;

import net.swofty.type.ravengardgeneric.resourcepack.RavengardPack;
import org.tinylog.Logger;

import java.io.File;

/**
 * The Chinese resource pack layer (tooltip tags, button and title textures, glyph advances for the
 * centring fonts, pack name and icon) laid over the official pack so players get a single pack.
 */
public final class RavengardLanguagePack {
    public static final String ENV_DIRECTORY = "RAVENGARD_LOCALE_DIR";

    private RavengardLanguagePack() {
    }

    public static void installFromEnvironment() {
        String directory = System.getenv(ENV_DIRECTORY);
        if (directory == null || directory.isBlank()) {
            Logger.info("No Ravengard language pack layer ({} not set)", ENV_DIRECTORY);
            return;
        }
        File layer = new File(directory, "resourcepack");
        if (!layer.isDirectory()) {
            Logger.warn("Ravengard language pack layer {} does not exist", layer);
            return;
        }
        RavengardPack.useOverlay(layer.getPath());
        Logger.info("Ravengard language pack layer {} is merged into the Ravengard pack", layer);
    }
}
