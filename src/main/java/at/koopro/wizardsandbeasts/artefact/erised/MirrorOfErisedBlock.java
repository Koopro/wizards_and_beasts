package at.koopro.wizardsandbeasts.artefact.erised;

import at.koopro.wizardsandbeasts.artefact.TwoTallBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * The Mirror of Erised: a tall gilded mirror on clawed feet, two blocks high. Click it -- with anything in hand -- to
 * look in; {@link ErisedService} decides what you see.
 */
public class MirrorOfErisedBlock extends TwoTallBlock {

    public static final MapCodec<MirrorOfErisedBlock> CODEC = simpleCodec(MirrorOfErisedBlock::new);

    /** Authored facing north: feet and frame across the full width, the glass a few pixels deep. */
    private static final Map<Direction, VoxelShape> LOWER_SHAPES =
            Shapes.rotateHorizontal(Block.box(1, 0, 6, 15, 16, 11));
    private static final Map<Direction, VoxelShape> UPPER_SHAPES =
            Shapes.rotateHorizontal(Block.box(1, 0, 7, 15, 16, 9));

    public MirrorOfErisedBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return (state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES : UPPER_SHAPES).get(state.getValue(FACING));
    }

    /** Whatever is in hand, a click is a look into the glass. */
    @Override
    protected @NonNull InteractionResult useItemOn(@NonNull ItemStack stack, @NonNull BlockState state,
                                                   @NonNull Level level, @NonNull BlockPos pos,
                                                   @NonNull Player player, @NonNull InteractionHand hand,
                                                   @NonNull BlockHitResult hit) {
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level,
                                                        @NonNull BlockPos pos, @NonNull Player player,
                                                        @NonNull BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            ErisedService.gaze(sp, lowerOf(state, pos));
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.SUCCESS;
    }
}
