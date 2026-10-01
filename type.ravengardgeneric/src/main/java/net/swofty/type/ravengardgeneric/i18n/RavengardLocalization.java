package net.swofty.type.ravengardgeneric.i18n;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.minestom.server.adventure.MinestomAdventure;
import net.swofty.commons.YamlFileUtils;
import net.swofty.commons.text.TextParser;
import org.jetbrains.annotations.Nullable;
import org.tinylog.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Player-facing text is authored in English throughout the code base and the configuration. A
 * localization catalog maps that English -- the markup as written in the source, or the plain text
 * of a config value -- onto translated markup, and every component leaving the server through a
 * component-holding packet is rewritten against the catalog for players whose locale it serves.
 * Nothing is translated unless a catalog is installed, so the default deployment stays English.
 */
public final class RavengardLocalization {
    public static final String ENV_LOCALE = "RAVENGARD_LOCALE";
    public static final String ENV_DIRECTORY = "RAVENGARD_LOCALE_DIR";
    public static final String ENV_REPORT = "RAVENGARD_LOCALE_REPORT";
    private static final java.util.Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static final java.util.Set<String> INTERNAL_TEXT = java.util.Set.of("displaynametest");
    private static volatile boolean report;
    private static final Key DEFAULT_FONT = Key.key("minecraft", "default");
    private static final Key ILLAGERALT = Key.key("minecraft", "illageralt");
    private static final int CACHE_LIMIT = 4096;

    private static volatile Catalog catalog;
    private static volatile boolean everyone;
    private static volatile String language;
    private static final Map<Component, Component> CACHE = new ConcurrentHashMap<>();

    private RavengardLocalization() {
    }

    public static void installFromEnvironment() {
        String locale = System.getenv(ENV_LOCALE);
        String directory = System.getenv(ENV_DIRECTORY);
        if (locale == null || locale.isBlank() || directory == null || directory.isBlank()) {
            Logger.info("Ravengard localization disabled ({} / {} not set)", ENV_LOCALE, ENV_DIRECTORY);
            return;
        }
        Catalog loaded = Catalog.load(new File(directory));
        if (loaded == null) return;
        install(loaded, locale, true);
        registerFontOverlay(new File(directory, "resourcepack"));
        report = Boolean.parseBoolean(System.getenv(ENV_REPORT));
        if (report) Logger.info("Localization report enabled: untranslated text leaves are logged once each");
    }

    /**
     * The overlay pack carries the glyph advances the official pack's centring fonts lack for the
     * translated characters. It is built here only to learn its hash; the pack server serves it.
     */
    private static void registerFontOverlay(File packDirectory) {
        if (!packDirectory.isDirectory()) {
            Logger.info("No localization font overlay at {}", packDirectory);
            return;
        }
        try {
            var definition = new net.swofty.packer.packs.ravengard.RavengardLocalePackDefinition(packDirectory.getPath());
            var built = new net.swofty.packer.HypixelPackBuilder(definition).build();
            String serverUrl = net.swofty.type.generic.resourcepack.HypixelResourcePack
                    .getConfigFor(net.swofty.packer.packs.ravengard.RavengardPackDefinition.INSTANCE.getPackName())
                    .getServerUrl();
            net.swofty.type.generic.resourcepack.ResourcePackManager.addExtraPack(
                    net.kyori.adventure.resource.ResourcePackInfo.resourcePackInfo(
                            java.util.UUID.nameUUIDFromBytes(built.hash().getBytes()),
                            java.net.URI.create(serverUrl + "/" + built.hash() + ".zip"),
                            built.hash()));
            Logger.info("Localization font overlay registered: {}", built.hash());
        } catch (Exception exception) {
            Logger.error(exception, "Failed to build the localization font overlay from {}", packDirectory);
        }
    }

    public static void install(Catalog loaded, String locale, boolean forEveryone) {
        catalog = loaded;
        language = locale.replace('-', '_').toLowerCase(Locale.ROOT);
        everyone = forEveryone;
        CACHE.clear();
        BiFunction<Component, Locale, Component> previous = MinestomAdventure.COMPONENT_TRANSLATOR;
        MinestomAdventure.COMPONENT_TRANSLATOR = (component, viewerLocale) -> {
            Component rendered = previous.apply(component, viewerLocale);
            return serves(viewerLocale) ? translate(rendered) : rendered;
        };
        Logger.info("Ravengard localization {} installed: {} exact entries, {} templates, for {}",
                language, loaded.exact.size(), loaded.templates.size(), forEveryone ? "everyone" : "matching locales");
    }

