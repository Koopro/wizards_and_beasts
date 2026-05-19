package at.koopro.wizardsandbeasts.deluminator;

import at.koopro.wizardsandbeasts.item.deluminator.DeluminatorItem;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * Block-targeting absorb / restore logic for the Deluminator (server-side).
 */
public final class DeluminatorBlockActions {

    private DeluminatorBlockActions() {}

    private static Map<Block, Block> litToUnlit;
    private static Map<Block, Block> unlitToLit;

    static Map<Block, Block> getLitToUnlit() {
        if (litToUnlit == null) {
            Map<Block, Block> map = new HashMap<>();
            map.put(Blocks.TORCH, ModBlocks.UNLIT_TORCH.get());
            map.put(Blocks.WALL_TORCH, ModBlocks.UNLIT_WALL_TORCH.get());
            map.put(Blocks.COPPER_TORCH, ModBlocks.UNLIT_COPPER_TORCH.get());
            map.put(Blocks.COPPER_WALL_TORCH, ModBlocks.UNLIT_COPPER_WALL_TORCH.get());
            map.put(Blocks.SOUL_TORCH, ModBlocks.UNLIT_SOUL_TORCH.get());
            map.put(Blocks.SOUL_WALL_TORCH, ModBlocks.UNLIT_SOUL_WALL_TORCH.get());
            map.put(Blocks.LANTERN, ModBlocks.UNLIT_LANTERN.get());
            map.put(Blocks.SOUL_LANTERN, ModBlocks.UNLIT_SOUL_LANTERN.get());
            map.put(Blocks.GLOWSTONE, ModBlocks.UNLIT_GLOWSTONE.get());

            addMappingIfPresent(map, "copper_lantern", ModBlocks.UNLIT_COPPER_LANTERN.get());
            addMappingIfPresent(map, "exposed_copper_lantern", ModBlocks.UNLIT_COPPER_LANTERN.get());
            addMappingIfPresent(map, "weathered_copper_lantern", ModBlocks.UNLIT_COPPER_LANTERN.get());
            addMappingIfPresent(map, "oxidized_copper_lantern", ModBlocks.UNLIT_COPPER_LANTERN.get());
            addMappingIfPresent(map, "waxed_copper_lantern", ModBlocks.UNLIT_COPPER_LANTERN.get());
            addMappingIfPresent(map, "waxed_exposed_copper_lantern", ModBlocks.UNLIT_COPPER_LANTERN.get());
            addMappingIfPresent(map, "waxed_weathered_copper_lantern", ModBlocks.UNLIT_COPPER_LANTERN.get());
            addMappingIfPresent(map, "waxed_oxidized_copper_lantern", ModBlocks.UNLIT_COPPER_LANTERN.get());
            litToUnlit = map;
        }
        return litToUnlit;
    }

    static Map<Block, Block> getUnlitToLit() {
        if (unlitToLit == null) {
            Map<Block, Block> map = new HashMap<>();
            map.put(ModBlocks.UNLIT_TORCH.get(), Blocks.TORCH);
            map.put(ModBlocks.UNLIT_WALL_TORCH.get(), Blocks.WALL_TORCH);
            map.put(ModBlocks.UNLIT_COPPER_TORCH.get(), Blocks.COPPER_TORCH);
            map.put(ModBlocks.UNLIT_COPPER_WALL_TORCH.get(), Blocks.COPPER_WALL_TORCH);
            map.put(ModBlocks.UNLIT_SOUL_TORCH.get(), Blocks.SOUL_TORCH);
            map.put(ModBlocks.UNLIT_SOUL_WALL_TORCH.get(), Blocks.SOUL_WALL_TORCH);
            map.put(ModBlocks.UNLIT_LANTERN.get(), Blocks.LANTERN);
            map.put(ModBlocks.UNLIT_SOUL_LANTERN.get(), Blocks.SOUL_LANTERN);
            map.put(ModBlocks.UNLIT_GLOWSTONE.get(), Blocks.GLOWSTONE);

            Block copperLantern = getBlockIfPresent("copper_lantern");
            map.put(ModBlocks.UNLIT_COPPER_LANTERN.get(), copperLantern != null ? copperLantern : Blocks.LANTERN);
            unlitToLit = map;
        }
        return unlitToLit;
    }

    private static void addMappingIfPresent(Map<Block, Block> map, String blockId, Block replacement) {
        Block block = getBlockIfPresent(blockId);
        if (block != null) {
            map.put(block, replacement);
        }
    }

