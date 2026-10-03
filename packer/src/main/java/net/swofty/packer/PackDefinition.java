package net.swofty.packer;

public interface PackDefinition {
    String getPackName();
    String getPackDirectory();

    /** Directories laid over the pack directory in order; a later file replaces an earlier one at the same path. */
    default java.util.List<String> getOverlayDirectories() {
        return java.util.List.of();
    }
}