    static boolean serves(Locale viewerLocale) {
        if (everyone) return true;
        String lang = language;
        return viewerLocale != null && lang != null
                && lang.startsWith(viewerLocale.getLanguage().toLowerCase(Locale.ROOT));
    }

    public static boolean isActive() {
        return catalog != null;
    }

    /** The translated markup for an English source string, or null when the catalog has none. */
    public static @Nullable String localize(String english) {
        Catalog current = catalog;
        if (current == null || english == null) return null;
        return current.exact.get(normalize(plain(english)));
    }

    static String normalize(String text) {
        return net.swofty.commons.text.MarkupLocalization.normalize(text);
    }

    public static Component translate(Component component) {
        Catalog current = catalog;
        if (current == null || component == null) return component;
        Component cached = CACHE.get(component);
        if (cached != null) return cached;
        Component translated;
        try {
            translated = translate(current, component, null);
        } catch (RuntimeException exception) {
            Logger.warn(exception, "Ravengard localization failed for a component; sending it untranslated");
            translated = component;
        }
        if (CACHE.size() >= CACHE_LIMIT) CACHE.clear();
        CACHE.put(component, translated);
        if (translated != component) CACHE.put(translated, translated);
        if (report) reportUntranslated(component, translated, null);
        return translated;
    }

    /** Subtrees the catalog left untouched still carrying Latin words are gaps; each is logged once. */
    private static void reportUntranslated(Component original, Component translated, @Nullable Key inheritedFont) {
        Key font = original.style().font() != null ? original.style().font() : inheritedFont;
        if (translated == original) {
            reportLeaves(original, font);
            return;
        }
        if (original.hoverEvent() == translated.hoverEvent()) reportHover(original);
        List<Component> before = original.children();
        List<Component> after = translated.children();
        if (before.size() != after.size()) return;
        for (int index = 0; index < before.size(); index++) {
            reportUntranslated(before.get(index), after.get(index), font);
        }
    }

    private static void reportLeaves(Component component, @Nullable Key inheritedFont) {
        Key font = component.style().font() != null ? component.style().font() : inheritedFont;
        if (component instanceof TextComponent text && !(font != null && isSpacingFont(font))
                && !ILLAGERALT.equals(font)) {
            String content = normalize(text.content());
            if (content.matches(".*[A-Za-z]{3,}.*") && !INTERNAL_TEXT.contains(content) && REPORTED.add(content)) {
                Logger.info("[i18n-gap] {}", content);
            }
        }
        reportHover(component);
        for (Component child : component.children()) reportLeaves(child, font);
    }

    private static void reportHover(Component component) {
        HoverEvent<?> hover = component.hoverEvent();
        if (hover != null && hover.value() instanceof Component text) reportLeaves(text, null);
    }

    private static Component translate(Catalog current, Component component, @Nullable Key inheritedFont) {
        Key font = component.style().font() != null ? component.style().font() : inheritedFont;
        // the rune font is used on purpose for text that must stay unreadable
        if (ILLAGERALT.equals(font)) return component;
        Source source = Source.of(component);
        if (hasLetters(source.text())) {
            Component whole = current.render(source);
            if (whole != null) return localizeHover(current, source.interactive(finish(whole, component, font)));
        }
        Component result = localizeHover(current, component);
        if (component instanceof TextComponent text && !text.content().isEmpty() && hasLetters(text.content())) {
            Component leaf = current.render(normalize(text.content()));
            if (leaf != null) {
                result = finish(leaf, Component.text(text.content()).style(result.style()), font)
                        .children(text.children());
            }
        }
        List<Component> children = result.children();
        if (children.isEmpty()) return result;
        List<Component> translated = new ArrayList<>(children.size());
        boolean changed = false;
        for (Component child : children) {
            Component next = translate(current, child, font);
            changed |= next != child;
            translated.add(next);
        }
        return changed ? result.children(translated) : result;
    }

    /**
     * The rendered translation keeps the original style. Fonts without CJK glyphs are swapped for the
     * default font, except the pack's spacing fonts: those carry no visible text and the overlay pack
     * gives them advances for every translated character.
     */
    private static Component finish(Component rendered, Component original, @Nullable Key effectiveFont) {
        Component styled = rendered.applyFallbackStyle(original.style());
        if (effectiveFont == null || effectiveFont.equals(DEFAULT_FONT) || isSpacingFont(effectiveFont)) return styled;
        return withDefaultFont(styled);
    }

    private static boolean isSpacingFont(Key font) {
        return "hypixel_ravengard".equals(font.namespace()) && font.value().startsWith("-");
    }

