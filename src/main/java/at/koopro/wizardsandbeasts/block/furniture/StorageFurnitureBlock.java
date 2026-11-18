package at.koopro.wizardsandbeasts.block.furniture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Furniture that holds things: a chest of drawers, a cabinet, a crate. Using it opens a vanilla chest screen of
 * {@code rows} rows; what it holds spills out when it is broken. One block entity type serves every piece
 * ({@link FurnitureStorageBlockEntity}).
 */
@NullMarked
public class StorageFurnitureBlock extends FurnitureBlock implements EntityBlock {

    private final int rows;

    public StorageFurnitureBlock(BlockBehaviour.Properties properties, Spec spec, int rows) {
        super(properties, spec);
        this.rows = rows;
    }

    public int rows() {
        return rows;
    }

    @Override
    protected MapCodec<? extends StorageFurnitureBlock> codec() {
        return simpleCodec(properties -> new StorageFurnitureBlock(properties, spec(), rows));
    }

    /** The block whose entity holds the items: this one, or for a two-high piece its lower half. */
    protected BlockPos storagePos(BlockState state, BlockPos pos) {
        return pos;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FurnitureStorageBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(storagePos(state, pos)) instanceof FurnitureStorageBlockEntity storage) {
            player.openMenu(storage);
        }
        return InteractionResult.SUCCESS;
    }
}
