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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        catalog.put("<#f0f05c>Press {} to use!", "<#f0f05c>按 {} 使用！");
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

    @Test
    void keybindArgumentsSurviveTemplateTranslation() {
        RavengardLocalization.Catalog catalog = catalog();
        Component hint = Text.of("<#f0f05c>Press {} to use!", Component.keybind("key.swapOffhand")).asComponent();

        Component translated = translate(catalog, hint);

        String flat = RavengardLocalization.flatten(translated);
        assertTrue(flat.startsWith("按 ") && flat.endsWith(" 使用！"), flat);
        assertEquals(1, countKeybinds(translated));
    }

    @Test
    void capturedNamesKeepTheColourTheyWereSentIn() {
        RavengardLocalization.Catalog catalog = catalog();
        catalog.put("{} <e>has left the party.", "{} <e>离开了队伍。");
        Component name = Component.text("[MVP] Alice", NamedTextColor.AQUA);
        Component message = Text.of("{} <e>has left the party.", name).asComponent();

        Component translated = translate(catalog, message);

        assertEquals("[MVP] Alice 离开了队伍。", RavengardLocalization.flatten(translated));
        assertEquals(NamedTextColor.AQUA, colourOf(translated, "[MVP] Alice", null));
    }

    @Test
    void aClickSharedByTheWholeSentenceSurvivesTranslation() {
        RavengardLocalization.Catalog catalog = catalog();
        catalog.put("<e>You have <c>60 <e>seconds to accept. <6>Click here to join!",
                "<e>你有 <c>60 <e>秒时间接受。<6>点击这里加入！");
        catalog.put("<e>Click here to join!", "<e>点击这里加入！");
        Component message = Text.of("""
                <hover:'<e>Click here to join!'><click:run:'/p accept {}'>\
                <e>You have <c>60 <e>seconds to accept. <6>Click here to join!</click></hover>""", "Alice").asComponent();

        Component translated = translate(catalog, message);

        assertEquals("你有 60 秒时间接受。点击这里加入！", RavengardLocalization.flatten(translated));
        assertEquals(net.kyori.adventure.text.event.ClickEvent.runCommand("/p accept Alice"), translated.clickEvent());
        assertEquals("点击这里加入！",
                RavengardLocalization.flatten((Component) translated.hoverEvent().value()));
    }

    @Test
    void differentClicksInOneLineAreTranslatedPieceByPiece() {
        RavengardLocalization.Catalog catalog = catalog();
        catalog.put("[ACCEPT]", "[接受]");
        catalog.put("[DENY]", "[拒绝]");
        Component accept = Component.text(" [ACCEPT]", NamedTextColor.GREEN)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/f accept Alice"));
        Component deny = Component.text(" [DENY]", NamedTextColor.RED)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/f deny Alice"));

        Component translated = translate(catalog, Component.empty().append(accept).append(deny));

        assertEquals("[接受][拒绝]", RavengardLocalization.flatten(translated));
        assertEquals(accept.clickEvent(), translated.children().get(0).clickEvent());
        assertEquals(deny.clickEvent(), translated.children().get(1).clickEvent());
    }

    @Test
    void unresolvedContextPlaceholdersAreCapturedSoHelpLinesStillTranslate() {
        RavengardLocalization.Catalog catalog = catalog();
        catalog.put("<e>/p invite {} <8>- <7>Invite another player to your party",
                "<e>/p invite \\<玩家> <8>- <7>邀请玩家加入你的队伍");
        Component line = Component.text("/p invite ", NamedTextColor.YELLOW)
                .append(Component.translatable("hsb.ctx.player"))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Invite another player to your party", NamedTextColor.GRAY));

        assertEquals("/p invite <玩家> - 邀请玩家加入你的队伍", RavengardLocalization.flatten(translate(catalog, line)));
    }

    @Test
    void everyShippedTranslationParsesAndUsesNoMoreArgumentsThanItsSource() throws Exception {
        java.io.File directory = new java.io.File("../localization/zh_CN");
        java.util.regex.Pattern placeholder = java.util.regex.Pattern.compile("\\{(\\d*)}");
        int entries = 0;
        java.util.Map<String, String> owners = new java.util.HashMap<>();
        for (java.io.File file : net.swofty.commons.YamlFileUtils.getYamlFiles(directory)) {
            for (String line : java.nio.file.Files.readAllLines(file.toPath())) {
                if (!line.startsWith("  \"")) continue;
                String quoted = line.substring(3, line.indexOf("\": \""));
                String key = RavengardLocalization.normalize(RavengardLocalization.plain(quoted
                        .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")));
                String previous = owners.put(key, file.getName());
                assertNull(previous, "duplicate key '" + key + "' in " + file.getName() + " and " + previous);
            }
            Object loaded = net.swofty.commons.YamlFileUtils.loadYaml(file).get("entries");
            for (java.util.Map.Entry<?, ?> entry : ((java.util.Map<?, ?>) loaded).entrySet()) {
                String key = RavengardLocalization.plain(String.valueOf(entry.getKey()));
                String value = String.valueOf(entry.getValue());
                int available = count(placeholder, key);
                int needed = highest(placeholder, RavengardLocalization.plain(value));
                assertTrue(needed <= available, file.getName() + ": " + entry.getKey());
                Object[] arguments = new Object[available];
                java.util.Arrays.fill(arguments, "x");
                net.swofty.commons.text.TextParser.parseLenient(value, arguments);
                entries++;
            }
        }
        assertTrue(entries > 500, "catalog entries: " + entries);
        assertTrue(RavengardLocalization.Catalog.load(directory).size() > 500);
    }

    private static int count(java.util.regex.Pattern placeholder, String text) {
        java.util.regex.Matcher matcher = placeholder.matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    /** Positional placeholders name an index; bare ones are consumed in order. */
    private static int highest(java.util.regex.Pattern placeholder, String text) {
        java.util.regex.Matcher matcher = placeholder.matcher(text);
        int bare = 0;
        int highest = 0;
        while (matcher.find()) {
            if (matcher.group(1).isEmpty()) highest = Math.max(highest, ++bare);
            else highest = Math.max(highest, Integer.parseInt(matcher.group(1)) + 1);
        }
        return highest;
    }

    @Test
    void shaderTintOnAnArgumentSurvivesWhereverTheTreeCarriesIt() {
        RavengardLocalization.Catalog catalog = catalog();
        catalog.put("Opening Chest", "开启宝箱");
        net.kyori.adventure.text.format.TextColor tint = net.kyori.adventure.text.format.TextColor.color(0xfefd02);

        Component translated = translate(catalog, Text.of("<#fefd02>{}", "Opening Chest").asComponent());

        assertEquals("开启宝箱", RavengardLocalization.flatten(translated));
        assertEquals(tint, colourOf(translated, "开启宝箱", null));
    }

    private static net.kyori.adventure.text.format.TextColor colourOf(
            Component component, String content, net.kyori.adventure.text.format.TextColor inherited) {
        net.kyori.adventure.text.format.TextColor colour = component.color() != null ? component.color() : inherited;
        if (component instanceof net.kyori.adventure.text.TextComponent text && text.content().equals(content)) {
            return colour;
        }
        for (Component child : component.children()) {
            net.kyori.adventure.text.format.TextColor found = colourOf(child, content, colour);
            if (found != null) return found;
        }
        return null;
    }

    private static int countKeybinds(Component component) {
        int count = component instanceof net.kyori.adventure.text.KeybindComponent ? 1 : 0;
        for (Component child : component.children()) count += countKeybinds(child);
        return count;
    }

    private static Component translate(RavengardLocalization.Catalog catalog, Component component) {
        RavengardLocalization.install(catalog, "zh_CN", false);
        return RavengardLocalization.translate(component);
    }
}