    private static Component withDefaultFont(Component component) {
        Component result = component.style().font() == null
                || !"minecraft".equals(component.style().font().namespace())
                ? component.font(DEFAULT_FONT) : component;
        if (result.children().isEmpty()) return result;
        List<Component> children = new ArrayList<>(result.children().size());
        for (Component child : result.children()) children.add(withDefaultFont(child));
        return result.children(children);
    }

    private static Component localizeHover(Catalog current, Component component) {
        HoverEvent<?> hover = component.hoverEvent();
        if (hover == null || !(hover.value() instanceof Component text)) return component;
        Component translated = translate(current, text, null);
        return translated == text ? component : component.hoverEvent(HoverEvent.showText(translated));
    }

    static String flatten(Component component) {
        StringBuilder builder = new StringBuilder();
        flatten(component, builder);
        return builder.toString();
    }

    private static final char TOKEN_BASE = '\uF8F0';
    private static final int TOKEN_LIMIT = 16;

    private static void flatten(Component component, StringBuilder builder) {
        if (component instanceof TextComponent text) {
            builder.append(text.content());
        } else {
            builder.append(TOKEN_BASE);
        }
        for (Component child : component.children()) flatten(child, builder);
    }

    /**
     * The visible text of a component tree, whitespace-normalized like a catalog key, together with
     * the style each stretch of it was written in. Leaves that are not plain text (keybinds,
     * translatables) become private-use tokens so a template can capture them whole.
     */
    static final class Source {
        private final StringBuilder text = new StringBuilder();
        private final List<Run> runs = new ArrayList<>();
        private final List<Component> tokens = new ArrayList<>();
        private boolean space;
        private boolean mixedInteraction;
        private @Nullable ClickEvent click;
        private @Nullable HoverEvent<?> hover;

        static Source of(Component component) {
            Source source = new Source();
            source.read(component, Style.empty());
            return source;
        }

        String text() {
            return text.toString();
        }

        List<Component> tokens() {
            return tokens;
        }

        private void read(Component component, Style inherited) {
            Style style = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
            if (component instanceof TextComponent content) {
                append(content.content(), style);
            } else if (tokens.size() < TOKEN_LIMIT) {
                tokens.add(component.children(List.of()));
                append(String.valueOf((char) (TOKEN_BASE + tokens.size() - 1)), style);
            }
            for (Component child : component.children()) read(child, style);
        }

        private void append(String content, Style style) {
            int start = -1;
            for (int index = 0; index < content.length(); index++) {
                char current = content.charAt(index);
                if (Character.isWhitespace(current)) {
                    space = true;
                    continue;
                }
                if (space && !text.isEmpty()) text.append(' ');
                space = false;
                if (start < 0) start = text.length();
                text.append(current);
            }
            if (start < 0) return;
            if (runs.isEmpty()) {
                click = style.clickEvent();
                hover = style.hoverEvent();
            } else if (!Objects.equals(click, style.clickEvent()) || !Objects.equals(hover, style.hoverEvent())) {
                mixedInteraction = true;
            }
            runs.add(new Run(start, text.length(), style));
        }

        /** A click or hover shared by all of the source text still applies to its translation. */
        Component interactive(Component rendered) {
            if (mixedInteraction || (click == null && hover == null)) return rendered;
            return rendered.applyFallbackStyle(Style.style().clickEvent(click).hoverEvent(hover).build());
        }

        /** The captured stretch as it was styled in the source, so names keep their colours inside a translated sentence. */
        Component slice(int start, int end) {
            List<Component> parts = new ArrayList<>();
            int cursor = start;
            for (Run run : runs) {
                if (run.end() <= cursor || run.start() >= end) continue;
                int stop = Math.min(run.end(), end);
                parts.add(Component.text(text.substring(cursor, stop)).style(run.style()));
                cursor = stop;
            }
            if (parts.isEmpty()) return Component.text(text.substring(start, end));
            if (cursor < end) parts.add(Component.text(text.substring(cursor, end)));
            return parts.size() == 1 ? parts.getFirst() : Component.textOfChildren(parts.toArray(Component[]::new));
        }

        /** The style a translated argument falls back to when the whole capture was written in one style. */
        @Nullable Style styleOf(int start, int end) {
            for (Run run : runs) {
                if (run.start() <= start && end <= run.end()) return readable(run.style());
            }
            return null;
        }

        private static Style readable(Style style) {
            Key font = style.font();
            return font == null || font.equals(DEFAULT_FONT) || isSpacingFont(font) ? style : style.font(DEFAULT_FONT);
        }

