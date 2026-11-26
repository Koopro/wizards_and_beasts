package at.koopro.wizardsandbeasts.client.handbook;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One frame of a handbook build guide: the blocks placed so far (the current layer lifted by {@code drop} blocks while
 * it settles), how the build is turned, and where on screen it goes.
 *
 * @param blocks     each block and its place, relative to the build's centre
 * @param drop       how far above its place the current layer still hangs, in blocks (0 once it has landed)
 * @param pixelScale screen pixels per block
 */
@NullMarked
public record StructurePreviewRenderState(List<Placed> blocks, float drop, float yawDeg, float pitchDeg, float pixelScale,
                                          int x0, int y0, int x1, int y1,
                                          @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
        implements PictureInPictureRenderState {

    /** A block of the build; {@code current} marks the layer being shown landing. */
    public record Placed(BlockPos pos, BlockState state, boolean current) {}

    public StructurePreviewRenderState(List<Placed> blocks, float drop, float yawDeg, float pitchDeg, float pixelScale,
                                       int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea) {
        this(blocks, drop, yawDeg, pitchDeg, pixelScale, x0, y0, x1, y1, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }

    @Override
    public float scale() {
        return pixelScale;
    }
}
