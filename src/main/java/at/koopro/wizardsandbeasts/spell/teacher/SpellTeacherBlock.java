package at.koopro.wizardsandbeasts.spell.teacher;

import at.koopro.wizardsandbeasts.network.spell.teacher.SpellTeacherOpenS2CPayload;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The lectern a wizard learns a spell from.
 *
 * <p>Horizontally directional, and it has to be: the model is a lectern with a <em>tilted reading
 * surface</em>, so unlike a cauldron it has a front. Without the property every one placed faced
 * north and a row of them along a wall read as a mistake.
 *
 * <p>{@link #FACING} is the side the reader stands on — {@code getHorizontalDirection().getOpposite()},
 * matching vanilla's own lectern and {@code OccamyEggshellBlock} — so the desk tilts up toward
 * whoever placed it. North is the unrotated model, which is the face
 * {@code BlockModelGenerators.ROTATION_HORIZONTAL_FACING} assumes.
 *
 * <p>{@code rotate}/{@code mirror} come from {@link HorizontalDirectionalBlock}, so a structure
 * block or a {@code /clone} with a rotation turns one correctly with no override here.
 *
 * <p>The collision shape is unchanged by facing on purpose: all three of its boxes are centred on
 * the block, so a per-facing shape would be four copies of one value.
 */
public class SpellTeacherBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<SpellTeacherBlock> CODEC = simpleCodec(SpellTeacherBlock::new);

    /** Foot, column and tilted reading surface — the lectern the model draws. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3, 0, 3, 13, 2, 13),
            Block.box(5, 2, 5, 11, 10, 11),
            Block.box(2, 10, 2, 14, 13, 14));

    public SpellTeacherBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            SpellTeacherOpenS2CPayload.sendToPlayer(serverPlayer);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
