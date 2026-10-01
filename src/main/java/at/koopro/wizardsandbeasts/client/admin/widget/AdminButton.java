package at.koopro.wizardsandbeasts.client.admin.widget;

import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

/**
 * The Control Center's button. Flat, compact, and toned by what pressing it means.
 *
 * <p>Tones are semantic rather than decorative: {@link Tone#PRIMARY} commits, {@link Tone#DANGER} commits
 * something that affects every player, {@link Tone#QUIET} navigates. {@link Tone#FRAME} is the same button
 * drawn for the dark purple chrome instead of the page.
 */
@NullMarked
public class AdminButton extends net.minecraft.client.gui.components.AbstractButton {

    public enum Tone {
        NEUTRAL,
        PRIMARY,
        DANGER,
        QUIET,
        FRAME
    }

    private static final int EDGE_PAD = 4;

    private final Runnable action;
    private Tone tone;

    public AdminButton(int x, int y, int w, int h, Component label, Tone tone, Runnable action) {
        super(x, y, w, h, label);
        this.tone = tone;
        this.action = action;
    }

    public AdminButton tone(Tone tone) {
        this.tone = tone;
        return this;
    }

    @Override
    public void onPress(@NonNull InputWithModifiers input) {
        action.run();
    }

    @Override
    protected void renderContents(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean lit = active && isHoveredOrFocused();
        int face;
        int border;
        int highlight;
        int ink;
        switch (tone) {
            case PRIMARY -> {
                face = lit ? AdminTheme.GOLD_LIGHT : AdminTheme.GOLD;
                border = AdminTheme.GOLD_DARK;
                highlight = AdminTheme.GOLD_LIGHT;
                ink = AdminTheme.INK;
            }
            case DANGER -> {
                face = lit ? 0xFFA8322C : AdminTheme.WAX;
                border = AdminTheme.BAD;
                highlight = 0xFFB5463F;
                ink = AdminTheme.PAPER;
            }
            case FRAME -> {
                face = lit ? AdminTheme.FRAME_HOVER : AdminTheme.FRAME_RAISED;
                border = lit ? AdminTheme.GOLD : AdminTheme.GOLD_DARK;
                highlight = AdminTheme.FRAME_HOVER;
                ink = AdminTheme.FRAME_TEXT;
            }
            case QUIET -> {
                face = lit ? AdminTheme.PAPER_SELECT : AdminTheme.PAPER_SHADE;
                border = AdminTheme.PAPER_RULE;
                highlight = AdminTheme.PAPER;
                ink = AdminTheme.INK_2;
            }
            default -> {
                face = lit ? AdminTheme.PAPER_SELECT : AdminTheme.PAPER;
                border = lit ? AdminTheme.GOLD_DARK : AdminTheme.PAPER_RULE;
                highlight = 0xFFFFFFFF;
                ink = AdminTheme.INK;
            }
        }
        if (!active) {
            face = tone == Tone.FRAME ? AdminTheme.FRAME : AdminTheme.PAPER_SHADE;
            border = tone == Tone.FRAME ? AdminTheme.FRAME_RAISED : AdminTheme.PAPER_RULE;
            highlight = face;
            ink = tone == Tone.FRAME ? AdminTheme.FRAME_TEXT_DIM : AdminTheme.INK_3;
        }
        AdminTheme.raised(g, getX(), getY(), getWidth(), getHeight(), face, border, highlight);
        var font = Minecraft.getInstance().font;
        GuiText.drawFittedCentered(g, font, getMessage().getString(), getX() + EDGE_PAD,
                getY() + (getHeight() - font.lineHeight) / 2 + 1, getWidth() - 2 * EDGE_PAD, ink);
        AdminTheme.focusRing(g, this);
    }

    @Override
    protected void renderDefaultLabel(@NonNull ActiveTextCollector collector) {
        // renderContents draws the label in the tone's ink, without vanilla's shadow.
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
