package dev.petthoughts.core.bubble;

import dev.petthoughts.core.Trigger;
import java.util.List;

/**
 * A fully designed bubble, ready for a platform to draw with vanilla Text Displays.
 *
 * <p>Coordinates are in font pixels (1 px = 0.025 blocks at scale 1), y up, with the origin at the
 * tip of the tail, which sits just above the pet. Everything is billboarded around that point, so
 * x/y are screen directions.
 *
 * @param rects     coloured rectangles (outline, fill, tail), each drawn by one background-only Text Display
 * @param lines     the text, already wrapped; each line is a list of styled runs
 * @param textRgb   default text colour (runs without a colour use it)
 * @param textX     horizontal centre of the text block
 * @param textY     bottom of the text block
 * @param keyframes animation, in order; the bubble disappears after {@code lifetime} ticks
 */
public record Bubble(
        List<Rect> rects,
        List<List<Run>> lines,
        int textRgb,
        float textX,
        float textY,
        List<Keyframe> keyframes,
        int lifetime,
        Trigger.Tier tier,
        String plainText) {

    /** Drawing layers, back to front. */
    public static final int LAYER_OUTLINE = 0;
    public static final int LAYER_FILL = 1;
    public static final int LAYER_TEXT = 2;

    /** A filled rectangle: bottom-left corner (x, y), size (w, h). */
    public record Rect(float x, float y, float w, float h, int argb, int layer) {}

    /** A run of text with one style. A null colour means "the bubble's text colour". */
    public record Run(String text, Integer rgb, boolean bold, boolean italic) {}

    /**
     * State the whole bubble must reach {@code duration} ticks after tick {@code tick}.
     *
     * @param scale    size multiplier around the tail tip (0 = invisible, 1 = normal)
     * @param dx       horizontal offset in font pixels (shake)
     * @param rise     vertical offset in blocks (gentle float)
     */
    public record Keyframe(int tick, float scale, float dx, float rise, int duration) {}

    /** Distance between drawing layers, in blocks (towards the camera). */
    public static final float LAYER_GAP = 0.004f;
    /** Vanilla scale of text pixels, in blocks. */
    public static final float PIXEL = 0.025f;

    /**
     * Transformation of one element for a keyframe: {translationX, translationY, translationZ, scaleX, scaleY}.
     *
     * <p>A background-only Text Display showing a single space draws a 5 x 10 px rectangle from
     * x = -2 to 3 and y = 0 to 10 (vanilla geometry), which is stretched to the wanted size.
     *
     * @param size  global size (config scale, smaller for babies)
     * @param lift  height of the tail tip above the entity the bubble is attached to, in blocks
     */
    public static float[] rectTransform(Rect rect, Keyframe frame, float size, float lift) {
        float s = size * frame.scale();
        float unit = PIXEL * s;
        return new float[] {
            unit * (rect.x() + frame.dx() + 2f * rect.w() / 5f),
            lift + frame.rise() + unit * rect.y(),
            LAYER_GAP * rect.layer(),
            s * rect.w() / 5f,
            s * rect.h() / 10f
        };
    }

    /** Transformation of the text element (vanilla draws text centred half a pixel to the right). */
    public float[] textTransform(Keyframe frame, float size, float lift) {
        float s = size * frame.scale();
        float unit = PIXEL * s;
        return new float[] {
            unit * (textX - 0.5f + frame.dx()),
            lift + frame.rise() + unit * textY,
            LAYER_GAP * LAYER_TEXT,
            s,
            s
        };
    }
}
