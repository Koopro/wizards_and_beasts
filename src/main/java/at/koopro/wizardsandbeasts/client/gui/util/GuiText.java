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

    private GuiText() {
    }

    /**
     * Draws {@code text} centred in {@code [x, x + boxW]}, shortened with an ellipsis if it would not fit.
     *
     * <p>This used to shrink the glyphs down to 65%. At GUI scale 4 that is a five-pixel-tall word, which
     * nobody reads; an ellipsis keeps what is shown legible and the screen can put the whole text in a
     * tooltip (see {@code UiText}, {@code UiTooltips.ifTruncated}).
     */
    public static void drawFittedCentered(GuiGraphics g, Font font, String text,
                                          int x, int y, int boxW, int colour) {
        String shown = at.koopro.wizardsandbeasts.client.gui.ui.UiText.fit(font, text, boxW);
        g.drawString(font, shown, x + (boxW - font.width(shown)) / 2, y, colour, false);
    }

    /** Draws {@code text} left-aligned at {@code x}, shortened with an ellipsis to fit {@code boxW}. */
    public static void drawFitted(GuiGraphics g, Font font, String text,
                                  int x, int y, int boxW, int colour) {
        g.drawString(font, at.koopro.wizardsandbeasts.client.gui.ui.UiText.fit(font, text, boxW), x, y, colour, false);
    }
}
