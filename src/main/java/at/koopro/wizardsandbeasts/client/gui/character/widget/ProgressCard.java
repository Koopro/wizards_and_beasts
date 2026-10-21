package at.koopro.wizardsandbeasts.client.gui.character.widget;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.character.SheetIcons;
import at.koopro.wizardsandbeasts.client.gui.character.SheetTab;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * One "what am I becoming" card on the Identity page: the page's glyph, a title with its headline value, one line
 * of where things stand, and — when there is a road with a length — a bar showing how far along it the wizard is.
 *
 * <p>Same paper card as the spell cards ({@link SpellCardWidget}), so the sheet carries one card shape. The card
 * is a link: clicking it opens the page it summarises, and hovering it says what the next step is.
 */
@NullMarked
public final class ProgressCard {

    private static final UiSurface S = UiSurface.PAGE;
    public static final int HEIGHT = 30;
    private static final int TEXT_X = SheetIcons.SIZE + 6;
    private static final int COLOR_BG = WizardsPalette.PAGE_LIGHT;
    private static final int COLOR_HI = 0xFFF6ECD0;
    private static final int COLOR_SHADOW = WizardsPalette.PAGE_DEEP;
    private static final int COLOR_FILL = WizardsPalette.GILT_DARK;

    /**
     * What a card says.
     *
     * @param value       the headline on the right ("Rank 2 of 5", "Loyal"), in {@code valueColour}
     * @param detail      one line under the title: the next step, or why there is nothing here yet
     * @param fraction    progress along the road, 0..1; negative for a card with no road (nothing to fill)
     * @param marker      a threshold on the bar (the next band), 0..1; negative for none
     * @param tooltip     the hover card, name first
     */
    public record Content(SheetTab target, Component title, Component value, int valueColour, Component detail,
                          float fraction, float marker, List<Component> tooltip) {}

    private ProgressCard() {}

    public static void draw(GuiGraphics g, Font font, int x, int y, int w, Content card, boolean hovered) {
        McStylePanel.drawPanel(g, x, y, w, HEIGHT, COLOR_BG, COLOR_HI, COLOR_SHADOW);
        if (hovered) {
            g.fill(x + 1, y + 1, x + w - 1, y + HEIGHT - 1, S.hoverWash());
        }
        SheetIcons.draw(g, card.target(), x + 3, y + (HEIGHT - SheetIcons.SIZE) / 2);

        int tx = x + TEXT_X;
        int right = x + w - WizardsMetrics.SPACE_S;
        int valueW = font.width(card.value());
        UiText.drawFit(g, font, S, card.title(), tx, y + 3, Math.max(0, right - tx - valueW - WizardsMetrics.SPACE_S),
                S.ink());
        UiText.drawRight(g, font, S, card.value(), right, y + 3, card.valueColour());
        UiText.drawFit(g, font, S, card.detail(), tx, y + 13, right - tx, S.ink2());
        if (card.fraction() >= 0.0f) {
            Ui.bar(g, S, tx, y + 23, right - tx, 4, card.fraction(), COLOR_FILL, card.marker());
        }
    }
}
