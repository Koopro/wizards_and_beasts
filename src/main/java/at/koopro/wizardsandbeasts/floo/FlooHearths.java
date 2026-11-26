package at.koopro.wizardsandbeasts.floo;

import at.koopro.wizardsandbeasts.block.floo.FlooGrateBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooGrateBlockEntity;
import at.koopro.wizardsandbeasts.block.floo.HearthFire;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Where a Floo hearth is, seen from the travel side (Floo remake P3/P4, 2026-11-26). A hearth is a {@code floo_grate}
 * standing in a finished build; the network's entry for it is the grate's position. One home for "is this traveller
 * standing in the fire" and "where does an arrival stand", which the retired 1x1 hearth spread over its flames block,
 * the departure and the travel handler.
 */
@NullMarked
public final class FlooHearths {

    private FlooHearths() {
    }

    public static @Nullable FlooGrateBlockEntity grate(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FlooGrateBlockEntity grate ? grate : null;
    }

    /** A grate burning green (thrown pinch or arrival flare). Reads the block, so a mined grate answers false. */
    public static boolean isGreen(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.FLOO_GRATE.get()) && state.getValue(FlooGrateBlock.FIRE) == HearthFire.FLOO;
    }

    /**
     * Whether {@code at} lies inside the opening of the hearth whose grate is at {@code grate}: the opening's width
     * across the grate's row, its height up from the grate, and the grate's own depth (half a block either side, so a
     * traveller shuffling on the hearthstone is still in it).
     */
    public static boolean inOpening(BlockPos grate, Direction facing, int width, int height, Vec3 at) {
        Vec3 centre = Vec3.atBottomCenterOf(grate);
        Vec3 d = at.subtract(centre);
        Direction across = facing.getCounterClockWise();
        double side = d.x * across.getStepX() + d.z * across.getStepZ();
        double front = d.x * facing.getStepX() + d.z * facing.getStepZ();
        return Math.abs(side) <= width / 2.0 && front > -0.5 && front < 0.9 && d.y > -0.5 && d.y < height;
    }

    public static boolean inOpening(FlooGrateBlockEntity grate, Vec3 at) {
        return inOpening(grate.getBlockPos(), grate.getBlockState().getValue(FlooGrateBlock.FACING),
                grate.openingWidth(), grate.openingHeight(), at);
    }

    /**
     * The registered, open, green hearth this player stands in, or null. Looked for in a small box around the player,
     * since the grate is under their feet or beside them in a wide opening.
     */
    public static @Nullable FlooGrateBlockEntity greenHearthAround(ServerPlayer player) {
        BlockPos centre = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-2, -1, -2), centre.offset(2, 0, 2))) {
            if (isGreen(player.level(), pos) && grate(player.level(), pos) instanceof FlooGrateBlockEntity grate
                    && grate.formed() && grate.isRegistered() && grate.isEnabled()
                    && inOpening(grate, player.position())) {
                return grate;
            }
        }
        return null;
    }

    /**
     * A registered, open, burning hearth (ordinary or green) within kneeling reach of this player: where a
     * head-in-the-fire call is made from (crouch + powder at the grate).
     */
    public static @Nullable FlooGrateBlockEntity burningHearthAround(ServerPlayer player) {
        BlockPos centre = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-2, -1, -2), centre.offset(2, 1, 2))) {
            BlockState state = player.level().getBlockState(pos);
            if (state.is(ModBlocks.FLOO_GRATE.get()) && state.getValue(FlooGrateBlock.FIRE).burning()
                    && grate(player.level(), pos) instanceof FlooGrateBlockEntity grate
                    && grate.formed() && grate.isRegistered() && grate.isEnabled()) {
                return grate;
            }
        }
        return null;
    }

    /**
     * Where a traveller lands: tumbled out of the fire onto the hearthstone in front of the opening (Harry "fell out
     * of the grate", CoS ch. 4), so they do not land back in the fire they came through. Null when the hearth is no
     * longer a finished hearth or the spot in front is built over.
     */
    public static @Nullable BlockPos arrivalSpot(ServerLevel level, BlockPos gratePos) {
        level.getChunk(gratePos);
        FlooGrateBlockEntity grate = grate(level, gratePos);
        if (grate == null || !grate.formed()) {
            return null;
        }
        BlockPos spot = gratePos.relative(grate.getBlockState().getValue(FlooGrateBlock.FACING));
        if (spot.getY() < level.getMinY() || spot.getY() + 1 > level.getMaxY()) {
            return null;
        }
        return open(level, spot) && open(level, spot.above()) ? spot : null;
    }

    /** The way a traveller faces on arrival: out of the hearth, into the room. */
    public static float arrivalYaw(ServerLevel level, BlockPos gratePos) {
        BlockState state = level.getBlockState(gratePos);
        return state.is(ModBlocks.FLOO_GRATE.get()) ? state.getValue(FlooGrateBlock.FACING).toYRot() : 0.0F;
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
