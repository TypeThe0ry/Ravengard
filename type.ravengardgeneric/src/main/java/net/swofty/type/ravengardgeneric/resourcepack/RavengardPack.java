package net.swofty.type.ravengardgeneric.resourcepack;

import net.swofty.commons.config.Settings;
import net.swofty.packer.HypixelPackBuilder;
import net.swofty.packer.packs.ravengard.RavengardPackDefinition;
import net.swofty.type.generic.resourcepack.HypixelResourcePack;
import net.swofty.type.generic.user.HypixelPlayer;
import net.swofty.type.ravengardgeneric.hud.RavengardHud;
import org.tinylog.Logger;
import team.unnamed.creative.BuiltResourcePack;

public class RavengardPack implements HypixelResourcePack {
    private static final String ENV_PACK_ID_FROM = "RAVENGARD_PACK_ID_FROM";
    private static final java.util.regex.Pattern SHA1 =
            java.util.regex.Pattern.compile("\"sha1\"\\s*:\\s*\"([0-9a-fA-F]{40})\"");
    private static volatile RavengardPackDefinition definition = RavengardPackDefinition.INSTANCE;

    private final String packUrl;
    private final String packHash;

    public RavengardPack(String serverUrl, String hash) {
        this.packHash = hash;
        this.packUrl = serverUrl + "/" + hash + ".zip";
    }

    /** Merges a localization layer into the pack, so it must be called before {@link #fromConfig()}. */
    public static void useOverlay(String overlayDirectory) {
        definition = RavengardPackDefinition.withOverlay(overlayDirectory);
    }

    public static RavengardPack fromConfig() {
        RavengardPackDefinition definition = RavengardPack.definition;
        Settings.ResourcePackSettings settings = HypixelResourcePack.getConfigFor(definition.getPackName());

        try {
            Logger.info("Building resource pack '{}' from {} with overlays {}...", definition.getPackName(),
                    definition.getPackDirectory(), definition.getOverlayDirectories());
            HypixelPackBuilder builder = new HypixelPackBuilder(definition);
            BuiltResourcePack built = builder.build();
            Logger.info("Resource pack '{}' built. Hash: {}", definition.getPackName(), built.hash());

            return new RavengardPack(settings.getServerUrl(), built.hash());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed reading configuration/resourcepacks/ravengard-original.zip", exception);
        }
    }

    @Override
    public String getPackName() {
        return definition.getPackName();
    }

    /**
     * Behind a network that ships its own server pack, this pack takes that pack's id: the client
     * then swaps one for the other when a player moves between Ravengard and the rest of the
     * network, instead of stacking both, and moving between the other servers stays reload-free.
     * The id follows the network pack's current SHA-1, which CraftEngine derives it from.
     */
    @Override
    public java.util.UUID getPackId() {
        java.util.UUID shared = sharedPackId(System.getenv(ENV_PACK_ID_FROM));
        return shared != null ? shared : HypixelResourcePack.super.getPackId();
    }

    static java.util.UUID sharedPackId(String source) {
        if (source == null || source.isBlank()) return null;
        try {
            var matcher = SHA1.matcher(java.nio.file.Files.readString(java.nio.file.Path.of(source)));
            if (!matcher.find()) {
                Logger.warn("No sha1 in {}; sending the Ravengard pack under its own id", source);
                return null;
            }
            return java.util.UUID.nameUUIDFromBytes(
                    matcher.group(1).toLowerCase(java.util.Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException exception) {
            Logger.warn("Cannot read {} ({}); sending the Ravengard pack under its own id", source, exception.getMessage());
            return null;
        }
    }

    @Override
    public String getPackUrl() {
        return packUrl;
    }

    @Override
    public String getPackHash() {
        return packHash;
    }

    @Override
    public boolean isRequired() {
        return false;
    }

    @Override
    public void initialize() {
        RavengardHud.start();
        net.swofty.type.ravengardgeneric.classes.RavengardStamina.start();
    }

    @Override
    public void onPlayerJoin(HypixelPlayer player) {

    }

    @Override
    public void onPlayerQuit(HypixelPlayer player) {
        RavengardHud.detach(player);
    }
}
