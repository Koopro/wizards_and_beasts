package at.koopro.wizardsandbeasts.block.furniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A four-poster bed: vanilla's bed in every way that matters (two halves, sleeping, setting the spawn, the explosion
 * in the Nether), drawn as a baked block model (posts, canopy, curtains) instead of vanilla's bed renderer, so it
 * needs no block entity.
 */
@NullMarked
public class FourPosterBedBlock extends BedBlock {

    private final DyeColor colour;

    public FourPosterBedBlock(DyeColor colour, Properties properties) {
        super(colour, properties);
        this.colour = colour;
    }

    @Override
    public MapCodec<BedBlock> codec() {
        return simpleCodec(properties -> new FourPosterBedBlock(colour, properties));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }
}
