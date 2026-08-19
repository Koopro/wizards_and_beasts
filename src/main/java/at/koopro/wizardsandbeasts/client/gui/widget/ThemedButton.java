package at.koopro.wizardsandbeasts.client.gui.widget;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel.ButtonTone;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel.Sprite;
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
    /**
     * A {@link Sprite} rather than an Identifier: the shared {@code theme/} art is now one atlas,
     * so an icon is a rectangle of a sheet. The skinned sets are still a file each, which
     * {@link Sprite#whole} covers.
     */
    private final @Nullable Sprite icon;
    /**
     * Which material the face is cut from: {@code null} is the shared leather-and-brass
     * {@code gui/theme/}, a name is one of the {@code gui/sprites/<skin>/} sets.
     *
     * <p>A skinned screen with a leather button on it is the same mismatch this widget exists to
     * fix, one level down — the skill web is night void and indigo, and a brass-on-leather button
     * reads as borrowed from another screen.
     */
    private final @Nullable String skin;
    /** Label colour, so a dark skin is not asked to carry the leather palette's ink. */
    private final int textColor;
    private final int textColorOff;
    /**
     * What pressing this does. Skinned buttons ignore it — a skin is already a material, and
     * tinting one screen's material by meaning would put two colour systems on one button.
     */
    private ButtonTone tone = ButtonTone.NEUTRAL;

    public ThemedButton(int x, int y, int w, int h, @NonNull Component label, @NonNull Runnable action) {
        this(x, y, w, h, label, action, null);
    }

    public ThemedButton(int x, int y, int w, int h, @NonNull Component label, @NonNull Runnable action,
                        @Nullable Sprite icon) {
        this(x, y, w, h, label, action, icon, null,
                WizardsPalette.TEXT, WizardsPalette.TEXT_DIM);
    }

    public ThemedButton(int x, int y, int w, int h, @NonNull Component label, @NonNull Runnable action,
                        @Nullable Sprite icon,
                        @Nullable String skin, int textColor, int textColorOff) {
        super(x, y, w, h, label);
        this.action = action;
        this.icon = icon;
        this.skin = skin;
        this.textColor = textColor;
        this.textColorOff = textColorOff;
    }

    /**
     * The randomise control: crossed shuffle arrows, then the label. Live but non-committing,
     * so {@code ACCENT}.
     */
    public static ThemedButton randomise(int x, int y, int w, int h, @NonNull Component label,
                                         @NonNull Runnable action) {
        return new ThemedButton(x, y, w, h, label, action, McStylePanel.THEME_ICON_RANDOMISE)
                .tone(ButtonTone.ACCENT);
    }

    /** Sets what pressing this button means. Chainable, so it reads at the call site. */
    public ThemedButton tone(@NonNull ButtonTone tone) {
        this.tone = tone;
        return this;
    }

    /** The same button cut from one of the {@code gui/sprites/<skin>/} materials. */
    public static ThemedButton skinned(int x, int y, int w, int h, @NonNull Component label,
                                       @NonNull Runnable action, @NonNull String skin,
                                       @Nullable Identifier icon, int iconSize,
                                       int textColor, int textColorOff) {
        return new ThemedButton(x, y, w, h, label, action,
                icon == null ? null : Sprite.whole(icon, iconSize, iconSize),
                skin, textColor, textColorOff);
    }

    @Override
    public void onPress(@NonNull InputWithModifiers input) {
        action.run();
    }

    @Override
    protected void renderContents(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        McStylePanel.ControlState state =
                McStylePanel.ControlState.of(active, isHovered() || isFocused());
        if (skin == null) {
            McStylePanel.drawThemedButton(g, getX(), getY(), getWidth(), getHeight(), tone, state);
        } else {
            McStylePanel.drawSkinButton(g, skin, getX(), getY(), getWidth(), getHeight(), state);
        }

        Font font = Minecraft.getInstance().font;
        String label = getMessage().getString();
        int textW = font.width(label);
        int iconSpan = icon == null ? 0 : icon.w() + ICON_GAP;
        // Icon and label are centred as one group, so a button reads as balanced whether or not it
        // carries an icon and whichever language the label is in.
        int groupX = getX() + (getWidth() - (iconSpan + textW)) / 2;
        int textY = getY() + (getHeight() - font.lineHeight) / 2;

        if (icon != null) {
            McStylePanel.drawSprite(g, icon, groupX, getY() + (getHeight() - icon.h()) / 2);
        }
        g.drawString(font, label, Math.max(getX() + EDGE_PAD, groupX + iconSpan), textY,
                active ? textColor : textColorOff, false);
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
