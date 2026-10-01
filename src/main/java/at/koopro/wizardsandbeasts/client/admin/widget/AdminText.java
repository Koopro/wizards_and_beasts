package at.koopro.wizardsandbeasts.client.admin.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NullMarked;

/**
 * Text helpers for the dense admin layout: clip with an ellipsis rather than overprint or shrink, and centre
 * without a shadow.
 */
@NullMarked
public final class AdminText {

    private static final String ELLIPSIS = "…";

    private AdminText() {}

    public static String clip(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ELLIPSIS))) + ELLIPSIS;
    }

    /**
     * What an empty list says, the same everywhere: "nothing matches" when a filter hid everything, the list's own
     * empty message when the server answered with nothing, "waiting for the server" until it has answered.
     */
    public static Component emptyList(boolean hasEntries, boolean loaded, Component emptyMessage) {
        if (hasEntries) {
            return Component.translatable("admin.wizards_and_beasts.filter.empty");
        }
        return loaded ? emptyMessage : Component.translatable("admin.wizards_and_beasts.status.loading");
    }

    /**
     * Centred text without vanilla's drop shadow. {@code drawCenteredString} always shadows, which is right
     * over the world and smears dark ink on parchment.
     */
    public static void centred(GuiGraphics g, Font font, Component text, int centreX, int y, int color) {
        g.drawString(font, text, centreX - font.width(text) / 2, y, color, false);
    }

    public static void centred(GuiGraphics g, Font font, FormattedCharSequence text, int centreX, int y, int color) {
        g.drawString(font, text, centreX - font.width(text) / 2, y, color, false);
    }
}
