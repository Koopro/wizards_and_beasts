package at.koopro.wizardsandbeasts.client.gui.character.widget;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.NonNull;

/** Renders a single spell card in the Spells tab grid. */
public final class SpellCardWidget {

    /** Preferred card width — the grid uses it to choose a column count, not to size cards. */
    public static final int CARD_W = 88;
    public static final int CARD_H = 28;
    /** Three 4px pips on a 6px pitch. */
    private static final int PIP_BLOCK_W = 16;

    private static final int COLOR_BG_CARD    = WizardsPalette.PAGE_LIGHT;
    private static final int COLOR_HI         = 0xFFF6ECD0;
    private static final int COLOR_SHADOW     = WizardsPalette.PAGE_DEEP;
    private static final int COLOR_NAME       = WizardsPalette.PAGE_INK;

    // Proficiency pip colors: NOVICE=grey, PROFICIENT=gold, MASTERED=red
    private static final int COLOR_PIP_NONE   = WizardsPalette.PAGE_DEEP;
    private static final int COLOR_PIP_NOVICE     = WizardsPalette.PAGE_INK_3;
    private static final int COLOR_PIP_PROFICIENT = WizardsPalette.GILT;
    private static final int COLOR_PIP_MASTERED   = WizardsPalette.WAX;

    private SpellCardWidget() {}

    /**
     * Draws a spell card at (x, y), {@code w} wide and {@link #CARD_H} tall.
     *
     * <p>The width is a parameter rather than {@link #CARD_W} because the card used to be
     * a fixed 88px in a column that is usually wider. That left the name clipped to 66px —
     * "Arresto Momentum" rendered as "Arresto Mom", "Avada Kedavra" as "Avada Kedav" —
     * while empty panel sat to the right of every card. {@code CARD_W} is now only the
     * *preferred* width the grid uses to pick a column count; the card fills whatever
     * column it lands in.
     *
     * @param g          GuiGraphics context
     * @param x          left edge
     * @param y          top edge
     * @param w          card width
     * @param spell      spell definition
     * @param proficiency player's proficiency for this spell
     */
    public static void draw(@NonNull GuiGraphics g, int x, int y, int w,
                            @NonNull Spell spell, @NonNull Proficiency proficiency) {
        Font font = Minecraft.getInstance().font;

        // Card background
        at.koopro.wizardsandbeasts.client.gui.McStylePanel.drawPanel(
                g, x, y, w, CARD_H, COLOR_BG_CARD, COLOR_HI, COLOR_SHADOW);

        // Spell name, truncated only if it genuinely will not fit beside the pips.
        int maxNameW = w - 4 - PIP_BLOCK_W - 2;
        String name = font.plainSubstrByWidth(
                at.koopro.wizardsandbeasts.client.gui.util.GuiText.resolve(spell.getDisplayName()), maxNameW);
        g.drawString(font, name, x + 3, y + 3, COLOR_NAME, false);

        // Mastery pips (3 dots on the right side)
        drawPips(g, x + w - 3 - PIP_BLOCK_W, y + 4, proficiency);

        // Category label
        String catLabel = formatCategory(spell.getCategory());
        // Category hues are the spell families' own; they are darkened onto the card, not replaced.
        g.drawString(font, catLabel, x + 3, y + 13,
                UiContrast.readableOn(spellCategoryColor(spell.getCategory()), COLOR_BG_CARD), false);

        // Proficiency tier label
        String tierLabel = proficiency.name().charAt(0)
                + proficiency.name().substring(1).toLowerCase();
        int tierW = font.width(tierLabel);
        g.drawString(font, tierLabel, x + w - 3 - tierW, y + 13,
                UiContrast.readableOn(tierLabelColor(proficiency), COLOR_BG_CARD), false);
    }

    // ── internals ──────────────────────────────────────────────────────────

    private static void drawPips(@NonNull GuiGraphics g, int x, int y,
                                 @NonNull Proficiency proficiency) {
        // 3 pips: NOVICE=1, PROFICIENT=2, MASTERED=3 filled
        int filled = proficiency == Proficiency.MASTERED ? 3
                   : proficiency == Proficiency.PROFICIENT ? 2
                   : 1;
        int pipColor = proficiency == Proficiency.MASTERED   ? COLOR_PIP_MASTERED
                     : proficiency == Proficiency.PROFICIENT ? COLOR_PIP_PROFICIENT
                     : COLOR_PIP_NOVICE;
        for (int i = 0; i < 3; i++) {
            int px = x + i * 6;
            int color = i < filled ? pipColor : COLOR_PIP_NONE;
            g.fill(px, y, px + 4, y + 4, color);
        }
    }

    @NonNull
    private static String formatCategory(@NonNull SpellCategory cat) {
        String raw = cat.getSerializedName().replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static int spellCategoryColor(@NonNull SpellCategory cat) {
        return switch (cat) {
            case COMBAT    -> 0xFFCC5544;
            case UTILITY   -> 0xFF55AA55;
            case DEFENSE   -> 0xFF4488CC;
            case DARK_ARTS -> 0xFF883388;
        };
    }

    private static int tierLabelColor(@NonNull Proficiency p) {
        return switch (p) {
            case NOVICE     -> WizardsPalette.PAGE_INK_2;
            case PROFICIENT -> WizardsPalette.GILT_DARK;
            case MASTERED   -> WizardsPalette.PAGE_RUBRIC;
        };
    }
}
