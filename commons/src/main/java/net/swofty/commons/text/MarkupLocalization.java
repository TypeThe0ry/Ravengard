package net.swofty.commons.text;

import net.swofty.commons.YamlFileUtils;
import org.tinylog.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Swaps the English markup handed to {@link Text#of} for a translated markup from a catalog, keyed by
 * the English text with its tags stripped. This is the localization path for processes that render no
 * packets themselves (the proxy); game servers translate at the packet boundary instead.
 */
public final class MarkupLocalization {
    public static final String ENV_LOCALE = "RAVENGARD_LOCALE";
    public static final String ENV_DIRECTORY = "RAVENGARD_LOCALE_DIR";

    private MarkupLocalization() {
    }

    public static void installFromEnvironment() {
        String locale = System.getenv(ENV_LOCALE);
        String directory = System.getenv(ENV_DIRECTORY);
        if (locale == null || locale.isBlank() || directory == null || directory.isBlank()) return;
        Map<String, String> catalog = load(new File(directory));
        if (catalog.isEmpty()) return;
        Text.markupLocalizer(markup -> catalog.getOrDefault(normalize(plain(markup)), markup));
        Logger.info("Markup localization {} installed with {} entries", locale, catalog.size());
    }

    static Map<String, String> load(File directory) {
        Map<String, String> catalog = new HashMap<>();
        if (!directory.isDirectory()) {
            Logger.warn("Localization directory {} does not exist", directory);
            return catalog;
        }
        try {
            for (File file : YamlFileUtils.getYamlFiles(directory)) {
                Object entries = YamlFileUtils.loadYaml(file).get("entries");
                if (!(entries instanceof Map<?, ?> map)) continue;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) continue;
                    catalog.put(normalize(plain(String.valueOf(entry.getKey()))), String.valueOf(entry.getValue()));
                }
            }
        } catch (Exception exception) {
            Logger.error(exception, "Failed to load the localization from {}", directory);
        }
        return catalog;
    }

    /** Markup or plain text reduced to what the client would show: tags, escapes and legacy colour codes removed. */
    public static String plain(String markup) {
        StringBuilder builder = new StringBuilder(markup.length());
        for (int index = 0; index < markup.length(); index++) {
            char current = markup.charAt(index);
            if (current == '\\' && index + 1 < markup.length()) {
                builder.append(markup.charAt(++index));
            } else if (current == '<') {
                int close = closingTag(markup, index);
                if (close < 0) builder.append(current);
                else index = close;
            } else if (current == '§' && index + 1 < markup.length()) {
                index++;
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private static int closingTag(String markup, int open) {
        boolean quoted = false;
        for (int index = open + 1; index < markup.length(); index++) {
            char current = markup.charAt(index);
            if (current == '\'') quoted = !quoted;
            else if (current == '>' && !quoted) return index;
        }
        return -1;
    }

    /** Whitespace runs collapse to one space so wrapped and joined text still matches. */
    public static String normalize(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        boolean space = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (Character.isWhitespace(current)) {
                space = true;
                continue;
            }
            if (space && !builder.isEmpty()) builder.append(' ');
            space = false;
            builder.append(current);
        }
        return builder.toString();
    }

    static UnaryOperator<String> identity() {
        return UnaryOperator.identity();
    }
}
