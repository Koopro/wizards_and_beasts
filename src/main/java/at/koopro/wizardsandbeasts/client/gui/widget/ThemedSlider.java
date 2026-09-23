package at.koopro.wizardsandbeasts.client.gui.widget;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/**
 * A slider ruled into the parchment: an inset groove, the travelled part washed in gilt, a gilt
 * tab for a handle, and the label in ink.
 *
 * <p>Subclass it exactly as you would {@link AbstractSliderButton} — {@code updateMessage} and
 * {@code applyValue} are still yours. Only drawing is replaced: vanilla blits two stone sprites and
 * draws its label through the widget text renderer, which shadows it.
 */
public abstract class ThemedSlider extends AbstractSliderButton {

    /** Vanilla's own handle width, so drag maths in the superclass lines up with what is drawn. */
    private static final int HANDLE_W = 8;
    private static final int LABEL_PAD = 4;
    private static final int GLYPH_H = 8;

    protected ThemedSlider(int x, int y, int w, int h, @NonNull Component message, double value) {
        super(x, y, w, h, message, value);
    }

    @Override
    public void renderWidget(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        McStylePanel.drawThemedInset(g, x, y, w, h);

        int handleX = x + (int) (value * (w - HANDLE_W));
        if (active && handleX > x + 2) {
            g.fill(x + 2, y + 2, handleX, y + h - 2, WizardsPalette.PAGE_SELECT);
        }

        boolean lit = active && (isHovered() || isFocused());
        int face = !active ? WizardsPalette.PAGE_SHADE : lit ? WizardsPalette.GILT_LIGHT : WizardsPalette.GILT;
        int edge = active ? WizardsPalette.GILT_DARK : WizardsPalette.PAGE_INK_3;
        g.fill(handleX, y, handleX + HANDLE_W, y + h, edge);
        g.fill(handleX + 1, y + 1, handleX + HANDLE_W - 1, y + h - 1, face);
        // Lit from the top-left, like every raised thing in the kit.
        g.fill(handleX + 1, y + 1, handleX + HANDLE_W - 1, y + 2, WizardsPalette.GILT_LIGHT);

        int ink = active ? WizardsPalette.PAGE_INK : WizardsPalette.PAGE_INK_3;
        GuiText.drawFittedCentered(g, Minecraft.getInstance().font, getMessage().getString(),
                x + LABEL_PAD, y + (h - GLYPH_H) / 2, w - 2 * LABEL_PAD, ink);
    }
}
