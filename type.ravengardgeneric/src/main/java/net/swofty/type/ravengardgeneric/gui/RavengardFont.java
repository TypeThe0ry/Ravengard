package net.swofty.type.ravengardgeneric.gui;

import net.kyori.adventure.key.Key;

public final class RavengardFont {
    public static final Key DEFAULT = Key.key("minecraft", "default");
    public static final Key RAVENGARD = Key.key("hypixel_ravengard", "ravengard");
    public static final Key HALF = Key.key("hypixel_ravengard", "-half");
    public static final Key FULL = Key.key("hypixel_ravengard", "-full");
    /** Vanilla Illager runes. Unreleased buttons render their whole tooltip in this so it reads as gibberish. */
    public static final Key ILLAGERALT = Key.key("minecraft", "illageralt");

    private static final int SPACE_ORIGIN = 0xD0000;
    private static final int SPACE_MIN = -8192;
    private static final int SPACE_MAX = 8192;

    private RavengardFont() {
    }

    public static String space(int pixels) {
        int clamped = Math.max(SPACE_MIN, Math.min(SPACE_MAX, pixels));
        return new String(Character.toChars(SPACE_ORIGIN + clamped));
    }

    // the pack only maps every pixel advance up to +-512, so exact wider gaps are chained
    private static final int PIXEL_SPACE_STEP = 512;

    public static String pixelSpace(int pixels) {
        int remaining = Math.max(SPACE_MIN, Math.min(SPACE_MAX, pixels));
        StringBuilder spaces = new StringBuilder();
        while (Math.abs(remaining) > PIXEL_SPACE_STEP) {
            int step = Integer.signum(remaining) * PIXEL_SPACE_STEP;
            spaces.appendCodePoint(SPACE_ORIGIN + step);
            remaining -= step;
        }
        return spaces.appendCodePoint(SPACE_ORIGIN + remaining).toString();
    }

    public static String glyph(int codePoint) {
        return new String(Character.toChars(codePoint));
    }
}
