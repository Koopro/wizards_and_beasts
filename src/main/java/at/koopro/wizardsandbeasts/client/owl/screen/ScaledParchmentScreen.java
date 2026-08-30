package at.koopro.wizardsandbeasts.client.owl.screen;

import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

/**
 * A screen laid out in fixed design-space pixels and scaled to fit whatever window it lands in.
 *
 * <p>All three OWL screens are drawn on the same parchment panel at a fixed size, then scaled up or
 * down as one. Each carried its own copy of the five layout fields, the three coordinate mappers,
 * and the push/translate/scale that opens a frame — around forty lines apiece, differing only in the
 * panel's dimensions.
 *
 * <p>A subclass calls {@link #layout} from {@code init()} and {@link #beginScaledPass} at the top of
 * {@code render}, then draws in design space and matches every {@code beginScaledPass} with a
 * {@code graphics.pose().popMatrix()}.
 */
@NullMarked
public abstract class ScaledParchmentScreen extends Screen {

    /** Aged-paper fill behind every OWL panel. */
    protected static final int PARCHMENT_ARGB = 0xEEF5E6C8;

    protected float guiScale = 1.0f;
    protected int originX;
    protected int originY;
    protected int designLeft;
    protected int designTop;

    protected ScaledParchmentScreen(Component title) {
        super(title);
    }

    /**
     * Recomputes the scale and origin for a panel of this size. Call from {@code init()}.
     *
     * @param overhang extra design-space height that must stay on screen but is not part of the
     *                 panel itself — the results screen's content runs past the parchment
     */
    protected void layout(int bgWidth, int bgHeight, int overhang) {
        guiScale = GuiScaleHelper.computeScale(bgWidth, bgHeight + overhang, width, height,
                GuiScaleHelper.DEFAULT_MARGIN);
        designLeft = width / 2 - bgWidth / 2;
        designTop = height / 2 - bgHeight / 2;
        originX = GuiScaleHelper.clampedLeft(Math.round(bgWidth * guiScale), width,
                GuiScaleHelper.DEFAULT_MARGIN);
        originY = GuiScaleHelper.clampedTop(Math.round((bgHeight + overhang) * guiScale), height,
                GuiScaleHelper.DEFAULT_MARGIN);
    }

    /** {@link #layout(int, int, int)} for a panel with nothing hanging past it. */
    protected void layout(int bgWidth, int bgHeight) {
        layout(bgWidth, bgHeight, 0);
    }

    /** Map a design-space x (relative to the current width/height) to screen space. */
    protected int sx(int designX) {
        return originX + Math.round((designX - designLeft) * guiScale);
    }

    /** Map a design-space y to screen space. */
    protected int sy(int designY) {
        return originY + Math.round((designY - designTop) * guiScale);
    }

    /** Scale a design-space size, never down to nothing. */
    protected int sw(int size) {
        return Math.max(1, Math.round(size * guiScale));
    }

    /**
     * Opens a scaled drawing pass. Everything drawn until the matching
     * {@code graphics.pose().popMatrix()} is in design space.
     */
    protected void beginScaledPass(GuiGraphics graphics) {
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(originX - designLeft * guiScale, originY - designTop * guiScale);
        pose.scale(guiScale, guiScale);
    }

    /** The parchment panel itself, centred in design space. */
    protected void drawParchment(GuiGraphics graphics, int bgWidth, int bgHeight) {
        int cx = width / 2;
        int cy = height / 2;
        graphics.fill(cx - bgWidth / 2, cy - bgHeight / 2, cx + bgWidth / 2, cy + bgHeight / 2,
                PARCHMENT_ARGB);
    }
}
