package at.koopro.wizardsandbeasts.block.location;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.StairSet;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.VariantSet;

// TODO: gate behind STRUCTURES module when added
public final class HogsmeadeBlocks {
    private HogsmeadeBlocks() {}

    private static BlockBehaviour.Properties stone(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(1.5f, 6.0f)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties wood(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(2.0f, 3.0f)
                .sound(SoundType.WOOD);
    }

    // ASSET: hogsmeade_stone, hogsmeade_stone_slab, hogsmeade_stone_stairs, hogsmeade_stone_wall
    // TODO: crafting recipe — stone + andesite
    public static final VariantSet HOGSMEADE_STONE =
            LocationBlockHelper.withVariants("hogsmeade_stone", stone(MapColor.COLOR_GRAY));

    // ASSET: hogsmeade_stone_bricks, hogsmeade_stone_bricks_slab, hogsmeade_stone_bricks_stairs, hogsmeade_stone_bricks_wall
    // TODO: crafting recipe — hogsmeade_stone
    public static final VariantSet HOGSMEADE_STONE_BRICKS =
            LocationBlockHelper.withVariants("hogsmeade_stone_bricks", stone(MapColor.COLOR_GRAY));

    // ASSET: hogsmeade_worn_stone, hogsmeade_worn_stone_slab, hogsmeade_worn_stone_stairs, hogsmeade_worn_stone_wall
    // TODO: crafting recipe — hogsmeade_stone (smelt/weather)
    public static final VariantSet HOGSMEADE_WORN_STONE =
            LocationBlockHelper.withVariants("hogsmeade_worn_stone", stone(MapColor.COLOR_LIGHT_GRAY));

    // ASSET: three_broomsticks_timber, three_broomsticks_timber_slab, three_broomsticks_timber_stairs
    // TODO: crafting recipe — dark oak log + iron nugget
    public static final StairSet THREE_BROOMSTICKS_TIMBER =
            LocationBlockHelper.withSlabStair("three_broomsticks_timber", wood(MapColor.COLOR_BROWN));

    // ASSET: three_broomsticks_planks, three_broomsticks_planks_slab, three_broomsticks_planks_stairs
    // TODO: crafting recipe — dark oak planks
    public static final StairSet THREE_BROOMSTICKS_PLANKS =
            LocationBlockHelper.withSlabStair("three_broomsticks_planks", wood(MapColor.COLOR_BROWN));

    // ASSET: honeydukes_pastel_pink, honeydukes_pastel_pink_slab, honeydukes_pastel_pink_stairs
    // TODO: crafting recipe — quartz + pink dye
    public static final StairSet HONEYDUKES_PASTEL_PINK =
            LocationBlockHelper.withSlabStair("honeydukes_pastel_pink", stone(MapColor.COLOR_PINK));

    // ASSET: honeydukes_pastel_yellow, honeydukes_pastel_yellow_slab, honeydukes_pastel_yellow_stairs
    // TODO: crafting recipe — quartz + yellow dye
    public static final StairSet HONEYDUKES_PASTEL_YELLOW =
            LocationBlockHelper.withSlabStair("honeydukes_pastel_yellow", stone(MapColor.COLOR_YELLOW));

    // ASSET: hogsmeade_roof_tile, hogsmeade_roof_tile_slab, hogsmeade_roof_tile_stairs
    // TODO: crafting recipe — stone + cobblestone slab
    public static final StairSet HOGSMEADE_ROOF_TILE =
            LocationBlockHelper.withSlabStair("hogsmeade_roof_tile", stone(MapColor.COLOR_GRAY));

    // ASSET: hogsmeade_chimney_brick, hogsmeade_chimney_brick_slab, hogsmeade_chimney_brick_stairs, hogsmeade_chimney_brick_wall
    // TODO: crafting recipe — brick + red terracotta
    public static final VariantSet HOGSMEADE_CHIMNEY_BRICK =
            LocationBlockHelper.withVariants("hogsmeade_chimney_brick", stone(MapColor.TERRACOTTA_RED));

    public static void init() { /* force class load, triggering static field registration */ }
}
