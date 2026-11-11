package dev.petthoughts.core.bubble;

import dev.petthoughts.core.Trigger;
import dev.petthoughts.core.bubble.Bubble.Keyframe;
import dev.petthoughts.core.bubble.Bubble.Rect;
import dev.petthoughts.core.bubble.Bubble.Run;
import dev.petthoughts.core.config.LangStore;
import dev.petthoughts.core.config.Settings;
import dev.petthoughts.core.text.MiniText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turns a line of text into a comic bubble: text wrapped to measure, a rounded pixel-art frame with
 * an outline and a thicker bottom edge, a tail (thought dots, or a pointer for danger) and the
 * pop / float / shake animation. Pure geometry: each platform only has to draw the result.
 */
public final class BubbleDesigner {

    private static final int PAD_X = 4;
    private static final int PAD_Y = 2;
    /** Height of the tail below the frame, by style. */
    private static final int DOTS_HEIGHT = 14;
    private static final int POINTER_HEIGHT = 6;

    private record Glyph(int codePoint, Integer rgb, boolean bold, boolean italic) {}

    private BubbleDesigner() {
    }

    /**
     * @param line         the thought, MiniMessage-style (colours, bold, italic)
     * @param placeholders values for &lt;owner&gt;, &lt;pet&gt;... always inserted as plain text
     */
    public static Bubble design(Settings settings, LangStore lang, String line, Trigger trigger,
            Map<String, Object> placeholders) {
        Trigger.Category category = trigger.category();
        Settings.Palette palette = settings.palette(category);

        List<Glyph> glyphs = new ArrayList<>();
        String prefix = lang.rawOrNull("bubble.prefix." + category.id());
        if (prefix != null && !prefix.isBlank()) {
            append(glyphs, MiniText.parse(prefix, Map.of()));
            glyphs.add(new Glyph(' ', null, false, false));
        }
        List<MiniText.Span> textSpans = MiniText.parse(line, placeholders);
        append(glyphs, textSpans);

        List<List<Glyph>> lines = wrap(glyphs, settings.lineWidth);
        int textWidth = 0;
        for (List<Glyph> l : lines) {
            textWidth = Math.max(textWidth, width(l));
        }
        int lineCount = Math.max(1, lines.size());

        boolean pointer = category == Trigger.Category.DANGER;
        int tail = !settings.tail ? 1 : pointer ? POINTER_HEIGHT : DOTS_HEIGHT;
        // Frame (outer edge of the outline). The text's visible area is [2, 10 * lines] above its origin.
        float w = Math.max(10, textWidth - 1) + 2 * PAD_X + 2;
        float h = 10 * lineCount - 2 + 2 * PAD_Y + 2;
        float left = -w / 2f;
        float bottom = tail;

        List<Rect> rects = new ArrayList<>();
        frame(rects, left, bottom, w, h, palette);
        if (settings.tail) {
            if (pointer) {
                pointer(rects, palette);
            } else {
                dots(rects, palette);
            }
        }

        StringBuilder plain = new StringBuilder();
        for (MiniText.Span span : textSpans) {
            plain.append(span.value());
        }
        return new Bubble(rects, runs(lines), palette.text(), 0f, bottom + 1 + PAD_Y - 2,
                keyframes(settings, pointer), settings.displayTicks, trigger.tier(), plain.toString());
    }

    // ------------------------------------------------------------------
    //  Text
    // ------------------------------------------------------------------

    private static void append(List<Glyph> out, List<MiniText.Span> spans) {
        for (MiniText.Span span : spans) {
            String text = String.valueOf(span.value());
            text.codePoints().forEach(cp -> out.add(new Glyph(cp == '\n' ? ' ' : cp, span.color(), span.bold(), span.italic())));
        }
    }

    private static int width(List<Glyph> glyphs) {
        int total = 0;
        for (Glyph g : glyphs) {
            total += FontWidths.advance(g.codePoint, g.bold);
        }
        return total;
    }

    /** Greedy word wrap with the real glyph widths; words longer than a line are cut. */
    private static List<List<Glyph>> wrap(List<Glyph> glyphs, int maxWidth) {
        List<List<Glyph>> words = new ArrayList<>();
        List<Glyph> word = new ArrayList<>();
        for (Glyph g : glyphs) {
            if (g.codePoint == ' ') {
                if (!word.isEmpty()) {
                    words.add(word);
                    word = new ArrayList<>();
                }
            } else {
                word.add(g);
            }
        }
        if (!word.isEmpty()) {
            words.add(word);
        }

        List<List<Glyph>> lines = new ArrayList<>();
        List<Glyph> current = new ArrayList<>();
        int currentWidth = 0;
        for (List<Glyph> w : words) {
            int ww = width(w);
            Glyph first = w.get(0);
            Glyph space = new Glyph(' ', first.rgb, first.bold, first.italic);
            int spaceWidth = FontWidths.advance(' ', first.bold);
            if (!current.isEmpty() && currentWidth + spaceWidth + ww <= maxWidth) {
                current.add(space);
                current.addAll(w);
                currentWidth += spaceWidth + ww;
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(current);
                current = new ArrayList<>();
                currentWidth = 0;
            }
            for (Glyph g : w) {
                int gw = FontWidths.advance(g.codePoint, g.bold);
                if (!current.isEmpty() && currentWidth + gw > maxWidth) {
                    lines.add(current);
                    current = new ArrayList<>();
                    currentWidth = 0;
                }
                current.add(g);
                currentWidth += gw;
            }
        }
        if (!current.isEmpty()) {
            lines.add(current);
        }
        return lines;
    }

