package at.koopro.wizardsandbeasts.client.gui.character.widget;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

/**
 * Renders the local player's 3D model in a fixed viewport rectangle.
 * Supports drag-rotate (left mouse) and scroll-zoom.
 */
public final class PlayerModelViewport {

    private static final float PITCH_MAX   = 60.0f;
    private static final float ZOOM_MIN    = 0.6f;
    private static final float ZOOM_MAX    = 2.5f;
    private static final float DEFAULT_ZOOM = 1.0f;
    private static final float BASE_SCALE  = 30.0f;

    private static final int COLOR_FILL   = 0xFF0D0905;
    private static final int COLOR_HI     = 0xFF3A2A14;
    private static final int COLOR_SHADOW = 0xFF0A0603;

    private float modelYaw   = 20.0f;
    private float modelPitch = 5.0f;
    private float zoom       = DEFAULT_ZOOM;

    private boolean dragging;
    private double  dragLastX;
    private double  dragLastY;

    private int vx, vy, vw, vh;

    public void render(@NonNull GuiGraphics g, int x, int y, int w, int h,
                       float partialTick, @NonNull LocalPlayer player) {
        this.vx = x; this.vy = y; this.vw = w; this.vh = h;

        McStylePanel.drawPanel(g, x, y, w, h, COLOR_FILL, COLOR_HI, COLOR_SHADOW);

        // renderEntityInInventoryFollowsAngle multiplies angle params by 20 internally,
        // so divide modelYaw/modelPitch by 20 to recover the original rotation.
        int scale = Math.round(BASE_SCALE * zoom);
        InventoryScreen.renderEntityInInventoryFollowsAngle(
                g,
                x, y, x + w, y + h,
                scale,
                0.0f,
                modelYaw   / 20.0f,
                modelPitch / 20.0f,
                player
        );
    }

    /** @return true if the (sx,sy) screen coordinate is inside the viewport. */
    public boolean isInsideViewport(double sx, double sy) {
        return sx >= vx && sx < vx + vw && sy >= vy && sy < vy + vh;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInsideViewport(mouseX, mouseY)) {
            dragging  = true;
            dragLastX = mouseX;
            dragLastY = mouseY;
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!dragging || button != 0) return false;
        modelYaw   += (float) (mouseX - dragLastX) * 0.75f;
        modelPitch  = Mth.clamp(modelPitch + (float) (mouseY - dragLastY) * 0.5f, -PITCH_MAX, PITCH_MAX);
        dragLastX   = mouseX;
        dragLastY   = mouseY;
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isInsideViewport(mouseX, mouseY)) return false;
        zoom = Mth.clamp(zoom + (float) (delta * 0.1), ZOOM_MIN, ZOOM_MAX);
        return true;
    }
}
