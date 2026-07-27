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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Factory helpers for registering common location block variant combinations.
 * All factory methods register blocks into {@link ModBlocks#BLOCKS} and items into
 * {@link ModItems#ITEMS}.
 */
public final class LocationBlockHelper {
    private LocationBlockHelper() {}

    /**
     * Every block and item these factories have handed out, in registration order.
     *
     * <p>Kept so the module tag providers can name the whole decorative build set without a 131-line
     * hand-written list that silently rots the next time someone adds a marble variant. Every caller of
     * this class is a location build set, so "registered through here" is exactly {@code Module.STRUCTURES}.
     * The three blocks {@code DiagonAlleyBlocks} registers directly are not in here and are listed by hand.
     */
    private static final List<DeferredBlock<? extends Block>> ALL_BLOCKS = new ArrayList<>();
    private static final List<DeferredItem<BlockItem>> ALL_ITEMS = new ArrayList<>();

    public static List<DeferredBlock<? extends Block>> allBlocks() {
        return Collections.unmodifiableList(ALL_BLOCKS);
    }

    public static List<DeferredItem<BlockItem>> allItems() {
        return Collections.unmodifiableList(ALL_ITEMS);
    }

    private static <T extends Block> DeferredBlock<T> track(DeferredBlock<T> block) {
        ALL_BLOCKS.add(block);
        return block;
    }

    private static DeferredItem<BlockItem> trackItem(DeferredItem<BlockItem> item) {
        ALL_ITEMS.add(item);
        return item;
    }

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
        var base = track(ModBlocks.BLOCKS.registerBlock(id, Block::new, () -> props));
        return new BlockEntry(base, trackItem(ModItems.ITEMS.registerSimpleBlockItem(base)));
    }

    public static PillarEntry pillar(String id, BlockBehaviour.Properties props) {
        var base = track(ModBlocks.BLOCKS.registerBlock(id, RotatedPillarBlock::new, () -> props));
        return new PillarEntry(base, trackItem(ModItems.ITEMS.registerSimpleBlockItem(base)));
    }

    public static SlabSet withSlab(String id, BlockBehaviour.Properties props) {
        var base = track(ModBlocks.BLOCKS.registerBlock(id, Block::new, () -> props));
        var slab = track(ModBlocks.BLOCKS.registerBlock(id + "_slab", SlabBlock::new, () -> props));
        return new SlabSet(
                base, slab,
                trackItem(ModItems.ITEMS.registerSimpleBlockItem(base)),
                trackItem(ModItems.ITEMS.registerSimpleBlockItem(slab)));
    }

    public static StairSet withSlabStair(String id, BlockBehaviour.Properties props) {
        var base = track(ModBlocks.BLOCKS.registerBlock(id, Block::new, () -> props));
        var slab = track(ModBlocks.BLOCKS.registerBlock(id + "_slab", SlabBlock::new, () -> props));
        var stairs = track(ModBlocks.BLOCKS.registerBlock(id + "_stairs",
                p -> new StairBlock(base.get().defaultBlockState(), p), () -> props));
        return new StairSet(
                base, slab, stairs,
                trackItem(ModItems.ITEMS.registerSimpleBlockItem(base)),
                trackItem(ModItems.ITEMS.registerSimpleBlockItem(slab)),
                trackItem(ModItems.ITEMS.registerSimpleBlockItem(stairs)));
    }

    public static VariantSet withVariants(String id, BlockBehaviour.Properties props) {
        var base = track(ModBlocks.BLOCKS.registerBlock(id, Block::new, () -> props));
        var slab = track(ModBlocks.BLOCKS.registerBlock(id + "_slab", SlabBlock::new, () -> props));
        var stairs = track(ModBlocks.BLOCKS.registerBlock(id + "_stairs",
                p -> new StairBlock(base.get().defaultBlockState(), p), () -> props));
        var wall = track(ModBlocks.BLOCKS.registerBlock(id + "_wall", WallBlock::new, () -> props));
        return new VariantSet(
                base, slab, stairs, wall,
                trackItem(ModItems.ITEMS.registerSimpleBlockItem(base)),
                trackItem(ModItems.ITEMS.registerSimpleBlockItem(slab)),
                trackItem(ModItems.ITEMS.registerSimpleBlockItem(stairs)),
                trackItem(ModItems.ITEMS.registerSimpleBlockItem(wall)));
    }

    /** Stone pressure plate registered separately (only needed for diagon_street_stone). */
    static DeferredBlock<PressurePlateBlock> stonePressurePlate(String id, BlockBehaviour.Properties props) {
        return track(ModBlocks.BLOCKS.registerBlock(id, p -> new PressurePlateBlock(BlockSetType.STONE, p), () -> props));
    }
}
