package at.koopro.wizardsandbeasts.client.gui.widget;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A text field pressed into the parchment: the kit's inset well, ink text, no shadows.
 *
 * <p>Vanilla's {@link EditBox} cannot be themed from outside. Its border is a stone sprite, and its
 * hint is drawn with a shadow whatever {@link EditBox#setTextShadow} says — a grey smudge on paper.
 * Two screens had each worked around that by hand (a borderless box over an inset, the hint redrawn
 * in ink), which is the drift a shared widget exists to stop.
 *
 * <p>The trick is to keep the box <em>bordered</em> — so vanilla still insets the text 4px and
 * centres it vertically — while reporting {@link #isBordered()} as false, which is the only thing
 * that decides whether the stone sprite is drawn. {@link #getInnerWidth()} is restored to the
 * bordered width so text stops short of the well's right edge.
 */
public class ThemedTextField extends EditBox {

    private static final int TEXT_INSET = 4;
    private static final int GLYPH_H = 8;

    /** Vanilla keeps its own copy private. */
    private final Font font;
    private @Nullable Component inkHint;
    /** The material the well is pressed into; {@code null} is the default page. */
    private @Nullable GuiSkin skin;

    public ThemedTextField(@NonNull Font font, int x, int y, int w, int h, @NonNull Component narration) {
        super(font, x, y, w, h, narration);
        this.font = font;
        setTextColor(WizardsPalette.PAGE_INK);
        setTextColorUneditable(WizardsPalette.PAGE_INK_3);
        setTextShadow(false);
    }

    /**
     * The placeholder, drawn in faint ink. Deliberately not {@link EditBox#setHint}: vanilla would
     * draw that one with a shadow.
     */
    public ThemedTextField inkHint(@Nullable Component hint) {
        this.inkHint = hint;
        return this;
    }

    /**
     * Presses the well into a skinned sheet instead of the default page, and takes that skin's
     * inks: a warm parchment well on the Floo's soot sheet reads as a patch sewn onto it.
     */
    public ThemedTextField skin(@NonNull GuiSkin skin) {
        this.skin = skin;
        setTextColor(skin.ink());
        setTextColorUneditable(skin.muted());
        return this;
    }

    @Override
    public boolean isBordered() {
        return false;
    }

    @Override
    public int getInnerWidth() {
        return getWidth() - 2 * TEXT_INSET;
    }

    @Override
    public void renderWidget(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) {
            return;
        }
        if (skin == null) {
            McStylePanel.drawThemedInset(g, getX(), getY(), getWidth(), getHeight());
        } else {
            McStylePanel.drawSkinInset(g, skin, getX(), getY(), getWidth(), getHeight());
        }
        if (isFocused()) {
            // Gilt rather than a brighter ink: focus is furniture, and the well's own ink line stays.
            int gilt = skin == null ? WizardsPalette.GILT : skin.accent();
            McStylePanel.drawBorder(g, getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, gilt, gilt);
        }
        super.renderWidget(g, mouseX, mouseY, partialTick);
        if (inkHint != null && getValue().isEmpty() && !isFocused()) {
            g.drawString(font, inkHint, getX() + TEXT_INSET, getY() + (getHeight() - GLYPH_H) / 2,
                    skin == null ? WizardsPalette.PAGE_INK_3 : skin.muted(), false);
        }
    }
}
