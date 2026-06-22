package at.koopro.wizardsandbeasts.block.location;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.BlockEntry;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.PillarEntry;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.SlabSet;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.StairSet;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.VariantSet;

// Blocks register unconditionally; their crafting recipes are gated behind Module.STRUCTURES (ModRecipeProvider).
public final class MinistryBlocks {
    private MinistryBlocks() {}

    private static BlockBehaviour.Properties stone(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(1.5f, 6.0f)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops();
    }

    // ASSET: ministry_black_marble, ministry_black_marble_slab, ministry_black_marble_stairs, ministry_black_marble_wall
    // Recipe (gated by Module.STRUCTURES, see ModRecipeProvider) —blackstone + black dye
    public static final VariantSet MINISTRY_BLACK_MARBLE =
            LocationBlockHelper.withVariants("ministry_black_marble", stone(MapColor.COLOR_BLACK));

    // ASSET: ministry_black_marble_pillar (side), ministry_black_marble_pillar_top (end)
    // Recipe (gated by Module.STRUCTURES, see ModRecipeProvider) —ministry_black_marble
    public static final PillarEntry MINISTRY_BLACK_MARBLE_PILLAR =
            LocationBlockHelper.pillar("ministry_black_marble_pillar", stone(MapColor.COLOR_BLACK));

    // ASSET: ministry_black_marble_tiles, ministry_black_marble_tiles_slab, ministry_black_marble_tiles_stairs
    // Recipe (gated by Module.STRUCTURES, see ModRecipeProvider) —ministry_black_marble
    public static final StairSet MINISTRY_BLACK_MARBLE_TILES =
            LocationBlockHelper.withSlabStair("ministry_black_marble_tiles", stone(MapColor.COLOR_BLACK));

    // ASSET: ministry_gilded_black_marble, ministry_gilded_black_marble_slab, ministry_gilded_black_marble_stairs, ministry_gilded_black_marble_wall
    // Recipe (gated by Module.STRUCTURES, see ModRecipeProvider) —blackstone + gold ingot
    public static final VariantSet MINISTRY_GILDED_BLACK_MARBLE =
            LocationBlockHelper.withVariants("ministry_gilded_black_marble", stone(MapColor.COLOR_BLACK));

    // ASSET: ministry_gilded_trim
    // Recipe (gated by Module.STRUCTURES, see ModRecipeProvider) —gold ingot + ministry_black_marble
    public static final BlockEntry MINISTRY_GILDED_TRIM =
            LocationBlockHelper.block("ministry_gilded_trim", stone(MapColor.GOLD));

    // ASSET: ministry_dark_tile, ministry_dark_tile_slab, ministry_dark_tile_stairs
    // Recipe (gated by Module.STRUCTURES, see ModRecipeProvider) —deepslate tiles
    public static final StairSet MINISTRY_DARK_TILE =
            LocationBlockHelper.withSlabStair("ministry_dark_tile", stone(MapColor.COLOR_GRAY));

    // ASSET: ministry_floor_tile, ministry_floor_tile_slab
    // Recipe (gated by Module.STRUCTURES, see ModRecipeProvider) —quartz + diorite
    public static final SlabSet MINISTRY_FLOOR_TILE =
            LocationBlockHelper.withSlab("ministry_floor_tile", stone(MapColor.COLOR_LIGHT_GRAY));

    // ASSET: ministry_wall_panel
    // Recipe (gated by Module.STRUCTURES, see ModRecipeProvider) —polished deepslate
    public static final BlockEntry MINISTRY_WALL_PANEL =
            LocationBlockHelper.block("ministry_wall_panel", stone(MapColor.COLOR_GRAY));

    public static void init() { /* force class load, triggering static field registration */ }
}
