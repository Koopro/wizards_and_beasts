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

/** Renders a single spell card in the Magic page grid. */
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
     * Draws a spell card at (x, y), {@code w} wide and {@link #CARD_H} tall: the name and its mastery pips, how many
     * successful casts the spell has toward its next tier with the tier's name, and that progress as a bar.
     *
     * <p>The width is a parameter rather than {@link #CARD_W} because the card used to be a fixed 88px in a column
     * that is usually wider, which clipped "Arresto Momentum" to "Arresto Mom". {@code CARD_W} is only the
     * <em>preferred</em> width the grid uses to pick a column count; the card fills whatever column it lands in.
     *
     * <p>The second line used to name the spell's category. The Magic page groups cards under their category
     * (2026-10-15), so the line now says the one thing the card did not: how far the spell is from its next tier.
     *
     * @param hits      successful casts so far
     * @param nextAt    successful casts the next tier asks for, or -1 once mastered
     * @param progress  progress from this tier to the next, 0..1
     */
    public static void draw(@NonNull GuiGraphics g, int x, int y, int w, @NonNull Spell spell,
                            @NonNull Proficiency proficiency, int hits, int nextAt, float progress) {
        Font font = Minecraft.getInstance().font;

        at.koopro.wizardsandbeasts.client.gui.McStylePanel.drawPanel(
                g, x, y, w, CARD_H, COLOR_BG_CARD, COLOR_HI, COLOR_SHADOW);

        // The spell's own colour as a spine down the left edge: the family it belongs to at a glance.
        g.fill(x + 1, y + 2, x + 2, y + CARD_H - 2,
                UiContrast.readableOn(spellCategoryColor(spell.getCategory()), COLOR_BG_CARD, 3.0));

        int maxNameW = w - 4 - PIP_BLOCK_W - 2;
        at.koopro.wizardsandbeasts.client.gui.util.GuiText.drawFitted(g, font,
                at.koopro.wizardsandbeasts.client.gui.util.GuiText.resolve(spell.getDisplayName()),
                x + 3, y + 3, maxNameW, COLOR_NAME);
        drawPips(g, x + w - 3 - PIP_BLOCK_W, y + 4, proficiency);

        String count = nextAt < 0 ? Integer.toString(hits) : hits + "/" + nextAt;
        g.drawString(font, count, x + 3, y + 13, WizardsPalette.PAGE_INK_2, false);
        String tierLabel = net.minecraft.network.chat.Component.translatable(
                "gui.wizards_and_beasts.character_sheet.magic.tier." + proficiency.name().toLowerCase(java.util.Locale.ROOT))
                .getString();
        int tierW = font.width(tierLabel);
        g.drawString(font, tierLabel, x + w - 3 - tierW, y + 13,
                UiContrast.readableOn(tierLabelColor(proficiency), COLOR_BG_CARD), false);

        at.koopro.wizardsandbeasts.client.gui.ui.Ui.bar(g, at.koopro.wizardsandbeasts.client.gui.ui.UiSurface.PAGE,
                x + 3, y + 22, w - 6, 3, progress, tierLabelColor(proficiency), -1.0f);
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

    /** The spell family's own hue, for marks drawn on paper through {@code UiContrast}. */
    public static int spellCategoryColor(@NonNull SpellCategory cat) {
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
