package at.koopro.wizardsandbeasts.client.gui.widget;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@code ◀ [ label ] ▶} selector over a fixed list of options.
 *
 * <p>Two arrow buttons step the selection, wrapping at both ends; the centre is a drawn label panel
 * rather than a third button. That is deliberate — a clickable centre with no menu behind it is an
 * affordance that does nothing, and the screens using this already bind arrow keys to the same
 * cycling.
 *
 * <p>The widget owns no selection state. It is handed the current value on construction, reports
 * changes through {@code onChange}, and the owning screen rebuilds. Generic because a screen
 * typically stacks several of these at different types.
 *
 * <p>Nothing here is textured beyond the shared {@code gui/theme/} panel sprites: the arrows are
 * drawn as triangles so the widget costs no new art and inherits {@link WizardsPalette} for free.
 */
public final class CyclerWidget<T> {

    /** Arrow hit-box. Tall enough to hit comfortably, narrow enough to leave the label room. */
    public static final int ARROW_W = 9;
    public static final int ARROW_H = 13;
    /** Clear space between an arrow and the label panel. */
    private static final int GAP = 3;

    private final List<T> options;
    private final Function<T, String> labeller;
    private final Consumer<T> onChange;

    private final ArrowButton left;
    private final ArrowButton right;

    private int index;
    private int x, y, w, h;

    public CyclerWidget(@NonNull List<T> options, @NonNull T current,
                        @NonNull Function<T, String> labeller, @NonNull Consumer<T> onChange) {
        this.options = options;
        this.labeller = labeller;
        this.onChange = onChange;
        int found = options.indexOf(current);
        this.index = found < 0 ? 0 : found;
        this.left = new ArrowButton(false, () -> step(-1));
        this.right = new ArrowButton(true, () -> step(1));
        // A one-option cycler has nowhere to go. Greying the arrows says so without a tooltip.
        boolean navigable = options.size() > 1;
        this.left.active = navigable;
        this.right.active = navigable;
    }

    /** Positions the widget and its arrows. Call from the screen's layout pass. */
    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        int arrowY = y + (h - ARROW_H) / 2;
        left.setPosition(x, arrowY);
        left.setSize(ARROW_W, ARROW_H);
        right.setPosition(x + w - ARROW_W, arrowY);
        right.setSize(ARROW_W, ARROW_H);
    }

    /** The two arrow buttons, for the screen to register as renderable widgets. */
    public List<AbstractButton> buttons() {
        return List.of(left, right);
    }

    public T selected() {
        return options.get(index);
    }

    private void step(int direction) {
        if (options.size() <= 1) return;
        index = Math.floorMod(index + direction, options.size());
        onChange.accept(options.get(index));
    }

    /**
     * Draws the label panel. The arrows draw themselves as registered widgets, so this only paints
     * what sits between them.
     */
    public void renderLabel(@NonNull GuiGraphics g) {
        Font font = Minecraft.getInstance().font;
        int panelX = x + ARROW_W + GAP;
        int panelW = w - 2 * (ARROW_W + GAP);
        if (panelW <= 0) return;

        McStylePanel.drawThemedPanel(g, panelX, y, panelW, h);

        String label = trim(font, labeller.apply(options.get(index)), panelW - 6);
        g.drawString(font, label,
                panelX + (panelW - font.width(label)) / 2,
                y + (h - font.lineHeight) / 2,
                WizardsPalette.TEXT, false);
    }

    private static String trim(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "..";
        while (!text.isEmpty() && font.width(text + ellipsis) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }

    /** A procedural triangle arrow. Its own class so it can carry hover state for the tint. */
    private static final class ArrowButton extends AbstractButton {

        private final boolean pointsRight;
        private final Runnable action;

        private ArrowButton(boolean pointsRight, Runnable action) {
            super(0, 0, ARROW_W, ARROW_H, Component.empty());
            this.pointsRight = pointsRight;
            this.action = action;
        }

        @Override
        public void onPress(@NonNull InputWithModifiers input) {
            action.run();
        }

        @Override
        protected void renderContents(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int tint = !active ? WizardsPalette.PIP_OFF
                    : (isHovered() || isFocused()) ? WizardsPalette.BRASS_HI
                    : WizardsPalette.BRASS;
            drawTriangle(g, getX(), getY(), getWidth(), getHeight(), pointsRight, tint);
        }

        /**
         * A filled triangle, one vertical slice per column, tapering toward the point. Drawn rather
         * than blitted so the widget carries no texture dependency.
         */
        private static void drawTriangle(GuiGraphics g, int x, int y, int w, int h,
                                         boolean pointsRight, int color) {
            int cy = y + h / 2;
            for (int i = 0; i < w; i++) {
                // Distance from the base column, so the slice shrinks as it approaches the tip.
                int fromBase = pointsRight ? i : w - 1 - i;
                int half = Math.max(1, (h / 2) - (fromBase * h) / (2 * w));
                g.fill(x + i, cy - half, x + i + 1, cy + half, color);
            }
        }

        @Override
        protected void renderDefaultLabel(@NonNull ActiveTextCollector collector) {
            // The arrow is the label.
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
