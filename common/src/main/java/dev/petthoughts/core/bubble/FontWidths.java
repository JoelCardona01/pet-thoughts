package dev.petthoughts.core.bubble;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Advance of every glyph of the vanilla font, so the server can measure text exactly like the
 * client will draw it (the bubble frame is built around the measured text).
 *
 * <p>The table is generated from the client font by {@code tools/gen_font_widths.py}.
 */
public final class FontWidths {

    private static int[] firsts = new int[0];
    private static int[] lasts = new int[0];
    private static byte[] widths = new byte[0];
    private static boolean loaded;

    private FontWidths() {
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        List<int[]> runs = new ArrayList<>();
        try (InputStream in = FontWidths.class.getClassLoader().getResourceAsStream("petthoughts/font_widths.txt")) {
            if (in == null) {
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] parts = line.split(" ");
                runs.add(new int[] {Integer.parseInt(parts[0], 16), Integer.parseInt(parts[1], 16), Integer.parseInt(parts[2])});
            }
        } catch (IOException | RuntimeException e) {
            return;
        }
        firsts = new int[runs.size()];
        lasts = new int[runs.size()];
        widths = new byte[runs.size()];
        for (int i = 0; i < runs.size(); i++) {
            firsts[i] = runs.get(i)[0];
            lasts[i] = runs.get(i)[1];
            widths[i] = (byte) runs.get(i)[2];
        }
    }

    /** Advance in font pixels (bold adds one pixel, like the client). */
    public static int advance(int codePoint, boolean bold) {
        load();
        int lo = 0;
        int hi = firsts.length - 1;
        int width = codePoint >= 0x2E80 ? 9 : 6; // estimate for glyphs the table does not know
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (codePoint < firsts[mid]) {
                hi = mid - 1;
            } else if (codePoint > lasts[mid]) {
                lo = mid + 1;
            } else {
                width = widths[mid];
                break;
            }
        }
        return width + (bold && width > 0 ? 1 : 0);
    }
}
