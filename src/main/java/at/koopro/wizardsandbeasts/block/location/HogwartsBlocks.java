package at.koopro.wizardsandbeasts.block.location;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.BlockEntry;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.PillarEntry;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.SlabSet;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.VariantSet;

// Blocks register unconditionally; their crafting recipes are gated behind Module.STRUCTURES (ModRecipeProvider).
// NOTE: house_banner_{gryffindor,slytherin,ravenclaw,hufflepuff} already registered in
//       WizardingWorldBlockRegistry — those four blocks are intentionally omitted here.
public final class HogwartsBlocks {
    private HogwartsBlocks() {}

    private static BlockBehaviour.Properties stone(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(1.5f, 6.0f)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops();
    }

    // ASSET: hogwarts_stone, hogwarts_stone_slab, hogwarts_stone_stairs, hogwarts_stone_wall
    // Recipe (gated by Module.STRUCTURES, see ModRecipeProvider) —stone + cobblestone
    public static final VariantSet HOGWARTS_STONE =
            LocationBlockHelper.withVariants("hogwarts_stone", stone(MapColor.COLOR_GRAY));

    // ASSET: hogwarts_stone_bricks, hogwarts_stone_bricks_slab, hogwarts_stone_bricks_stairs, hogwarts_stone_bricks_wall
    // Recipe (gated by Module.STRUCTURES, see ModRecipeProvider) —hogwarts_stone
    public static final VariantSet HOGWARTS_STONE_BRICKS =
            LocationBlockHelper.withVariants("hogwarts_stone_bricks", stone(MapColor.COLOR_GRAY));

    // ASSET: hogwarts_cracked_stone_bricks
    // Recipe (gated by Module.STRUCTURES, see ModRecipeProvider) —hogwarts_stone_bricks (smelt)
    public static final BlockEntry HOGWARTS_CRACKED_STONE_BRICKS =
            LocationBlockHelper.block("hogwarts_cracked_stone_bricks", stone(MapColor.COLOR_GRAY));

    // ASSET: hogwarts_mossy_stone_bricks, hogwarts_mossy_stone_bricks_slab, hogwarts_mossy_stone_bricks_stairs, hogwarts_mossy_stone_bricks_wall
    // Recipe (gated by Module.STRUCTURES, see ModRecipeProvider) —hogwarts_stone_bricks + vine
    public static final VariantSet HOGWARTS_MOSSY_STONE_BRICKS =
            LocationBlockHelper.withVariants("hogwarts_mossy_stone_bricks", stone(MapColor.COLOR_GRAY));

    // ASSET: hogwarts_stone_pillar (side), hogwarts_stone_pillar_top (end)
    // Recipe (gated by Module.STRUCTURES, see ModRecipeProvider) —hogwarts_stone
    public static final PillarEntry HOGWARTS_STONE_PILLAR =
            LocationBlockHelper.pillar("hogwarts_stone_pillar", stone(MapColor.COLOR_GRAY));

    // ASSET: hogwarts_dark_stone, hogwarts_dark_stone_slab, hogwarts_dark_stone_stairs, hogwarts_dark_stone_wall
    // Recipe (gated by Module.STRUCTURES, see ModRecipeProvider) —deepslate + hogwarts_stone
    public static final VariantSet HOGWARTS_DARK_STONE =
            LocationBlockHelper.withVariants("hogwarts_dark_stone", stone(MapColor.DEEPSLATE));

    // ASSET: hogwarts_flagstone, hogwarts_flagstone_slab
    // Recipe (gated by Module.STRUCTURES, see ModRecipeProvider) —hogwarts_stone
    public static final SlabSet HOGWARTS_FLAGSTONE =
            LocationBlockHelper.withSlab("hogwarts_flagstone", stone(MapColor.COLOR_LIGHT_GRAY));

    // ASSET: hogwarts_floor_tile, hogwarts_floor_tile_slab
    // Recipe (gated by Module.STRUCTURES, see ModRecipeProvider) —quartz + hogwarts_stone
    public static final SlabSet HOGWARTS_FLOOR_TILE =
            LocationBlockHelper.withSlab("hogwarts_floor_tile", stone(MapColor.COLOR_LIGHT_GRAY));

    // ASSET: enchanted_ceiling_tile
    // Recipe (gated by Module.STRUCTURES, see ModRecipeProvider) —lapis + quartz
    public static final BlockEntry ENCHANTED_CEILING_TILE =
            LocationBlockHelper.block("enchanted_ceiling_tile", stone(MapColor.COLOR_BLUE));

    public static void init() { /* force class load, triggering static field registration */ }
}