        private record Run(int start, int end, Style style) {
        }
    }

    static @Nullable Component token(String captured, List<Component> tokens) {
        if (captured.length() != 1) return null;
        int index = captured.charAt(0) - TOKEN_BASE;
        return index >= 0 && index < tokens.size() ? tokens.get(index) : null;
    }

    private static boolean hasLetters(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (Character.isLetter(text.charAt(index))) return true;
        }
        return false;
    }

    static String plain(String markup) {
        return net.swofty.commons.text.MarkupLocalization.plain(markup);
    }

    public static final class Catalog {
        private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d*)(?::[^}]*)?}");

        private final Map<String, String> exact = new HashMap<>();
        private final List<Template> templates = new ArrayList<>();

        public static @Nullable Catalog load(File directory) {
            if (!directory.isDirectory()) {
                Logger.warn("Ravengard localization directory {} does not exist", directory);
                return null;
            }
            Catalog loaded = new Catalog();
            try {
                for (File file : YamlFileUtils.getYamlFiles(directory)) {
                    Map<String, Object> data = YamlFileUtils.loadYaml(file);
                    Object entries = data == null ? null : data.get("entries");
                    if (!(entries instanceof Map<?, ?> map)) continue;
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (entry.getKey() == null || entry.getValue() == null) continue;
                        loaded.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                    }
                }
            } catch (Exception exception) {
                Logger.error(exception, "Failed to load the Ravengard localization from {}", directory);
                return null;
            }
            loaded.sortTemplates();
            return loaded;
        }

        /** The most specific template wins: more literal text is matched before shorter patterns. */
        public void sortTemplates() {
            templates.sort((a, b) -> Integer.compare(b.literalLength(), a.literalLength()));
        }

        public void put(String english, String translated) {
            String key = normalize(plain(english));
            if (key.isBlank() || key.equals(plain(translated))) return;
            Matcher placeholders = PLACEHOLDER.matcher(key);
            if (!placeholders.find()) {
                exact.put(key, translated);
                return;
            }
            StringBuilder regex = new StringBuilder("^");
            int last = 0;
            int count = 0;
            placeholders.reset();
            while (placeholders.find()) {
                regex.append(Pattern.quote(key.substring(last, placeholders.start()))).append("(.+?)");
                last = placeholders.end();
                count++;
            }
            regex.append(Pattern.quote(key.substring(last))).append('$');
            templates.add(new Template(Pattern.compile(regex.toString(), Pattern.DOTALL), translated, count,
                    key.substring(0, Math.min(key.length(), firstPlaceholder(key))),
                    PLACEHOLDER.matcher(key).replaceAll("").length()));
        }

        private static int firstPlaceholder(String key) {
            Matcher matcher = PLACEHOLDER.matcher(key);
            return matcher.find() ? matcher.start() : key.length();
        }

        public int size() {
            return exact.size() + templates.size();
        }

        @Nullable Component render(String flat) {
            return render(flat, List.of(), null);
        }

        @Nullable Component render(Source source) {
            return render(source.text(), source.tokens(), source);
        }

        private @Nullable Component render(String flat, List<Component> tokens, @Nullable Source source) {
            String direct = exact.get(flat);
            if (direct != null) return TextParser.parseLenient(direct);
            for (Template template : templates) {
                if (!flat.startsWith(template.prefix())) continue;
                Matcher matcher = template.pattern().matcher(flat);
                if (!matcher.matches()) continue;
                Object[] arguments = new Object[template.arguments()];
                for (int index = 0; index < arguments.length; index++) {
                    int start = matcher.start(index + 1);
                    int end = matcher.end(index + 1);
                    while (start < end && flat.charAt(start) == ' ') start++;
                    while (end > start && flat.charAt(end - 1) == ' ') end--;
                    String captured = flat.substring(start, end);
                    Component token = token(captured, tokens);
                    Component nested = token == null && hasLetters(captured) ? render(captured, tokens, null) : null;
                    if (token != null) {
                        arguments[index] = token;
                    } else if (source == null) {
                        arguments[index] = nested != null ? nested : captured;
                    } else if (nested != null) {
                        Style style = source.styleOf(start, end);
                        arguments[index] = style == null ? nested : nested.applyFallbackStyle(style);
                    } else {
                        arguments[index] = source.slice(start, end);
                    }
                }
                return TextParser.parseLenient(template.translated(), arguments);
            }
            return null;
        }

        private record Template(Pattern pattern, String translated, int arguments, String prefix, int literalLength) {
        }
    }
}
