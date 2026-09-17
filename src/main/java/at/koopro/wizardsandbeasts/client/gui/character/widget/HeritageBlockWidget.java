package at.koopro.wizardsandbeasts.client.gui.character.widget;

import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSignatureSpellState;
import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageTraits;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Renders the heritage block in the sheet's identity column: who this character is.
 *
 * <p>Heritage, lineage, any condition they carry, and then their traits, magical affinities and special
 * characteristics by name ({@link HeritageTraits}). The block used to print heritage, variant, patronus and animagus
 * and stop there, which told a player their label and nothing about what it meant.
 */
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

        PlayerHeritageData data = ClientHeritageDataState.get();
        ConditionOrigin condition = data.getCondition();

        drawRow(g, font, x, cy, w, "Heritage", heritage != null ? heritage.getDisplayName() : "—");
        cy += ROW_H;
        drawRow(g, font, x, cy, w, "Lineage", variant != null ? variant.getDisplayName() : "—");
        cy += ROW_H;
        if (condition != null) {
            // Named, not hidden: the character knows. A condition is a fact about them, like a scar.
            drawRow(g, font, x, cy, w, "Condition",
                    Component.translatable(condition.condition().getTranslationKey()).getString()
                            + " (" + Component.translatable(condition.getTranslationKey()).getString() + ")");
            cy += ROW_H;
        }
        drawRow(g, font, x, cy, w, "Patronus", resolvePatronus());
        cy += ROW_H;
        drawRow(g, font, x, cy, w, "Animagus", resolveAnimagus());
        cy += ROW_H;

        for (HeritageTraits.Kind kind : HeritageTraits.Kind.values()) {
            java.util.List<HeritageTraits.Trait> traits = HeritageTraits.of(data, kind);
            if (traits.isEmpty()) {
                continue;
            }
            g.drawString(font, Component.translatable(kind.getHeadingKey()), x, cy, COLOR_HEADER, false);
            cy += LINE_H;
            for (HeritageTraits.Trait trait : traits) {
                g.drawString(font, font.plainSubstrByWidth(
                                Component.translatable(trait.getNameKey()).getString(), w - 4),
                        x + 4, cy, COLOR_VALUE, false);
                cy += LINE_H;
            }
            cy += 2;
        }
    }

    /**
     * Total pixel height consumed by this block, for the identity column's layout.
     *
     * <p>Computed from the same state {@link #draw} reads rather than fixed, because a character's trait list is
     * exactly as long as their traits: a bitten half-blood prints two more lines than they did yesterday, and a
     * fixed height would have the next block drawn over them.
     */
    public static int height() {
        PlayerHeritageData data = ClientHeritageDataState.get();
        int rows = data.getCondition() == null ? 4 : 5;
        int height = LINE_H + ROW_H * rows;
        for (HeritageTraits.Kind kind : HeritageTraits.Kind.values()) {
            int traits = HeritageTraits.of(data, kind).size();
            if (traits > 0) {
                height += LINE_H * (traits + 1) + 2;
            }
        }
        return height;
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
