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
    /** The tier icon's width ({@code FamilyIcons.SIZE}). */
    private static final int ICON_W = at.koopro.wizardsandbeasts.client.gui.ui.FamilyIcons.SIZE;

    private static final int COLOR_BG_CARD    = WizardsPalette.PAGE_LIGHT;
    private static final int COLOR_HI         = 0xFFF6ECD0;
    private static final int COLOR_SHADOW     = WizardsPalette.PAGE_DEEP;
    private static final int COLOR_NAME       = WizardsPalette.PAGE_INK;


    private SpellCardWidget() {}

    /**
     * Draws a spell card at (x, y), {@code w} wide and {@link #CARD_H} tall: its tier icon and name, how many
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

        // The tier as its progression icon (UI renaissance P4) to the left of both lines; it replaced three pips.
        at.koopro.wizardsandbeasts.client.gui.ui.FamilyIcons.proficiency(g, proficiency, x + 3, y + 3,
                proficiency == Proficiency.NOVICE ? 0.5f : 1f);
        int textX = x + 3 + ICON_W + 3;
        int maxNameW = x + w - 3 - textX;
        at.koopro.wizardsandbeasts.client.gui.util.GuiText.drawFitted(g, font,
                at.koopro.wizardsandbeasts.client.gui.util.GuiText.resolve(spell.getDisplayName()),
                textX, y + 3, maxNameW, COLOR_NAME);

        String count = nextAt < 0 ? Integer.toString(hits) : hits + "/" + nextAt;
        g.drawString(font, count, textX, y + 13, WizardsPalette.PAGE_INK_2, false);
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
