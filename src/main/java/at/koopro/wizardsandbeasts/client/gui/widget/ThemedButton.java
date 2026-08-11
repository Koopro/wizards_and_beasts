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
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A button wearing this mod's leather-and-brass chrome instead of vanilla's grey stone.
 *
 * <p>The screens that most need it are the ones a player meets first — the heritage gate is the
 * very first screen of a new character, and it was drawing a hand-built dossier panel with vanilla
 * buttons sat on top of it. Two art styles, one screen.
 *
 * <p>Optionally carries an icon to the left of its label, which is how the randomise control reads
 * as "roll" rather than as another word.
 */
public final class ThemedButton extends AbstractButton {

    /** Clearance between the icon and the label, and between the label and the frame. */
    private static final int ICON_GAP = 4;
    private static final int EDGE_PAD = 6;

    private final Runnable action;
    private final @Nullable Identifier icon;
    private final int iconW;
    private final int iconH;

    public ThemedButton(int x, int y, int w, int h, @NonNull Component label, @NonNull Runnable action) {
        this(x, y, w, h, label, action, null, 0, 0);
    }

    public ThemedButton(int x, int y, int w, int h, @NonNull Component label, @NonNull Runnable action,
                        @Nullable Identifier icon, int iconW, int iconH) {
        super(x, y, w, h, label);
        this.action = action;
        this.icon = icon;
        this.iconW = iconW;
        this.iconH = iconH;
    }

    /** The randomise control: a die, then the label. */
    public static ThemedButton randomise(int x, int y, int w, int h, @NonNull Component label,
                                         @NonNull Runnable action) {
        return new ThemedButton(x, y, w, h, label, action, McStylePanel.THEME_ICON_RANDOMISE, 12, 12);
    }

    @Override
    public void onPress(@NonNull InputWithModifiers input) {
        action.run();
    }

    @Override
    protected void renderContents(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        McStylePanel.ControlState state =
                McStylePanel.ControlState.of(active, isHovered() || isFocused());
        McStylePanel.drawThemedButton(g, getX(), getY(), getWidth(), getHeight(), state);

        Font font = Minecraft.getInstance().font;
        String label = getMessage().getString();
        int textW = font.width(label);
        int iconSpan = icon == null ? 0 : iconW + ICON_GAP;
        // Icon and label are centred as one group, so a button reads as balanced whether or not it
        // carries an icon and whichever language the label is in.
        int groupX = getX() + (getWidth() - (iconSpan + textW)) / 2;
        int textY = getY() + (getHeight() - font.lineHeight) / 2;

        if (icon != null) {
            McStylePanel.drawTexture(g, icon, groupX, getY() + (getHeight() - iconH) / 2,
                    iconW, iconH, iconW, iconH);
        }
        g.drawString(font, label, Math.max(getX() + EDGE_PAD, groupX + iconSpan), textY,
                active ? WizardsPalette.TEXT : WizardsPalette.TEXT_DIM, false);
    }

    @Override
    protected void renderDefaultLabel(@NonNull ActiveTextCollector collector) {
        // renderContents draws the label itself, so the group stays centred with the icon.
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
