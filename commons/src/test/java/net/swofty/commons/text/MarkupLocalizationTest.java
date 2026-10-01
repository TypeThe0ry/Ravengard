package net.swofty.commons.text;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MarkupLocalizationTest {
    private static final File CATALOG = new File("../localization/zh_CN");

    @AfterEach
    void restore() {
        Text.markupLocalizer(null);
    }

    @Test
    void shippedCatalogResolvesProxyMarkupByItsPlainText() {
        Map<String, String> catalog = MarkupLocalization.load(CATALOG);

        assertFalse(catalog.isEmpty());
        assertEquals("<c>与服务器的连接已断开，请稍后再试。", catalog.get(MarkupLocalization.normalize(MarkupLocalization.plain(
                "<c>Your connection to the server was lost. Please try again later."))));
    }

    @Test
    void installedLocalizerRewritesMarkupBeforeItIsParsed() {
        Map<String, String> catalog = MarkupLocalization.load(CATALOG);
        Text.markupLocalizer(markup -> catalog.getOrDefault(
                MarkupLocalization.normalize(MarkupLocalization.plain(markup)), markup));

        assertEquals("与服务器的连接已断开，请稍后再试。",
                Text.of("<c>Your connection to the server was lost. Please try again later.").plain());
    }
}
