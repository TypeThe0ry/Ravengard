package net.swofty.type.ravengardgeneric.hud;

import net.kyori.adventure.text.Component;

/**
 * Ravengard draws its own minimap, so it asks client-side minimap mods to step aside. Xaero's
 * Minimap hides itself when a system chat line contains this formatting-code string, which
 * renders as nothing; the code rides on the blank line printed under the profile id on joining.
 */
public final class ClientMinimapSuppressor {
    private static final String SPACER = "       ";
    private static final String XAERO_NO_MINIMAP = "§n§o§m§i§n§i§m§a§p";
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getenv("RAVENGARD_HIDE_CLIENT_MINIMAP"))
            && !"false".equalsIgnoreCase(System.getenv("RAVENGARD_HUD_MINIMAP"));

    private ClientMinimapSuppressor() {
    }

    public static Component joinSpacer() {
        if (!ENABLED) return Component.text(SPACER);
        return Component.text(SPACER + XAERO_NO_MINIMAP);
    }
}
