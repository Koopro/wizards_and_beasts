package at.koopro.wizardsandbeasts.floo.hearth;

import at.koopro.wizardsandbeasts.block.floo.FlooGrateBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooGrateBlockEntity;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * Builds a whole hearth of a style in the world, block by block from its pattern: for captures, GameTests and places
 * the mod builds itself. Players build theirs by hand; this is the same pattern placed for them. A tag symbol takes
 * the tag's first block; a log laid as a mantel lies across the opening.
 */
@NullMarked
public final class HearthStamp {

    private HearthStamp() {
    }

    /** Builds {@code style} with its grate at {@code grate}, front toward {@code facing}; false if the style is unknown. */
    public static boolean stamp(ServerLevel level, BlockPos grate, Direction facing, Identifier style) {
        Optional<HearthPattern> pattern = HearthPatterns.get(style);
        if (pattern.isEmpty()) {
            return false;
        }
        Direction.Axis across = facing.getCounterClockWise().getAxis();
        for (HearthPattern.Cell cell : pattern.get().cells()) {
            BlockPos at = HearthPattern.place(grate, facing, cell);
            BlockState state = switch (cell.symbol()) {
                case HearthPattern.GRATE -> ModBlocks.FLOO_GRATE.get().defaultBlockState().setValue(FlooGrateBlock.FACING, facing);
                case HearthPattern.OPEN -> Blocks.AIR.defaultBlockState();
                default -> first(pattern.get().key().getOrDefault(String.valueOf(cell.symbol()), "minecraft:stone"));
            };
            if (state.hasProperty(BlockStateProperties.AXIS)) {
                state = state.setValue(BlockStateProperties.AXIS, across);
            }
            level.setBlock(at, state, 3);
        }
        if (level.getBlockEntity(grate) instanceof FlooGrateBlockEntity be) {
            be.recheck(level, level.getBlockState(grate));
        }
        return true;
    }

    private static BlockState first(String entry) {
        if (entry.startsWith("#")) {
            Identifier tag = Identifier.tryParse(entry.substring(1));
            if (tag != null) {
                for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(TagKey.create(Registries.BLOCK, tag))) {
                    return holder.value().defaultBlockState();
                }
            }
            return Blocks.STONE.defaultBlockState();
        }
        Identifier id = Identifier.tryParse(entry);
        return (id == null ? Optional.<Block>empty() : BuiltInRegistries.BLOCK.getOptional(id))
                .map(Block::defaultBlockState).orElse(Blocks.STONE.defaultBlockState());
    }
}
