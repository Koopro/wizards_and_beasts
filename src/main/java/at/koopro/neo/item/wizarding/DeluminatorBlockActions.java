package at.koopro.neo.item.wizarding;

import at.koopro.neo.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Map;

/**
 * Block-targeting absorb / restore logic for the Deluminator (server-side).
 */
final class DeluminatorBlockActions {

    private DeluminatorBlockActions() {}

    private static Map<Block, Block> litToUnlit;
    private static Map<Block, Block> unlitToLit;

    static Map<Block, Block> getLitToUnlit() {
        if (litToUnlit == null) {
            litToUnlit = Map.of(
                    Blocks.TORCH, ModBlocks.UNLIT_TORCH.get(),
                    Blocks.WALL_TORCH, ModBlocks.UNLIT_WALL_TORCH.get(),
                    Blocks.LANTERN, ModBlocks.UNLIT_LANTERN.get(),
                    Blocks.GLOWSTONE, ModBlocks.UNLIT_GLOWSTONE.get()
            );
        }
        return litToUnlit;
    }

    static Map<Block, Block> getUnlitToLit() {
        if (unlitToLit == null) {
            unlitToLit = Map.of(
                    ModBlocks.UNLIT_TORCH.get(), Blocks.TORCH,
                    ModBlocks.UNLIT_WALL_TORCH.get(), Blocks.WALL_TORCH,
                    ModBlocks.UNLIT_LANTERN.get(), Blocks.LANTERN,
                    ModBlocks.UNLIT_GLOWSTONE.get(), Blocks.GLOWSTONE
            );
        }
        return unlitToLit;
    }

    static InteractionResult handleUseOnBlock(UseOnContext context, ItemStack stack) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null) return InteractionResult.PASS;

        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (level.isClientSide()) {
            if (block == ModBlocks.DELUMINATOR_LIGHT.get()) return InteractionResult.SUCCESS;
            if (getUnlitToLit().containsKey(block) && DeluminatorItem.getStoredLights(stack) > 0) {
                return InteractionResult.SUCCESS;
            }
            if (getLitToUnlit().containsKey(block)) return InteractionResult.SUCCESS;
            if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        if (block == ModBlocks.DELUMINATOR_LIGHT.get()) {
            level.removeBlock(pos, false);
            DeluminatorItem.adjustStoredLights(stack, 1);
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

        if (getUnlitToLit().containsKey(block) && DeluminatorItem.getStoredLights(stack) > 0) {
            level.setBlock(pos, copyBlockState(state, getUnlitToLit().get(block)), 3);
            DeluminatorItem.adjustStoredLights(stack, -1);
            level.playSound(null, pos, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

        if (getLitToUnlit().containsKey(block)) {
            level.setBlock(pos, copyBlockState(state, getLitToUnlit().get(block)), 3);
            DeluminatorItem.adjustStoredLights(stack, 1);
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), 3);
            DeluminatorItem.adjustStoredLights(stack, 1);
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    static BlockState copyBlockState(BlockState oldState, Block newBlock) {
        BlockState newState = newBlock.defaultBlockState();
        if (oldState.hasProperty(WallTorchBlock.FACING) && newState.hasProperty(WallTorchBlock.FACING)) {
            newState = newState.setValue(WallTorchBlock.FACING, oldState.getValue(WallTorchBlock.FACING));
        }
        if (oldState.hasProperty(LanternBlock.HANGING) && newState.hasProperty(LanternBlock.HANGING)) {
            newState = newState.setValue(LanternBlock.HANGING, oldState.getValue(LanternBlock.HANGING));
        }
        if (oldState.hasProperty(BlockStateProperties.WATERLOGGED) && newState.hasProperty(BlockStateProperties.WATERLOGGED)) {
            newState = newState.setValue(BlockStateProperties.WATERLOGGED, oldState.getValue(BlockStateProperties.WATERLOGGED));
        }
        return newState;
    }
}
