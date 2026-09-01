package at.koopro.wizardsandbeasts.floo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.NullMarked;

/**
 * Where the green fire sits relative to the hearth that owns it.
 *
 * <h2>The rule</h2>
 * <p><b>Flames occupy the block directly in front of the fireplace's opening</b> — the one a player
 * can walk into. {@code FlooFireplaceBlock.FACING} is the opening direction (it is set to the
 * placer's facing reversed, so the front turns toward whoever placed it), which makes the flame
 * position {@code firePos.relative(facing)} and the hearth {@code flamePos.relative(facing.getOpposite())}.
 *
 * <p>Not inside the fireplace block: that block's model fills its own cube with a hearth cavity
 * carved out, so there is no standable air in it. The flames have to be somewhere a player can
 * physically stand, and the block in front of the opening is the only candidate that is both
 * unambiguous and always adjacent.
 *
 * <p>Both directions are here, and both are pure, because the two halves of the system look the
 * problem up from opposite ends: {@code FlooPowderItem} knows the hearth and needs the flame, and
 * {@code FlooFlamesBlock} knows the flame and needs the hearth. Deriving one from the other by hand
 * at two call sites is how they drift apart by a block.
 */
@NullMarked
public final class FlooFlamePlacement {

    private FlooFlamePlacement() {}

    /** Where this fireplace's flames belong. */
    public static BlockPos flamePos(BlockPos firePlacePos, Direction facing) {
        return firePlacePos.relative(horizontal(facing));
    }

    /** Which fireplace these flames belong to. */
    public static BlockPos fireplacePos(BlockPos flamePos, Direction facing) {
        return flamePos.relative(horizontal(facing).getOpposite());
    }

    /**
     * Guards the one way this can go wrong.
     *
     * <p>{@code FACING} on both blocks is a {@code HORIZONTAL_FACING} property, so a vertical
     * direction cannot arrive through a blockstate — but it can arrive from a caller that passed the
     * hit face, and flames one block above a hearth read as a chimney fire nobody can reach. Falling
     * back to north rather than throwing keeps a bad call visible as a misplaced block instead of a
     * crash in a block-place path.
     */
    private static Direction horizontal(Direction facing) {
        return facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
    }

    /** True when {@code flamePos} is exactly where a hearth at {@code firePlacePos} would put its flames. */
    public static boolean isFlamePosFor(BlockPos flamePos, BlockPos firePlacePos, Direction facing) {
        return flamePos.equals(flamePos(firePlacePos, facing));
    }
}
