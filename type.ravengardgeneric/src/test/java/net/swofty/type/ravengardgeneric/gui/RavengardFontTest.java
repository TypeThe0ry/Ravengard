package net.swofty.type.ravengardgeneric.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavengardFontTest {
    @Test
    void pixelSpacesOnlyUseAdvancesThePackDefinesAndSumToTheRequestedWidth() {
        for (int pixels : new int[]{0, 1, -1, 512, -512, 513, 525, -1061, 1000, 4000, -8192, 8192}) {
            int total = 0;
            for (int codePoint : RavengardFont.pixelSpace(pixels).codePoints().toArray()) {
                int advance = codePoint - 0xD0000;
                assertTrue(Math.abs(advance) <= 512, "undefined space advance " + advance + " for " + pixels);
                total += advance;
            }
            assertEquals(pixels, total);
        }
    }

    @Test
    void menuChromeKeepsTheSingleCodepointSpacesItWasDesignedAgainst() {
        assertEquals(new String(Character.toChars(0xCF008)), RavengardFont.space(-4088));
        assertEquals(new String(Character.toChars(0xCFFF3)), RavengardFont.space(-13));
    }
}
