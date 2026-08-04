package at.koopro.wizardsandbeasts.client.gui.character.widget;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

/**
 * Renders the local player's 3D model in a fixed viewport rectangle.
 * The model looks toward the cursor (like the vanilla inventory) and supports scroll-zoom.
 */
public final class PlayerModelViewport {

    private static final float ZOOM_MIN    = 0.6f;
    private static final float ZOOM_MAX    = 2.5f;
    private static final float DEFAULT_ZOOM = 1.0f;
    /**
     * Fraction of the viewport's height the player should occupy at 1x zoom, and the
     * player's height in entity units.
     *
     * <p>This used to be a flat {@code BASE_SCALE = 30}, which is vanilla's number for the
     * inventory's ~54px doll. In a 112px-tall viewport that drew the player at under half
     * the height available, which is why the sheet showed a small figure marooned in a
     * large black box. Deriving the scale from the box means the framing holds if the
     * viewport is ever resized.
     */
    private static final float FILL_FRACTION = 0.80f;
    private static final float PLAYER_UNITS  = 1.8f;

    private static final int COLOR_FILL   = 0xFF0D0905;
    private static final int COLOR_HI     = 0xFF3A2A14;
    private static final int COLOR_SHADOW = 0xFF0A0603;

    private float zoom = DEFAULT_ZOOM;

    private int vx, vy, vw, vh;

    /**
     * @param mouseX,mouseY cursor position in the same (design) coordinate space as {@code x,y,w,h}
     *        so the look-at angle matches what the player sees, even when the sheet is GUI-scaled.
     */
    public void render(@NonNull GuiGraphics g, int x, int y, int w, int h,
                       float partialTick, @NonNull LocalPlayer player, float mouseX, float mouseY) {
        this.vx = x; this.vy = y; this.vw = w; this.vh = h;

        McStylePanel.drawPanel(g, x, y, w, h, COLOR_FILL, COLOR_HI, COLOR_SHADOW);

        int scale = Math.max(1, Math.round(h * FILL_FRACTION / PLAYER_UNITS * zoom));
        // Vanilla helper: entity head/body track the cursor relative to the viewport rect.
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g,
                x, y, x + w, y + h,
                scale,
                0.0625f,
                mouseX,
                mouseY,
                player
        );
    }

    /** @return true if the (sx,sy) screen coordinate is inside the viewport. */
    public boolean isInsideViewport(double sx, double sy) {
        return sx >= vx && sx < vx + vw && sy >= vy && sy < vy + vh;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isInsideViewport(mouseX, mouseY)) return false;
        zoom = Mth.clamp(zoom + (float) (delta * 0.1), ZOOM_MIN, ZOOM_MAX);
        return true;
    }
}
