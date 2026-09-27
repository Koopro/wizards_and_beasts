package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.block.AcromantulaWebBlock;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where an Acromantula may put silk, and how much. Server-side rules only; the block is
 * {@link AcromantulaWebBlock}, which also cleans up after itself.
 *
 * <ul>
 *   <li>Only into plain air that has something solid to hang from (a face below or beside it), inside a loaded
 *       chunk the level will let it build in — never over water, a plant, a torch or anything a player placed.</li>
 *   <li>A snare puts at most one web, at the target's feet or head.</li>
 *   <li>Nest-webbing puts one web now and then near the colony's home, and stops once {@link #NEST_CAP} webs already
 *       hang within {@link #NEST_RADIUS} — a nest is webbed, not filled.</li>
 * </ul>
 */
public final class AcromantulaWebs {

    public static final int NEST_RADIUS = 5;
    public static final int NEST_CAP = 10;

    private AcromantulaWebs() {}

    /** Whether silk may go at {@code pos}. */
    public static boolean canSpin(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.mayInteract(null, pos) || level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() || !level.getFluidState(pos).isEmpty()) {
            return false;
        }
        for (Direction dir : Direction.values()) {
            if (dir != Direction.UP && level.getBlockState(pos.relative(dir)).isFaceSturdy(level, pos.relative(dir), dir.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    /** Puts one web at the first of {@code candidates} that can take it. */
    public static boolean spinAt(ServerLevel level, BlockPos... candidates) {
        for (BlockPos pos : candidates) {
            if (canSpin(level, pos)) {
                level.setBlockAndUpdate(pos, ModBlocks.ACROMANTULA_WEB.get().defaultBlockState());
                return true;
            }
        }
        return false;
    }

    /** How many webs hang within {@code radius} of {@code center}. */
    public static int count(ServerLevel level, BlockPos center, int radius) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 3, radius))) {
            if (level.getBlockState(pos).is(ModBlocks.ACROMANTULA_WEB.get())) {
                n++;
            }
        }
        return n;
    }

    /** One more strand near the nest, if the nest is not already webbed enough. */
    public static boolean webTheNest(ServerLevel level, BlockPos home, RandomSource random) {
        if (count(level, home, NEST_RADIUS) >= NEST_CAP) {
            return false;
        }
        BlockPos pos = home.offset(random.nextInt(NEST_RADIUS * 2 + 1) - NEST_RADIUS, random.nextInt(4),
                random.nextInt(NEST_RADIUS * 2 + 1) - NEST_RADIUS);
        return spinAt(level, pos);
    }
}
