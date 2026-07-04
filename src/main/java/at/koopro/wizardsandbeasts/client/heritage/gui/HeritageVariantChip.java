package at.koopro.wizardsandbeasts.client.heritage.gui;

import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A variant chip in the detail panel's variant selector. Shows the variant name; the selected chip
 * gains an accent outline in the variant's signature colour. Every heritage uses these uniformly.
 */
public final class HeritageVariantChip extends AbstractButton {

    private final HeritageVariant variant;
    private final BooleanSupplier isSelected;
    private final Consumer<HeritageVariant> onSelect;

    public HeritageVariantChip(int x, int y, int w, int h,
                               @NonNull HeritageVariant variant,
                               @NonNull BooleanSupplier isSelected, @NonNull Consumer<HeritageVariant> onSelect) {
        super(x, y, w, h, Component.literal(variant.getDisplayName()));
        this.variant = variant;
        this.isSelected = isSelected;
        this.onSelect = onSelect;
    }

    @Override
    public void onPress(@NonNull InputWithModifiers input) {
        onSelect.accept(variant);
    }

    /** The variant this chip represents (used for hover tooltips). */
    public HeritageVariant variantValue() {
        return variant;
    }

    @Override
    protected void renderContents(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + getWidth();
        int y1 = y0 + getHeight();
        boolean selected = isSelected.getAsBoolean();
        int accent = variant.getUiColor() | 0xFF000000;

        g.fill(x0, y0, x1, y1, selected ? 0xE83A2B17 : 0xC8C6AB74);
        if (selected) {
            outline(g, x0, y0, getWidth(), getHeight(), accent, accent);
        } else if (isHovered()) {
            outline(g, x0, y0, getWidth(), getHeight(), 0xFFE3C88B, 0xFF3C2A14);
        }

        int color = selected ? accent : 0xFF2A2013;
        String label = variant.getDisplayName();
        int tx = x0 + (getWidth() - font.width(label)) / 2;
        int ty = y0 + (getHeight() - font.lineHeight) / 2;
        g.drawString(font, label, tx, ty, color, false);
    }

    @Override
    protected void renderDefaultLabel(@NonNull ActiveTextCollector collector) {
        // Label drawn in renderContents().
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int topLeft, int bottomRight) {
        g.fill(x, y, x + w, y + 1, topLeft);
        g.fill(x, y, x + 1, y + h, topLeft);
        g.fill(x, y + h - 1, x + w, y + h, bottomRight);
        g.fill(x + w - 1, y, x + w, y + h, bottomRight);
    }
}
