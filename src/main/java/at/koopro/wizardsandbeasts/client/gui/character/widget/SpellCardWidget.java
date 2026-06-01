package at.koopro.wizardsandbeasts.client.gui.character.widget;

import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.NonNull;

/** Renders a single spell card in the Spells tab grid. */
public final class SpellCardWidget {

    public static final int CARD_W = 88;
    public static final int CARD_H = 28;

    private static final int COLOR_BG_CARD    = 0xFF1E1408;
    private static final int COLOR_HI         = 0xFF3A2A14;
    private static final int COLOR_SHADOW     = 0xFF0A0500;
    private static final int COLOR_NAME       = 0xFFEEDDBB;
    private static final int COLOR_CATEGORY   = 0xFF998877;

    // Proficiency pip colors: NOVICE=grey, PROFICIENT=gold, MASTERED=red
    private static final int COLOR_PIP_NONE   = 0xFF443322;
    private static final int COLOR_PIP_NOVICE     = 0xFF887766;
    private static final int COLOR_PIP_PROFICIENT = 0xFFCC9933;
    private static final int COLOR_PIP_MASTERED   = 0xFFCC3322;

    private SpellCardWidget() {}

    /**
     * Draws a spell card at (x, y). Width and height are CARD_W × CARD_H.
     *
     * @param g          GuiGraphics context
     * @param x          left edge
     * @param y          top edge
     * @param spell      spell definition
     * @param proficiency player's proficiency for this spell
     */
    public static void draw(@NonNull GuiGraphics g, int x, int y,
                            @NonNull Spell spell, @NonNull Proficiency proficiency) {
        Font font = Minecraft.getInstance().font;

        // Card background
        at.koopro.wizardsandbeasts.client.gui.McStylePanel.drawPanel(
                g, x, y, CARD_W, CARD_H, COLOR_BG_CARD, COLOR_HI, COLOR_SHADOW);

        // Spell name (truncated to fit)
        int maxNameW = CARD_W - 4 - 18; // leave space for 3 pips (6px each)
        String name = font.plainSubstrByWidth(spell.getDisplayName(), maxNameW);
        g.drawString(font, name, x + 3, y + 3, COLOR_NAME, false);

        // Mastery pips (3 dots on the right side)
        drawPips(g, x + CARD_W - 19, y + 4, proficiency);

        // Category label
        String catLabel = formatCategory(spell.getCategory());
        g.drawString(font, catLabel, x + 3, y + 13, spellCategoryColor(spell.getCategory()), false);

        // Proficiency tier label
        String tierLabel = proficiency.name().charAt(0)
                + proficiency.name().substring(1).toLowerCase();
        int tierW = font.width(tierLabel);
        g.drawString(font, tierLabel, x + CARD_W - 3 - tierW, y + 13, tierLabelColor(proficiency), false);
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
            case NOVICE     -> 0xFF887766;
            case PROFICIENT -> 0xFFCC9933;
            case MASTERED   -> 0xFFCC3322;
        };
    }
}
