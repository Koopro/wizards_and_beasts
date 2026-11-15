package at.koopro.wizardsandbeasts.client.wand.gui;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One frame of the restyle screen's 3D wand: the wand to draw, how it is turned, and where on screen it goes.
 *
 * @param wand       the wand as it will come out of the bench
 * @param yawDeg     turn about the upright axis: the turntable, or where the player dragged it
 * @param pitchDeg   tip toward the viewer
 * @param pixelScale screen pixels per block (the wand is drawn 0.618 of its model length, as in an item frame)
 * @param glint      the core's light, RGB, drawn at the point; 0 for none
 * @param tipHeight  how far the point stands above the wand's middle, in blocks
 */
@NullMarked
public record WandPreviewRenderState(ItemStack wand, float yawDeg, float pitchDeg, float pixelScale, int glint,
                                     float tipHeight, int x0, int y0, int x1, int y1,
                                     @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
        implements PictureInPictureRenderState {

    public WandPreviewRenderState(ItemStack wand, float yawDeg, float pitchDeg, float pixelScale, int glint,
                                  float tipHeight, int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea) {
        this(wand, yawDeg, pitchDeg, pixelScale, glint, tipHeight, x0, y0, x1, y1, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }

    @Override
    public float scale() {
        return pixelScale;
    }
}
