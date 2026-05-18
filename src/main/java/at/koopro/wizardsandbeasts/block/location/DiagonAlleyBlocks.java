package at.koopro.wizardsandbeasts.block.location;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.StairSet;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.VariantSet;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModItems;

// TODO: gate behind STRUCTURES module when added
public final class DiagonAlleyBlocks {
    private DiagonAlleyBlocks() {}

    private static BlockBehaviour.Properties stone(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(2.0f, 6.0f)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties wood(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(2.0f, 3.0f)
                .sound(SoundType.WOOD);
    }

    // ASSET: diagon_brick, diagon_brick_slab, diagon_brick_stairs, diagon_brick_wall
    // TODO: crafting recipe — brick + terracotta
    public static final VariantSet DIAGON_BRICK =
            LocationBlockHelper.withVariants("diagon_brick", stone(MapColor.COLOR_ORANGE));

    // ASSET: diagon_brick_tiles, diagon_brick_tiles_slab, diagon_brick_tiles_stairs
    // TODO: crafting recipe — diagon_brick
    public static final StairSet DIAGON_BRICK_TILES =
            LocationBlockHelper.withSlabStair("diagon_brick_tiles", stone(MapColor.COLOR_ORANGE));

    // ASSET: diagon_worn_brick, diagon_worn_brick_slab, diagon_worn_brick_stairs, diagon_worn_brick_wall
    // TODO: crafting recipe — diagon_brick (age/smelt)
    public static final VariantSet DIAGON_WORN_BRICK =
            LocationBlockHelper.withVariants("diagon_worn_brick", stone(MapColor.TERRACOTTA_ORANGE));

    // ASSET: diagon_cobblestone, diagon_cobblestone_slab, diagon_cobblestone_stairs, diagon_cobblestone_wall
    // TODO: crafting recipe — cobblestone + flint
    public static final VariantSet DIAGON_COBBLESTONE =
            LocationBlockHelper.withVariants("diagon_cobblestone", stone(MapColor.COLOR_GRAY));

    // ASSET: diagon_shopfront_wood, diagon_shopfront_wood_slab, diagon_shopfront_wood_stairs
    // TODO: crafting recipe — dark oak log + copper ingot
    public static final StairSet DIAGON_SHOPFRONT_WOOD =
            LocationBlockHelper.withSlabStair("diagon_shopfront_wood", wood(MapColor.COLOR_BROWN));

    // ASSET: diagon_shopfront_planks, diagon_shopfront_planks_slab, diagon_shopfront_planks_stairs
    // TODO: crafting recipe — dark oak planks + copper ingot
    public static final StairSet DIAGON_SHOPFRONT_PLANKS =
            LocationBlockHelper.withSlabStair("diagon_shopfront_planks", wood(MapColor.COLOR_BROWN));

    // ASSET: diagon_painted_wood_green, diagon_painted_wood_green_slab, diagon_painted_wood_green_stairs
    // TODO: crafting recipe — planks + green dye
    public static final StairSet DIAGON_PAINTED_WOOD_GREEN =
            LocationBlockHelper.withSlabStair("diagon_painted_wood_green", wood(MapColor.COLOR_GREEN));

    // ASSET: diagon_painted_wood_purple, diagon_painted_wood_purple_slab, diagon_painted_wood_purple_stairs
    // TODO: crafting recipe — planks + purple dye
    public static final StairSet DIAGON_PAINTED_WOOD_PURPLE =
            LocationBlockHelper.withSlabStair("diagon_painted_wood_purple", wood(MapColor.COLOR_PURPLE));

    // Diagon Street Stone — base + slab + pressure plate (no stairs, no wall)
    // Registered manually to avoid double-registration from helper calls.
    // ASSET: diagon_street_stone, diagon_street_stone_slab, diagon_street_stone_pressure_plate
    // TODO: crafting recipe — stone bricks + gravel
    private static final BlockBehaviour.Properties STREET_PROPS =
            stone(MapColor.COLOR_LIGHT_GRAY);

    public static final DeferredBlock<Block> DIAGON_STREET_STONE =
            ModBlocks.BLOCKS.registerBlock("diagon_street_stone", Block::new, () -> STREET_PROPS);
    public static final DeferredBlock<SlabBlock> DIAGON_STREET_STONE_SLAB =
            ModBlocks.BLOCKS.registerBlock("diagon_street_stone_slab", SlabBlock::new, () -> STREET_PROPS);
    public static final DeferredBlock<PressurePlateBlock> DIAGON_STREET_STONE_PRESSURE_PLATE =
            ModBlocks.BLOCKS.registerBlock("diagon_street_stone_pressure_plate",
                    p -> new PressurePlateBlock(BlockSetType.STONE, p), () -> STREET_PROPS);

    public static final DeferredItem<BlockItem> DIAGON_STREET_STONE_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem(DIAGON_STREET_STONE);
    public static final DeferredItem<BlockItem> DIAGON_STREET_STONE_SLAB_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem(DIAGON_STREET_STONE_SLAB);
    public static final DeferredItem<BlockItem> DIAGON_STREET_STONE_PRESSURE_PLATE_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem(DIAGON_STREET_STONE_PRESSURE_PLATE);

    public static void init() { /* force class load, triggering static field registration */ }
}
