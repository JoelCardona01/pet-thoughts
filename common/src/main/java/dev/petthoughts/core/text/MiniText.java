package dev.petthoughts.core.text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A small subset of MiniMessage for platforms without Adventure (the Fabric mod): named colors,
 * {@code <#RRGGBB>}, {@code <bold>}/{@code <b>}, {@code <italic>}/{@code <i>}, closing tags and
 * placeholders. Unknown tags are kept as literal text, exactly like MiniMessage does.
 *
 * <p>The Paper plugin uses the real MiniMessage, so every tag works there.
 */
public final class MiniText {

    /** A run of text with one style. {@code value} is a String, or a platform text object from a placeholder. */
    public record Span(Object value, Integer color, boolean bold, boolean italic) {}

    private record Style(String tag, Integer color, boolean bold, boolean italic) {}

    private static final Map<String, Integer> COLORS = Map.ofEntries(
            Map.entry("black", 0x000000), Map.entry("dark_blue", 0x0000AA), Map.entry("dark_green", 0x00AA00),
            Map.entry("dark_aqua", 0x00AAAA), Map.entry("dark_red", 0xAA0000), Map.entry("dark_purple", 0xAA00AA),
            Map.entry("gold", 0xFFAA00), Map.entry("gray", 0xAAAAAA), Map.entry("grey", 0xAAAAAA),
            Map.entry("dark_gray", 0x555555), Map.entry("dark_grey", 0x555555), Map.entry("blue", 0x5555FF),
            Map.entry("green", 0x55FF55), Map.entry("aqua", 0x55FFFF), Map.entry("red", 0xFF5555),
            Map.entry("light_purple", 0xFF55FF), Map.entry("yellow", 0xFFFF55), Map.entry("white", 0xFFFFFF));

    private MiniText() {
    }

    /**
     * @param placeholders values for {@code <name>} tags: a String is inserted as plain text, any
     *                     other object is passed through as a span value for the platform to render
     */
    public static List<Span> parse(String input, Map<String, Object> placeholders) {
        List<Span> spans = new ArrayList<>();
        Deque<Style> stack = new ArrayDeque<>();
        stack.push(new Style("", null, false, false));
        StringBuilder text = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            int close = c == '<' ? input.indexOf('>', i) : -1;
            if (close < 0) {
                text.append(c);
                i++;
                continue;
            }
            String tag = input.substring(i + 1, close).trim().toLowerCase(Locale.ROOT);
            Style top = stack.peek();
            if (tag.startsWith("/")) {
                String name = alias(tag.substring(1));
                if (stack.stream().anyMatch(s -> s.tag.equals(name)) && stack.size() > 1) {
                    flush(spans, text, top);
                    while (stack.size() > 1 && !stack.pop().tag.equals(name)) {
                        // closing an outer tag also closes everything opened inside it
                    }
                    i = close + 1;
                    continue;
                }
            } else if (placeholders.containsKey(tag)) {
                flush(spans, text, top);
                Object value = placeholders.get(tag);
                spans.add(new Span(value instanceof String s ? s : value, top.color, top.bold, top.italic));
                i = close + 1;
                continue;
            } else {
                Style next = style(alias(tag), top);
                if (next != null) {
                    flush(spans, text, top);
                    stack.push(next);
                    i = close + 1;
                    continue;
                }
            }
            text.append(c);
            i++;
        }
        flush(spans, text, stack.peek());
        return spans;
    }

    private static String alias(String tag) {
        return switch (tag) {
            case "b" -> "bold";
            case "i", "em" -> "italic";
            default -> tag;
        };
    }

    private static Style style(String tag, Style parent) {
        if (tag.equals("bold")) {
            return new Style(tag, parent.color, true, parent.italic);
        }
        if (tag.equals("italic")) {
            return new Style(tag, parent.color, parent.bold, true);
        }
        Integer color = COLORS.get(tag);
        if (color == null && tag.matches("#[0-9a-f]{6}")) {
            color = Integer.parseInt(tag.substring(1), 16);
        }
        return color == null ? null : new Style(tag, color, parent.bold, parent.italic);
    }

    private static void flush(List<Span> spans, StringBuilder text, Style style) {
        if (text.length() > 0) {
            spans.add(new Span(text.toString(), style.color, style.bold, style.italic));
            text.setLength(0);
        }
    }
}
