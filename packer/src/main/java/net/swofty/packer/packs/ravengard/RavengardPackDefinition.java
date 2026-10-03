package net.swofty.packer.packs.ravengard;

import net.swofty.packer.PackDefinition;

import java.util.List;

/**
 * The official Ravengard pack, optionally with a localization layer merged over it so the
 * client receives a single pack.
 */
public class RavengardPackDefinition implements PackDefinition {
    public static final RavengardPackDefinition INSTANCE = new RavengardPackDefinition(List.of());
    public static final String LOCALE_OVERLAY_DIRECTORY = "configuration/resourcepacks/ravengard-zh_CN";

    private final List<String> overlays;

    private RavengardPackDefinition(List<String> overlays) {
        this.overlays = List.copyOf(overlays);
    }

    public static RavengardPackDefinition withOverlay(String overlayDirectory) {
        return new RavengardPackDefinition(List.of(overlayDirectory));
    }

    @Override
    public String getPackName() {
        return "ravengard";
    }

    @Override
    public String getPackDirectory() {
        return "configuration/resourcepacks/ravengard";
    }

    @Override
    public List<String> getOverlayDirectories() {
        return overlays;
    }
}
