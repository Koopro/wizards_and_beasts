package at.koopro.wizardsandbeasts.client.gui.widget;

import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
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
 * An on/off switch: a label on the left, a small track with a sliding stud on the right.
 *
 * <p>For a setting that takes effect at once ("Show hints"). A choice that is applied later with the rest
 * of a form is a {@link ThemedCheckbox}; a choice among more than two is a {@link ThemedDropdown} or a
 * {@link CyclerWidget}. The whole row is the hit area, not just the track.
 */
@NullMarked
public final class ThemedToggle extends AbstractButton {

    private static final int TRACK_W = 18;
    private static final int TRACK_H = 9;

    private final UiSurface surface;
    private final Consumer<Boolean> onChange;
    private boolean on;

    public ThemedToggle(int x, int y, int w, int h, Component label, boolean on, UiSurface surface,
                        Consumer<Boolean> onChange) {
        super(x, y, w, h, label);
        this.on = on;
        this.surface = surface;
        this.onChange = onChange;
    }

    public boolean value() {
        return on;
    }

    public void setValue(boolean value) {
        this.on = value;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        on = !on;
        onChange.accept(on);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        boolean hot = active && (isHovered() || isFocused());
        int tx = getX() + getWidth() - TRACK_W;
        int ty = getY() + (getHeight() - TRACK_H) / 2;
        int trackFill = !active ? surface.disabled() : on ? surface.good() : surface.ink3();
        g.fill(tx + 1, ty, tx + TRACK_W - 1, ty + TRACK_H, surface.edge());
        g.fill(tx, ty + 1, tx + TRACK_W, ty + TRACK_H - 1, surface.edge());
        g.fill(tx + 1, ty + 1, tx + TRACK_W - 1, ty + TRACK_H - 1, (0xA0 << 24) | (trackFill & 0xFFFFFF));
        int studX = on ? tx + TRACK_W - TRACK_H : tx;
        g.fill(studX + 1, ty + 1, studX + TRACK_H - 1, ty + TRACK_H - 1, hot ? WizardsPalette.GILT_LIGHT : WizardsPalette.PAGE_LIGHT);
        g.fill(studX + 1, ty + TRACK_H - 2, studX + TRACK_H - 1, ty + TRACK_H - 1, WizardsPalette.PAGE_DEEP);
        int colour = !active ? surface.disabled() : hot ? surface.ink() : surface.ink2();
        UiText.drawFit(g, font, surface, getMessage(), getX(), getY() + (getHeight() - font.lineHeight) / 2 + 1,
                getWidth() - TRACK_W - WizardsMetrics.SPACE_M, colour);
    }

    @Override
    protected void renderDefaultLabel(ActiveTextCollector collector) {
        // drawn in renderContents
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.empty().append(getMessage()).append(": ")
                .append(on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF));
    }
}