    private static Block getBlockIfPresent(String blockId) {
        Identifier id = Identifier.withDefaultNamespace(blockId);
        Block block = BuiltInRegistries.BLOCK.get(id).map(holder -> holder.value()).orElse(Blocks.AIR);
        return block == Blocks.AIR ? null : block;
    }

    public static InteractionResult handleUseOnBlock(UseOnContext context, ItemStack stack) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null) return InteractionResult.PASS;

        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (level.isClientSide()) {
            if (block == ModBlocks.DELUMINATOR_LIGHT.get() && DeluminatorItem.canStoreMoreLights(stack)) {
                return InteractionResult.SUCCESS;
            }
            if (getUnlitToLit().containsKey(block) && DeluminatorItem.getStoredLights(stack) > 0) {
                return InteractionResult.SUCCESS;
            }
            if (getLitToUnlit().containsKey(block) && DeluminatorItem.canStoreMoreLights(stack)) {
                return InteractionResult.SUCCESS;
            }
            if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
                return DeluminatorItem.canStoreMoreLights(stack) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            if (state.hasProperty(BlockStateProperties.LIT)
                    && !state.getValue(BlockStateProperties.LIT)
                    && DeluminatorItem.getStoredLights(stack) > 0) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        if (block == ModBlocks.DELUMINATOR_LIGHT.get() && DeluminatorItem.canStoreMoreLights(stack)) {
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

        // Generic restore path for vanilla "lamp-like" blocks (e.g., copper bulbs)
        // that encode light state via BlockStateProperties.LIT.
        if (state.hasProperty(BlockStateProperties.LIT)
                && !state.getValue(BlockStateProperties.LIT)
                && DeluminatorItem.getStoredLights(stack) > 0) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), 3);
            DeluminatorItem.adjustStoredLights(stack, -1);
            level.playSound(null, pos, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

        if (getLitToUnlit().containsKey(block) && DeluminatorItem.canStoreMoreLights(stack)) {
            level.setBlock(pos, copyBlockState(state, getLitToUnlit().get(block)), 3);
            DeluminatorItem.adjustStoredLights(stack, 1);
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

        if (state.hasProperty(BlockStateProperties.LIT)
                && state.getValue(BlockStateProperties.LIT)
                && DeluminatorItem.canStoreMoreLights(stack)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), 3);
            DeluminatorItem.adjustStoredLights(stack, 1);
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    public static int absorbNearbyLights(Level level, BlockPos center, ItemStack stack, int radius) {
        if (level.isClientSide() || !DeluminatorItem.canStoreMoreLights(stack)) {
            return 0;
        }

        List<BlockPos> candidates = collectCandidatePositions(level, center, radius);
        int absorbed = 0;

        for (BlockPos pos : candidates) {
            if (!DeluminatorItem.canStoreMoreLights(stack)) {
                break;
            }

            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();

            if (getLitToUnlit().containsKey(block)) {
                level.setBlock(pos, copyBlockState(state, getLitToUnlit().get(block)), 3);
                DeluminatorItem.adjustStoredLights(stack, 1);
                absorbed++;
                continue;
            }

            if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
                level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), 3);
                DeluminatorItem.adjustStoredLights(stack, 1);
                absorbed++;
            }
        }

        if (absorbed > 0) {
            level.playSound(null, center, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        return absorbed;
    }

    public static int restoreNearbyLights(Level level, BlockPos center, ItemStack stack, int radius) {
        if (level.isClientSide() || DeluminatorItem.getStoredLights(stack) <= 0) {
            return 0;
        }

        List<BlockPos> candidates = collectCandidatePositions(level, center, radius);
        int restored = 0;

        for (BlockPos pos : candidates) {
            if (DeluminatorItem.getStoredLights(stack) <= 0) {
                break;
            }

            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();

            if (getUnlitToLit().containsKey(block)) {
                level.setBlock(pos, copyBlockState(state, getUnlitToLit().get(block)), 3);
                DeluminatorItem.adjustStoredLights(stack, -1);
                restored++;
                continue;
            }

            if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) {
                level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), 3);
                DeluminatorItem.adjustStoredLights(stack, -1);
                restored++;
            }
        }

        if (restored > 0) {
            level.playSound(null, center, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        return restored;
    }

    private static List<BlockPos> collectCandidatePositions(Level level, BlockPos center, int radius) {
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            BlockPos immutable = pos.immutable();
            if (immutable.equals(center)) {
                continue;
            }
            if (!level.isLoaded(immutable)) {
                continue;
            }
            positions.add(immutable);
        }
        positions.sort((a, b) -> Double.compare(a.distSqr(center), b.distSqr(center)));
        return positions;
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
