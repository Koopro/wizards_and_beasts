package at.koopro.wizardsandbeasts.client.heritage.gui;

import at.koopro.wizardsandbeasts.heritage.Heritage;
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
 * Left-rail heritage entry: signature-colour sigil disc + name + state. Available heritages read as
 * parchment ink; locked (non-alpha) heritages render dimmed with a procedural padlock but remain
 * clickable so their detail can still be browsed.
 */
public final class HeritageRailEntry extends AbstractButton {

    private final Heritage heritage;
    private final boolean locked;
    private final BooleanSupplier isSelected;
    private final Consumer<Heritage> onSelect;

    public HeritageRailEntry(int x, int y, int w, int h,
                             @NonNull Heritage heritage, boolean locked,
                             @NonNull BooleanSupplier isSelected, @NonNull Consumer<Heritage> onSelect) {
        super(x, y, w, h, Component.literal(heritage.getDisplayName()));
        this.heritage = heritage;
        this.locked = locked;
        this.isSelected = isSelected;
        this.onSelect = onSelect;
    }

    @Override
    public void onPress(@NonNull InputWithModifiers input) {
        onSelect.accept(heritage);
    }

    /** The heritage this rail entry represents (used for hover tooltips). */
    public Heritage heritage() {
        return heritage;
    }

    /** Whether this heritage is locked (non-alpha, browse-only). */
    public boolean isLockedEntry() {
        return locked;
    }

    @Override
    protected void renderContents(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + getWidth();
        int y1 = y0 + getHeight();
        boolean selected = isSelected.getAsBoolean();

        if (selected) {
            g.fill(x0, y0, x1, y1, 0x55C69A44);
            g.fill(x0, y0, x0 + 2, y1, heritage.getColor() | 0xFF000000);
        } else if (isHovered()) {
            g.fill(x0, y0, x1, y1, 0x22FFE8C0);
        }

        // Sigil disc in the heritage's signature colour (dimmed when locked).
        int disc = getHeight() - 6;
        int dx = x0 + 4;
        int dy = y0 + 3;
        int sigil = locked ? dim(heritage.getColor()) : (heritage.getColor() | 0xFF000000);
        filledDisc(g, dx + disc / 2, dy + disc / 2, disc / 2, sigil);
        filledDisc(g, dx + disc / 2, dy + disc / 2, disc / 2 - 1, blend(sigil, 0xFF000000, 0.25F));

        int textX = dx + disc + 5;
        int textY = y0 + (getHeight() - font.lineHeight) / 2;
        int nameColor = locked ? 0xFF6B5A40 : 0xFF1B130A;
        String name = trim(font, heritage.getDisplayName(), x1 - textX - (locked ? 12 : 4));
        g.drawString(font, name, textX, textY, nameColor, false);

        if (locked) {
            drawPadlock(g, x1 - 11, y0 + (getHeight() - 8) / 2);
        }
    }

    @Override
    protected void renderDefaultLabel(@NonNull ActiveTextCollector collector) {
        // Name is drawn in renderContents(); suppress the default label.
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    // ── Primitives ───────────────────────────────────────────────────────

    private static void drawPadlock(GuiGraphics g, int x, int y) {
        // Body.
        g.fill(x, y + 3, x + 8, y + 8, 0xFF4A3A22);
        g.fill(x + 1, y + 4, x + 7, y + 7, 0xFF6B5A40);
        // Shackle.
        g.fill(x + 1, y, x + 2, y + 3, 0xFF4A3A22);
        g.fill(x + 6, y, x + 7, y + 3, 0xFF4A3A22);
        g.fill(x + 1, y, x + 7, y + 1, 0xFF4A3A22);
    }

    private static void filledDisc(GuiGraphics g, int cx, int cy, int r, int color) {
        if (r <= 0) {
            g.fill(cx, cy, cx + 1, cy + 1, color);
            return;
        }
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static int dim(int rgb) {
        return blend(rgb | 0xFF000000, 0xFF2A2318, 0.55F);
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t);
        int rb = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
    }

    private static String trim(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ell = "..";
        while (!text.isEmpty() && font.width(text + ell) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ell;
    }
}
