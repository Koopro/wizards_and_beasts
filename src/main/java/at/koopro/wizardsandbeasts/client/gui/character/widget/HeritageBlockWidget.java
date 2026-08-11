package at.koopro.wizardsandbeasts.client.gui.character.widget;

import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSignatureSpellState;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Renders the heritage info block in the sheet's identity column. */
public final class HeritageBlockWidget {

    private static final int COLOR_HEADER = 0xFFDDB97A;
    private static final int COLOR_VALUE  = 0xFFEEDDBB;
    private static final int COLOR_NONE   = 0xFF776655;
    private static final int LINE_H       = 9;
    /** A label line, its value line, and a blank one so the four rows read as separate. */
    private static final int ROW_H        = LINE_H * 2 + 3;

    private HeritageBlockWidget() {}

    /**
     * Draws the heritage block.
     *
     * @param g GuiGraphics context
     * @param x left edge
     * @param y top edge
     * @param w available width
     */
    public static void draw(@NonNull GuiGraphics g, int x, int y, int w) {
        Font font = Minecraft.getInstance().font;

        @Nullable Heritage heritage = ClientHeritageDataState.get().getSelectedHeritage();
        @Nullable HeritageVariant variant = ClientHeritageDataState.get().getSelectedHeritageVariant();

        // Section header
        g.drawString(font, "Heritage", x, y, COLOR_HEADER, false);
        int cy = y + LINE_H;

        String typeName   = heritage != null ? heritage.getDisplayName()  : "—";
        String variantName = variant != null ? variant.getDisplayName()   : "—";
        String patronus   = resolvePatronus();
        String animagus   = resolveAnimagus();

        drawRow(g, font, x, cy, w, "Type",     typeName);    cy += ROW_H;
        drawRow(g, font, x, cy, w, "Variant",  variantName); cy += ROW_H;
        drawRow(g, font, x, cy, w, "Patronus", patronus);    cy += ROW_H;
        drawRow(g, font, x, cy, w, "Animagus", animagus);
    }

    /** Total pixel height consumed by this block. */
    public static int height() {
        return LINE_H + ROW_H * 4; // header + 4 two-line rows
    }

    // ── internals ───────────────────────────────────────────────────────────

    /**
     * A label above its value rather than beside it.
     *
     * <p>Side by side, the label ate the width the value needed: in a 92px column "Variant:
     * Pure-Blood" truncated to "Variant: Pure-Blo", and the values most worth reading — a heritage
     * name, a patronus form — are exactly the long ones. Stacking spends height instead, which this
     * column has in surplus, and lets the value use the full width.
     */
    private static void drawRow(@NonNull GuiGraphics g, @NonNull Font font,
                                int x, int y, int w,
                                @NonNull String key, @NonNull String value) {
        g.drawString(font, key, x, y, COLOR_HEADER, false);
        int valueColor = "—".equals(value) ? COLOR_NONE : COLOR_VALUE;
        g.drawString(font, font.plainSubstrByWidth(value, w), x, y + LINE_H, valueColor, false);
    }

    @NonNull
    private static String resolvePatronus() {
        String form = ClientSignatureSpellState.getPatronusForm();
        if (form == null || form.isEmpty()) return "—";
        // Convert resource-key style "namespace:animal" → "Animal"
        int colon = form.lastIndexOf(':');
        String raw = colon >= 0 ? form.substring(colon + 1) : form;
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1).replace('_', ' ');
    }

    @NonNull
    private static String resolveAnimagus() {
        String formId = ClientHeritageDataState.get().getActiveFormId();
        if (formId == null || formId.isEmpty()) return "—";
        int colon = formId.lastIndexOf(':');
        String raw = colon >= 0 ? formId.substring(colon + 1) : formId;
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1).replace('_', ' ');
    }
}
