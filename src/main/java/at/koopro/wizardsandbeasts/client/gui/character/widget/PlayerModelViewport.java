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
    private static final float BASE_SCALE  = 30.0f;

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

        int scale = Math.round(BASE_SCALE * zoom);
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
