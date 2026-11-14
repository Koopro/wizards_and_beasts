package at.koopro.wizardsandbeasts.block.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Set dressing that moves: an owl in its cage, scales that will not settle, a quill writing by itself. Drawn by
 * GeckoLib from {@code geckolib/models/block/deco/<rig>}, playing the rig's idle clip for ever.
 */
@NullMarked
public class AnimatedDecoBlock extends DecoBlock implements EntityBlock {

    private final String rig;
    private final Supplier<? extends BlockEntityType<AnimatedDecoBlockEntity>> type;

    public AnimatedDecoBlock(BlockBehaviour.Properties properties, VoxelShape shape, String rig,
                             Supplier<? extends BlockEntityType<AnimatedDecoBlockEntity>> type) {
        super(properties, shape);
        this.rig = rig;
        this.type = type;
    }

    public String rig() {
        return rig;
    }

    BlockEntityType<AnimatedDecoBlockEntity> type() {
        return type.get();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnimatedDecoBlockEntity(pos, state);
    }
}