    private static List<List<Run>> runs(List<List<Glyph>> lines) {
        List<List<Run>> out = new ArrayList<>();
        for (List<Glyph> line : lines) {
            List<Run> runs = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            Glyph style = null;
            for (Glyph g : line) {
                if (style != null && !(Objects.equals(style.rgb, g.rgb) && style.bold == g.bold && style.italic == g.italic)) {
                    runs.add(new Run(text.toString(), style.rgb, style.bold, style.italic));
                    text.setLength(0);
                }
                style = g;
                text.appendCodePoint(g.codePoint);
            }
            if (style != null) {
                runs.add(new Run(text.toString(), style.rgb, style.bold, style.italic));
            }
            out.add(runs);
        }
        return out;
    }

    // ------------------------------------------------------------------
    //  Shapes (pixel art, opaque colours so overlapping rectangles never darken)
    // ------------------------------------------------------------------

    /** Rounded frame, corner radius 3, 1 px outline and a 2 px bottom edge as a soft shadow. */
    private static void frame(List<Rect> out, float x, float y, float w, float h, Settings.Palette p) {
        int o = p.outline();
        int f = p.fill();
        out.add(new Rect(x + 3, y - 1, w - 6, h + 1, o, Bubble.LAYER_OUTLINE));
        out.add(new Rect(x + 1, y, w - 2, h - 1, o, Bubble.LAYER_OUTLINE));
        out.add(new Rect(x, y + 3, w, h - 6, o, Bubble.LAYER_OUTLINE));
        out.add(new Rect(x + 3, y + 1, w - 6, h - 2, f, Bubble.LAYER_FILL));
        out.add(new Rect(x + 2, y + 2, w - 4, h - 4, f, Bubble.LAYER_FILL));
        out.add(new Rect(x + 1, y + 3, w - 2, h - 6, f, Bubble.LAYER_FILL));
    }

    /** Two round dots leading from the pet up to the bubble. */
    private static void dots(List<Rect> out, Settings.Palette p) {
        dot(out, -6.5f, 0, 5, p);
        dot(out, -4.5f, 6, 7, p);
    }

    private static void dot(List<Rect> out, float x, float y, float size, Settings.Palette p) {
        out.add(new Rect(x + 1, y, size - 2, size, p.outline(), Bubble.LAYER_OUTLINE));
        out.add(new Rect(x, y + 1, size, size - 2, p.outline(), Bubble.LAYER_OUTLINE));
        out.add(new Rect(x + 1, y + 1, size - 2, size - 2, p.fill(), Bubble.LAYER_FILL));
    }

    /** A speech pointer: the bubble "shouts" instead of thinking. Its fill opens the frame's bottom edge. */
    private static void pointer(List<Rect> out, Settings.Palette p) {
        int o = p.outline();
        int f = p.fill();
        out.add(new Rect(-1, 0, 2, 1, o, Bubble.LAYER_OUTLINE));
        out.add(new Rect(-2, 1, 4, 1, o, Bubble.LAYER_OUTLINE));
        out.add(new Rect(-3, 2, 6, 1, o, Bubble.LAYER_OUTLINE));
        out.add(new Rect(-4, 3, 8, 2, o, Bubble.LAYER_OUTLINE));
        out.add(new Rect(-1, 1, 2, 1, f, Bubble.LAYER_FILL));
        out.add(new Rect(-2, 2, 4, 1, f, Bubble.LAYER_FILL));
        out.add(new Rect(-3, 3, 6, POINTER_HEIGHT + 1 - 3, f, Bubble.LAYER_FILL));
    }

    // ------------------------------------------------------------------
    //  Animation
    // ------------------------------------------------------------------

    /** Pop in with a small overshoot, shake (danger only), float up slowly, pop out. */
    private static List<Keyframe> keyframes(Settings settings, boolean shake) {
        List<Keyframe> frames = new ArrayList<>();
        int life = settings.displayTicks;
        if (!settings.animation) {
            frames.add(new Keyframe(0, 1f, 0f, 0f, 0));
            return frames;
        }
        frames.add(new Keyframe(0, 0f, 0f, 0f, 0));
        frames.add(new Keyframe(2, 1.12f, 0f, 0f, 3));
        frames.add(new Keyframe(5, 1f, 0f, 0f, 2));
        int next = 7;
        if (shake) {
            float[] shakes = {3f, -3f, 2.5f, -2.5f, 1.5f, -1f, 0f};
            for (int i = 0; i < shakes.length; i++) {
                frames.add(new Keyframe(7 + i, 1f, shakes[i], 0f, 1));
            }
            next = 7 + shakes.length;
        }
        int out = Math.max(next + 2, life - 3);
        frames.add(new Keyframe(next, 1f, 0f, 0.07f, out - next));
        frames.add(new Keyframe(out, 0f, 0f, 0.07f, 3));
        return frames;
    }
}
