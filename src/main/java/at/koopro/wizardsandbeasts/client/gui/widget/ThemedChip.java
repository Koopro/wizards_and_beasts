package at.koopro.wizardsandbeasts.client.gui.widget;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
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
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * A filter or option on a parchment screen: a paper tab that wears the select wash while {@code selected}
 * answers true -- a category filter, a lineage.
 *
 * <p>A real widget rather than a painted rectangle, so Tab reaches it and Enter or Space presses it. The
 * selection is read, not stored: the screen owns which option is on, and a chip cannot disagree with it.
 * A chip that cannot be pressed never shows as chosen.
 */
public final class ThemedChip extends AbstractButton {

    private static final int EDGE_PAD = 4;

    private final Runnable action;
    private final BooleanSupplier selected;
    /** A {@code gui/sprites/<folder>/} set with a {@code button_select} face, or null for the parchment tab. */
    private final @Nullable String skin;
    private final int ink;
    private final int inkOff;

    public ThemedChip(int x, int y, int w, int h, @NonNull Component label,
                      @NonNull Runnable action, @NonNull BooleanSupplier selected) {
        this(x, y, w, h, label, action, selected, null, WizardsPalette.PAGE_INK, WizardsPalette.PAGE_INK_3);
    }

    private ThemedChip(int x, int y, int w, int h, Component label, Runnable action, BooleanSupplier selected,
                       @Nullable String skin, int ink, int inkOff) {
        super(x, y, w, h, label);
        this.action = action;
        this.selected = selected;
        this.skin = skin;
        this.ink = ink;
        this.inkOff = inkOff;
    }

    /**
     * The same chip cut from a {@code gui/sprites/<skin>/} set: its {@code button}, {@code _hover} and
     * {@code _off} faces, and {@code button_select} -- a pressed face -- while chosen.
     */
    public static ThemedChip skinned(int x, int y, int w, int h, @NonNull Component label,
                                     @NonNull Runnable action, @NonNull BooleanSupplier selected,
                                     @NonNull String skin, int ink, int inkOff) {
        return new ThemedChip(x, y, w, h, label, action, selected, skin, ink, inkOff);
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
        boolean on = active && selected.getAsBoolean();
        McStylePanel.ControlState state = McStylePanel.ControlState.of(active, isHovered() || isFocused());
        if (skin == null) {
            McStylePanel.drawThemedButton(g, getX(), getY(), getWidth(), getHeight(),
                    on ? McStylePanel.ButtonTone.SELECT : McStylePanel.ButtonTone.NEUTRAL, state);
        } else if (on) {
            McStylePanel.drawNineSlice(g, McStylePanel.skinSprite(skin, "button_select"), getX(), getY(),
                    getWidth(), getHeight(), WizardsMetrics.BUTTON_SPRITE_SIZE, WizardsMetrics.BUTTON_SPRITE_BORDER);
        } else {
            McStylePanel.drawSkinButton(g, skin, getX(), getY(), getWidth(), getHeight(), state);
        }
        Font font = Minecraft.getInstance().font;
        String text = UiText.fit(font, getMessage().getString(), getWidth() - 2 * EDGE_PAD);
        g.drawString(font, text, getX() + (getWidth() - font.width(text)) / 2,
                getY() + (getHeight() - font.lineHeight) / 2 + 1,
                active ? ink : inkOff, false);
    }

    @Override
    protected void renderDefaultLabel(@NonNull ActiveTextCollector collector) {
        // renderContents draws the label in ink, without vanilla's shadow.
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
        if (selected.getAsBoolean()) {
            output.add(NarratedElementType.HINT, Component.translatable("gui.wizards_and_beasts.chip.selected"));
        }
    }
}
