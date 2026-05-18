package at.koopro.wizardsandbeasts.block.location;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModItems;

/**
 * Factory helpers for registering common location block variant combinations.
 * All factory methods register blocks into {@link ModBlocks#BLOCKS} and items into
 * {@link ModItems#ITEMS}.
 */
public final class LocationBlockHelper {
    private LocationBlockHelper() {}

    // --- Return types ---

    public record BlockEntry(
            DeferredBlock<Block> block,
            DeferredItem<BlockItem> item) {}

    public record PillarEntry(
            DeferredBlock<RotatedPillarBlock> block,
            DeferredItem<BlockItem> item) {}

    public record SlabSet(
            DeferredBlock<Block> base,
            DeferredBlock<SlabBlock> slab,
            DeferredItem<BlockItem> baseItem,
            DeferredItem<BlockItem> slabItem) {}

    public record StairSet(
            DeferredBlock<Block> base,
            DeferredBlock<SlabBlock> slab,
            DeferredBlock<StairBlock> stairs,
            DeferredItem<BlockItem> baseItem,
            DeferredItem<BlockItem> slabItem,
            DeferredItem<BlockItem> stairsItem) {}

    public record VariantSet(
            DeferredBlock<Block> base,
            DeferredBlock<SlabBlock> slab,
            DeferredBlock<StairBlock> stairs,
            DeferredBlock<WallBlock> wall,
            DeferredItem<BlockItem> baseItem,
            DeferredItem<BlockItem> slabItem,
            DeferredItem<BlockItem> stairsItem,
            DeferredItem<BlockItem> wallItem) {}

    // --- Factories ---

    public static BlockEntry block(String id, BlockBehaviour.Properties props) {
        var base = ModBlocks.BLOCKS.registerBlock(id, Block::new, () -> props);
        return new BlockEntry(base, ModItems.ITEMS.registerSimpleBlockItem(base));
    }

    public static PillarEntry pillar(String id, BlockBehaviour.Properties props) {
        var base = ModBlocks.BLOCKS.registerBlock(id, RotatedPillarBlock::new, () -> props);
        return new PillarEntry(base, ModItems.ITEMS.registerSimpleBlockItem(base));
    }

    public static SlabSet withSlab(String id, BlockBehaviour.Properties props) {
        var base = ModBlocks.BLOCKS.registerBlock(id, Block::new, () -> props);
        var slab = ModBlocks.BLOCKS.registerBlock(id + "_slab", SlabBlock::new, () -> props);
        return new SlabSet(
                base, slab,
                ModItems.ITEMS.registerSimpleBlockItem(base),
                ModItems.ITEMS.registerSimpleBlockItem(slab));
    }

    public static StairSet withSlabStair(String id, BlockBehaviour.Properties props) {
        var base = ModBlocks.BLOCKS.registerBlock(id, Block::new, () -> props);
        var slab = ModBlocks.BLOCKS.registerBlock(id + "_slab", SlabBlock::new, () -> props);
        var stairs = ModBlocks.BLOCKS.registerBlock(id + "_stairs",
                p -> new StairBlock(base.get().defaultBlockState(), p), () -> props);
        return new StairSet(
                base, slab, stairs,
                ModItems.ITEMS.registerSimpleBlockItem(base),
                ModItems.ITEMS.registerSimpleBlockItem(slab),
                ModItems.ITEMS.registerSimpleBlockItem(stairs));
    }

    public static VariantSet withVariants(String id, BlockBehaviour.Properties props) {
        var base = ModBlocks.BLOCKS.registerBlock(id, Block::new, () -> props);
        var slab = ModBlocks.BLOCKS.registerBlock(id + "_slab", SlabBlock::new, () -> props);
        var stairs = ModBlocks.BLOCKS.registerBlock(id + "_stairs",
                p -> new StairBlock(base.get().defaultBlockState(), p), () -> props);
        var wall = ModBlocks.BLOCKS.registerBlock(id + "_wall", WallBlock::new, () -> props);
        return new VariantSet(
                base, slab, stairs, wall,
                ModItems.ITEMS.registerSimpleBlockItem(base),
                ModItems.ITEMS.registerSimpleBlockItem(slab),
                ModItems.ITEMS.registerSimpleBlockItem(stairs),
                ModItems.ITEMS.registerSimpleBlockItem(wall));
    }

    /** Stone pressure plate registered separately (only needed for diagon_street_stone). */
    static DeferredBlock<PressurePlateBlock> stonePressurePlate(String id, BlockBehaviour.Properties props) {
        return ModBlocks.BLOCKS.registerBlock(id, p -> new PressurePlateBlock(BlockSetType.STONE, p), () -> props);
    }
}
