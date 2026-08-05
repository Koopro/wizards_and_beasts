package at.koopro.wizardsandbeasts.client.gui.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Label drawing that respects the width it was given.
 *
 * <p>Vanilla's own buttons never overflow: {@code AbstractButton.renderDefaultLabel} routes
 * through {@code renderScrollingStringOverContents}, which scrolls a label that is too wide
 * rather than letting it spill. Panels and tab strips drawn by hand get no such treatment —
 * a plain {@code drawString} at a centred x happily runs out past both edges the moment the
 * text is wider than its box, which is what happens to longer translations of a label that
 * was sized against English.
 *
 * <p>These helpers shrink the glyphs instead of clipping or truncating them, so the full
 * label stays readable.
 */
public final class GuiText {

    /**
     * Resolves a display string that may be either a lang key or literal prose.
     *
     * <p>Datapack text is mid-migration: some fields hold keys, some still hold English.
     * {@code I18n.exists} decides, and a literal falls through unchanged, so one call
     * handles both. Anything that *searches or sorts* display text must go through here --
     * comparing raw fields would match and order by key rather than by what the player
     * reads, in every language including English.
     */
    public static String resolve(String keyOrLiteral) {
        return keyOrLiteral != null && net.minecraft.client.resources.language.I18n.exists(keyOrLiteral)
                ? net.minecraft.client.resources.language.I18n.get(keyOrLiteral)
                : keyOrLiteral;
    }

    /**
     * Below this the text is too small to read, so it is left at this scale and allowed to
     * clip. A label needing to shrink past ~65% signals a layout problem, not a text problem.
     */
    private static final float MIN_SCALE = 0.65F;

    private GuiText() {
    }

    /** Draws {@code text} centred in {@code [x, x + boxW]}, shrunk if it would not fit. */
    public static void drawFittedCentered(GuiGraphics g, Font font, String text,
                                          int x, int y, int boxW, int colour) {
        int textW = font.width(text);
        if (textW <= boxW) {
            g.drawString(font, text, x + (boxW - textW) / 2, y, colour, false);
            return;
        }
        float scale = Math.max(MIN_SCALE, boxW / (float) textW);
        // Centre on the scaled width, then scale about this label's own origin so the
        // surrounding panel geometry is untouched.
        int drawX = x + Math.round((boxW - textW * scale) / 2.0F);
        drawScaled(g, font, text, drawX, y, scale, colour);
    }

    /** Draws {@code text} left-aligned at {@code x}, shrunk to fit {@code boxW}. */
    public static void drawFitted(GuiGraphics g, Font font, String text,
                                  int x, int y, int boxW, int colour) {
        int textW = font.width(text);
        if (textW <= boxW) {
            g.drawString(font, text, x, y, colour, false);
            return;
        }
        drawScaled(g, font, text, x, y, Math.max(MIN_SCALE, boxW / (float) textW), colour);
    }

    private static void drawScaled(GuiGraphics g, Font font, String text,
                                   int x, int y, float scale, int colour) {
        var pose = g.pose();
        pose.pushMatrix();
        // Keep (x, y) fixed while scaling, so the caller's anchor still means what it says.
        pose.translate(x * (1.0F - scale), y * (1.0F - scale));
        pose.scale(scale, scale);
        g.drawString(font, text, x, y, colour, false);
        pose.popMatrix();
    }
}
