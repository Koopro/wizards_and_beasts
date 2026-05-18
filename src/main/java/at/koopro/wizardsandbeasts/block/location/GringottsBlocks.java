package at.koopro.wizardsandbeasts.block.location;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.BlockEntry;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.PillarEntry;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.SlabSet;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.StairSet;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.VariantSet;

// TODO: gate behind STRUCTURES module when added
public final class GringottsBlocks {
    private GringottsBlocks() {}

    private static BlockBehaviour.Properties marble(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(1.5f, 6.0f)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties vault(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(3.0f, 9.0f)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops();
    }

    // ASSET: gringotts_white_marble, gringotts_white_marble_slab, gringotts_white_marble_stairs, gringotts_white_marble_wall
    // TODO: crafting recipe — quartz + calcite
    public static final VariantSet GRINGOTTS_WHITE_MARBLE =
            LocationBlockHelper.withVariants("gringotts_white_marble", marble(MapColor.QUARTZ));

    // ASSET: gringotts_white_marble_pillar (side), gringotts_white_marble_pillar_top (end)
    // TODO: crafting recipe — gringotts_white_marble
    public static final PillarEntry GRINGOTTS_WHITE_MARBLE_PILLAR =
            LocationBlockHelper.pillar("gringotts_white_marble_pillar", marble(MapColor.QUARTZ));

    // ASSET: gringotts_white_marble_tiles, gringotts_white_marble_tiles_slab, gringotts_white_marble_tiles_stairs
    // TODO: crafting recipe — gringotts_white_marble
    public static final StairSet GRINGOTTS_WHITE_MARBLE_TILES =
            LocationBlockHelper.withSlabStair("gringotts_white_marble_tiles", marble(MapColor.QUARTZ));

    // ASSET: gringotts_pale_marble, gringotts_pale_marble_slab, gringotts_pale_marble_stairs, gringotts_pale_marble_wall
    // TODO: crafting recipe — calcite + diorite
    public static final VariantSet GRINGOTTS_PALE_MARBLE =
            LocationBlockHelper.withVariants("gringotts_pale_marble", marble(MapColor.COLOR_LIGHT_GRAY));

    // ASSET: gringotts_gold_trim
    // TODO: crafting recipe — gold ingot + gringotts_white_marble
    public static final BlockEntry GRINGOTTS_GOLD_TRIM =
            LocationBlockHelper.block("gringotts_gold_trim", marble(MapColor.GOLD));

    // ASSET: gringotts_iron_vault_stone, gringotts_iron_vault_stone_slab, gringotts_iron_vault_stone_stairs, gringotts_iron_vault_stone_wall
    // TODO: crafting recipe — stone + iron ingot
    public static final VariantSet GRINGOTTS_IRON_VAULT_STONE =
            LocationBlockHelper.withVariants("gringotts_iron_vault_stone", vault(MapColor.METAL));

    // ASSET: gringotts_vault_bricks, gringotts_vault_bricks_slab, gringotts_vault_bricks_stairs, gringotts_vault_bricks_wall
    // TODO: crafting recipe — gringotts_iron_vault_stone
    public static final VariantSet GRINGOTTS_VAULT_BRICKS =
            LocationBlockHelper.withVariants("gringotts_vault_bricks", vault(MapColor.METAL));

    // ASSET: gringotts_goblin_stonework, gringotts_goblin_stonework_slab, gringotts_goblin_stonework_stairs, gringotts_goblin_stonework_wall
    // TODO: crafting recipe — chiseled stone bricks + gold nugget
    public static final VariantSet GRINGOTTS_GOBLIN_STONEWORK =
            LocationBlockHelper.withVariants("gringotts_goblin_stonework", marble(MapColor.COLOR_GRAY));

    // ASSET: gringotts_counting_floor, gringotts_counting_floor_slab
    // TODO: crafting recipe — gringotts_white_marble + gold ingot
    public static final SlabSet GRINGOTTS_COUNTING_FLOOR =
            LocationBlockHelper.withSlab("gringotts_counting_floor", marble(MapColor.COLOR_LIGHT_GRAY));

    public static void init() { /* force class load, triggering static field registration */ }
}
