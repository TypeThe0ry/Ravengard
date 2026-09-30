package net.swofty.packer.packs.ravengard;

import net.swofty.packer.PackDefinition;

/**
 * Font overlay shipped with a Ravengard localization. It is sent below the official pack and
 * only adds glyph advances the localized text needs; the official pack is never modified.
 */
public class RavengardLocalePackDefinition implements PackDefinition {
    public static final RavengardLocalePackDefinition INSTANCE =
            new RavengardLocalePackDefinition("configuration/resourcepacks/ravengard-zh_CN");

    private final String directory;

    public RavengardLocalePackDefinition(String directory) {
        this.directory = directory;
    }

    @Override
    public String getPackName() {
        return "ravengard-zh_CN";
    }

    @Override
    public String getPackDirectory() {
        return directory;
    }
}
