package at.koopro.wizardsandbeasts.block.floo;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NullMarked;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The pot of Floo Powder on the mantelpiece that guests take a pinch from: the Burrow keeps it in a flowerpot
 * (CoS ch. 4; CANON.md). Vanilla flower pot shape and clay, powder heaped inside at one of three levels. An empty
 * hand takes a pinch (one level), Floo Powder puts one back; crafted full from a flower pot and three pinches.
 */
@NullMarked
public class FlooPowderPotBlock extends Block {

    public static final MapCodec<FlooPowderPotBlock> CODEC = simpleCodec(FlooPowderPotBlock::new);
    public static final int MAX_FILL = 3;
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, MAX_FILL);

    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 6, 11);

    public FlooPowderPotBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FILL, MAX_FILL));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int fill = state.getValue(FILL);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (fill == 0) {
            player.displayClientMessage(Component.translatable("floo.wizards_and_beasts.pot.empty"), true);
            return InteractionResult.SUCCESS;
        }
        level.setBlock(pos, state.setValue(FILL, fill - 1), 3);
        player.getInventory().placeItemBackInInventory(new ItemStack(MiscItemRegistry.FLOO_POWDER.get()));
        level.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.4F, 1.6F);
        player.displayClientMessage(Component.translatable("floo.wizards_and_beasts.pot.took"), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(MiscItemRegistry.FLOO_POWDER.get()) || state.getValue(FILL) >= MAX_FILL) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(FILL, state.getValue(FILL) + 1), 3);
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.4F, 1.4F);
        }
        return InteractionResult.SUCCESS;
    }
}
