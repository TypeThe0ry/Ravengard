package net.swofty.type.ravengardgeneric.i18n;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.swofty.commons.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class RavengardLocalizationTest {
    private static RavengardLocalization.Catalog catalog() {
        RavengardLocalization.Catalog catalog = new RavengardLocalization.Catalog();
        catalog.put("<a>You used <f>{}<a>!", "<a>你使用了 <f>{}<a>！");
        catalog.put("Med Kit", "医疗包");
        catalog.put("{} <7>was defeated by <f>{}<7>!", "<7>{} 被 <f>{}<7> 击败！");
        catalog.put("Zombie Knight", "僵尸骑士");
        catalog.put("Welcome to §5Ravenport§f! It's always nice to see a new adventurer.",
                "欢迎来到 <5>渡鸦港<f>！很高兴见到新的冒险者。");
        catalog.put("Fight", "战斗");
        catalog.put("<7>Cooldown: <e>{}", "<7>冷却：<e>{}");
        return catalog;
    }

    @Test
    void templatesRenderWithTranslatedArguments() {
        RavengardLocalization.Catalog catalog = catalog();
        Component message = Text.of("<a>You used <f>{}<a>!", "Med Kit").asComponent();

        Component translated = translate(catalog, message);

        assertEquals("你使用了 医疗包！", RavengardLocalization.flatten(translated));
    }

    @Test
    void wholeSentenceTemplatesReorderArgumentsAndKeepStyle() {
        RavengardLocalization.Catalog catalog = catalog();
        Component message = Text.of("{} <7>was defeated by <f>{}<7>!", "TypeThe0ry", "Zombie Knight").asComponent()
                .decoration(TextDecoration.ITALIC, false);

        Component translated = translate(catalog, message);

        assertEquals("TypeThe0ry 被 僵尸骑士 击败！", RavengardLocalization.flatten(translated));
        assertEquals(TextDecoration.State.FALSE, translated.decoration(TextDecoration.ITALIC));
    }

    @Test
    void legacyColourCodesInConfigTextMatchTheirPlainForm() {
        RavengardLocalization.Catalog catalog = catalog();
        Component prefix = Text.of("<d>{}<f>: ", "Baldwin").asComponent();
        Component line = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .deserialize("Welcome to §5Ravenport§f! It's always nice to see a new adventurer.");

        Component translated = translate(catalog, prefix.append(line));

        assertEquals("Baldwin: 欢迎来到 渡鸦港！很高兴见到新的冒险者。", RavengardLocalization.flatten(translated));
    }

    @Test
    void packBitmapFontsFallBackToTheDefaultFontAndRunesStayUntouched() {
        RavengardLocalization.Catalog catalog = catalog();
        Component button = Component.text("Fight").font(Key.key("hypixel_ravengard", "ravengard")).color(NamedTextColor.RED);
        Component runes = Component.text("Fight").font(Key.key("minecraft", "illageralt"));

        Component translated = translate(catalog, button);

        assertEquals("战斗", RavengardLocalization.flatten(translated));
        assertEquals(Key.key("minecraft", "default"), translated.style().font());
        assertEquals(NamedTextColor.RED, translated.color());
        assertSame(runes, translate(catalog, runes));
    }

    @Test
    void spacingFontsKeepTheirFontSoTheOverlayAdvancesApply() {
        RavengardLocalization.Catalog catalog = catalog();
        Component half = Component.text("Fight").font(Key.key("hypixel_ravengard", "-half"));

        Component translated = translate(catalog, half);

        assertEquals("战斗", RavengardLocalization.flatten(translated));
        assertEquals(Key.key("hypixel_ravengard", "-half"), translated.style().font());
    }

    @Test
    void untranslatedComponentsAreReturnedUntouched() {
        RavengardLocalization.Catalog catalog = catalog();
        Component glyphs = Component.text("").font(Key.key("hypixel_ravengard", "ravengard"));

        assertSame(glyphs, translate(catalog, glyphs));
        assertNull(catalog.render("No such line"));
        assertFalse(RavengardLocalization.serves(java.util.Locale.US));
    }

    @Test
    void numbersInsideTemplatesStayLiteral() {
        RavengardLocalization.Catalog catalog = catalog();
        Component lore = Text.of("<7>Cooldown: <e>{}", "1m 40s").asComponent();

        assertEquals("冷却：1m 40s", RavengardLocalization.flatten(translate(catalog, lore)));
    }

    private static Component translate(RavengardLocalization.Catalog catalog, Component component) {
        RavengardLocalization.install(catalog, "zh_CN", false);
        return RavengardLocalization.translate(component);
    }
}
