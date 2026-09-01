package at.koopro.wizardsandbeasts.brew;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;

/**
 * What counts as a fire under a cauldron.
 *
 * <p>Lifted out of {@code CauldronBrewing} when brewing moved onto the block entity, so that the one
 * rule is not duplicated between the block that asks it every tick and anything else that wants to
 * know whether a pot is on the boil.
 *
 * <p>The list is deliberately short and physical: things that are visibly burning, plus magma, which
 * is the only block that is hot without being on fire. No block tag, because a tag would invite
 * datapacks to make a cauldron boil on a chest — and "is there a fire under it" is a rule a player
 * reads off the world rather than out of a data file.
 */
@NullMarked
public final class CauldronHeat {

    private CauldronHeat() {
    }

    /** Whether the block at {@code pos} would boil a cauldron sitting on top of it. */
    public static boolean hasHeatSource(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos);
        if (below.is(Blocks.FIRE) || below.is(Blocks.SOUL_FIRE)) return true;
        if (below.is(Blocks.LAVA)) return true;
        if (below.is(Blocks.MAGMA_BLOCK)) return true;
        return below.getBlock() instanceof CampfireBlock && below.getValue(CampfireBlock.LIT);
    }
}
