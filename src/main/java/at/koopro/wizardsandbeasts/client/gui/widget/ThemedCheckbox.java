package at.koopro.wizardsandbeasts.client.gui.widget;

import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * A ticked box and its label, the box on the left.
 *
 * <p>For one of several independent yes/no choices in a form that is confirmed as a whole ("Include
 * creatures", "Include spells" before Export). A setting that applies the moment it is clicked is a
 * {@link ThemedToggle}. The tick is an ink check drawn in the box, so it reads on any paper.
 */
@NullMarked
public final class ThemedCheckbox extends AbstractButton {

    private static final int BOX = 10;

    private final UiSurface surface;
    private final Consumer<Boolean> onChange;
    private boolean checked;

    public ThemedCheckbox(int x, int y, int w, int h, Component label, boolean checked, UiSurface surface,
                          Consumer<Boolean> onChange) {
        super(x, y, w, h, label);
        this.checked = checked;
        this.surface = surface;
        this.onChange = onChange;
    }

    public boolean checked() {
        return checked;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        checked = !checked;
        onChange.accept(checked);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        boolean hot = active && (isHovered() || isFocused());
        int bx = getX();
        int by = getY() + (getHeight() - BOX) / 2;
        int edge = hot ? surface.accent() : surface.edge();
        g.fill(bx, by, bx + BOX, by + BOX, edge);
        g.fill(bx + 1, by + 1, bx + BOX - 1, by + BOX - 1, surface.dark() ? surface.well() : 0xFFF6ECD0);
        if (checked) {
            int ink = active ? surface.ink() : surface.disabled();
            // A two-stroke tick, one texel thick.
            int[][] tick = {{2, 5}, {3, 6}, {4, 7}, {5, 6}, {6, 5}, {7, 4}, {8, 3}};
            for (int[] p : tick) {
                g.fill(bx + p[0], by + p[1], bx + p[0] + 1, by + p[1] + 1, ink);
                g.fill(bx + p[0], by + p[1] - 1, bx + p[0] + 1, by + p[1], ink);
            }
        }
        int colour = !active ? surface.disabled() : hot ? surface.ink() : surface.ink2();
        UiText.drawFit(g, font, surface, getMessage(), bx + BOX + WizardsMetrics.SPACE_S,
                getY() + (getHeight() - font.lineHeight) / 2 + 1, getWidth() - BOX - WizardsMetrics.SPACE_S, colour);
    }

    @Override
    protected void renderDefaultLabel(ActiveTextCollector collector) {
        // drawn in renderContents
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.empty().append(getMessage()).append(": ")
                .append(checked ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF));
    }
}
