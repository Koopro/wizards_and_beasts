package at.koopro.wizardsandbeasts.client.gui.widget;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.ui.NightChrome;
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
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.function.BooleanSupplier;

/**
 * A filter or tab on the night sheet: a button whose face stays the arcane "chosen" face while
 * {@code selected} answers true.
 *
 * <p>A real widget rather than a painted rectangle, so Tab reaches it and Enter or Space presses it.
 * The selection is read, not stored: the screen owns which filter is on, and a chip cannot disagree
 * with it.
 */
public final class NightChip extends AbstractButton {

    private static final int EDGE_PAD = 4;

    private final Runnable action;
    private final BooleanSupplier selected;

    public NightChip(int x, int y, int w, int h, @NonNull Component label,
                     @NonNull Runnable action, @NonNull BooleanSupplier selected) {
        super(x, y, w, h, label);
        this.action = action;
        this.selected = selected;
    }

    /** Width a chip needs for {@code label}: the text plus the face's padding. */
    public static int widthFor(Font font, Component label) {
        return font.width(label) + 2 * EDGE_PAD + 2;
    }

    @Override
    public void onPress(@NonNull InputWithModifiers input) {
        action.run();
    }

    @Override
    protected void renderContents(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean on = selected.getAsBoolean();
        McStylePanel.ControlState state = McStylePanel.ControlState.of(active, isHovered() || isFocused());
        NightChrome.button(g, getX(), getY(), getWidth(), getHeight(), state, on);
        Font font = Minecraft.getInstance().font;
        UiSurface s = UiSurface.NIGHT_SHEET;
        int colour = !active ? s.disabled() : on ? s.heading() : s.ink2();
        String text = UiText.fit(font, getMessage().getString(), getWidth() - 2 * EDGE_PAD);
        g.drawString(font, text, getX() + (getWidth() - font.width(text)) / 2,
                getY() + (getHeight() - font.lineHeight) / 2 + 1, colour, s.shadow());
    }

    @Override
    protected void renderDefaultLabel(@NonNull ActiveTextCollector collector) {
        // renderContents draws the label in the sheet's inks.
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
        if (selected.getAsBoolean()) {
            output.add(NarratedElementType.HINT, Component.translatable("gui.wizards_and_beasts.chip.selected"));
        }
    }
}
