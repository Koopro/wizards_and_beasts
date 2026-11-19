package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.block.furniture.ArchBlock;
import at.koopro.wizardsandbeasts.block.furniture.ColumnBlock;
import at.koopro.wizardsandbeasts.block.location.DiagonAlleyBlocks;
import at.koopro.wizardsandbeasts.block.location.GringottsBlocks;
import at.koopro.wizardsandbeasts.block.location.HogsmeadeBlocks;
import at.koopro.wizardsandbeasts.block.location.HogwartsBlocks;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper;
import at.koopro.wizardsandbeasts.block.location.MinistryBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Architecture for every material family (World Decoration Renaissance, W2): two new families (the ancient ruins'
 * runestone and Knockturn's slate), the shapes the old families were missing (walls for tile sets, stairs for
 * floors, pillars, carved blocks), patterned floors, roofs, and an arch, a column and a balustrade per family.
 *
 * <p>Each block says what it is ({@link Piece}), so the model, loot, language and tag providers loop over
 * {@link #PIECES} instead of naming every block again. The masonry is registered through
 * {@link LocationBlockHelper}, so it joins the Structures module like the location sets; the arches, columns and
 * balustrades are furniture pieces (hand-authored models from {@code tools/furniture_models.py}).
 */
public final class ArchitectureBlocks {

    private ArchitectureBlocks() {}

    /** How datagen draws a piece: a cube, a log-like pillar, or a slab, stairs or wall of {@code base}. */
    public enum Shape { CUBE, PILLAR, SLAB, STAIRS, WALL }

    /** One datagen-drawn block: its shape, the block whose texture it wears, and its English name. */
    public record Piece(Shape shape, Supplier<? extends Block> block, Supplier<? extends Block> base, String name,
                        boolean wood) {}

    public static final List<Piece> PIECES = new ArrayList<>();
    /** Every block registered here (masonry and family furniture), in creative-tab order. */
    public static final List<Supplier<? extends Block>> TAB = new ArrayList<>();

    private static BlockBehaviour.Properties stone(MapColor color) {
        return LocationBlockHelper.stone(color);
    }

    private static Supplier<Block> cube(String id, String name, BlockBehaviour.Properties props) {
        var entry = LocationBlockHelper.block(id, props);
        add(new Piece(Shape.CUBE, entry.block(), entry.block(), name, false));
        return entry.block();
    }

    private static void pillar(String id, String name, BlockBehaviour.Properties props) {
        var entry = LocationBlockHelper.pillar(id, props);
        add(new Piece(Shape.PILLAR, entry.block(), entry.block(), name, false));
    }

    /** Base, slab, stairs and wall. */
    private static Supplier<Block> variants(String id, String name, BlockBehaviour.Properties props) {
        var set = LocationBlockHelper.withVariants(id, props);
        add(new Piece(Shape.CUBE, set.base(), set.base(), name, false));
        add(new Piece(Shape.SLAB, set.slab(), set.base(), name + " Slab", false));
        add(new Piece(Shape.STAIRS, set.stairs(), set.base(), name + " Stairs", false));
        add(new Piece(Shape.WALL, set.wall(), set.base(), name + " Wall", false));
        return set.base();
    }

    /** Base and slab: a floor. */
    private static void floor(String id, String name, BlockBehaviour.Properties props, boolean wood) {
        var set = LocationBlockHelper.withSlab(id, props);
        add(new Piece(Shape.CUBE, set.base(), set.base(), name, wood));
        add(new Piece(Shape.SLAB, set.slab(), set.base(), name + " Slab", wood));
    }

    /** Base, slab and stairs: a roof. */
    private static void roof(String id, String name, BlockBehaviour.Properties props) {
        var set = LocationBlockHelper.withSlabStair(id, props);
        add(new Piece(Shape.CUBE, set.base(), set.base(), name, false));
        add(new Piece(Shape.SLAB, set.slab(), set.base(), name + " Slab", false));
        add(new Piece(Shape.STAIRS, set.stairs(), set.base(), name + " Stairs", false));
    }

    private static void wallOf(String id, String name, Supplier<? extends Block> base, BlockBehaviour.Properties props) {
        add(new Piece(Shape.WALL, LocationBlockHelper.single(id, WallBlock::new, props), base, name, false));
    }

    private static void stairsOf(String id, String name, Supplier<? extends Block> base, BlockBehaviour.Properties props) {
        add(new Piece(Shape.STAIRS, LocationBlockHelper.single(id, p -> new StairBlock(base.get().defaultBlockState(), p),
                props), base, name, false));
    }

    private static void add(Piece piece) {
        PIECES.add(piece);
        TAB.add(piece.block());
    }

    // -- Ancient ruins: weathered runestone, grey-green with lichen ------------------------------------------------

    public static final Supplier<Block> ANCIENT_RUNESTONE = variants("ancient_runestone", "Ancient Runestone",
            stone(MapColor.COLOR_GRAY));
    public static final Supplier<Block> ANCIENT_RUNESTONE_BRICKS = variants("ancient_runestone_bricks",
            "Ancient Runestone Bricks", stone(MapColor.COLOR_GRAY));
    static {
        cube("ancient_cracked_runestone_bricks", "Cracked Ancient Runestone Bricks", stone(MapColor.COLOR_GRAY));
        variants("ancient_mossy_runestone_bricks", "Mossy Ancient Runestone Bricks", stone(MapColor.COLOR_GRAY));
        cube("ancient_carved_runestone", "Carved Ancient Runestone", stone(MapColor.COLOR_GRAY));
        pillar("ancient_runestone_pillar", "Ancient Runestone Pillar", stone(MapColor.COLOR_GRAY));
        floor("ancient_runic_floor", "Ancient Runic Floor", stone(MapColor.COLOR_GRAY), false);
    }

    // -- Dark wizards: Knockturn slate, near-black and cold ---------------------------------------------------------

    public static final Supplier<Block> KNOCKTURN_SLATE = variants("knockturn_slate", "Knockturn Slate",
            stone(MapColor.DEEPSLATE));
    public static final Supplier<Block> KNOCKTURN_SLATE_BRICKS = variants("knockturn_slate_bricks",
            "Knockturn Slate Bricks", stone(MapColor.DEEPSLATE));
    static {
        variants("knockturn_polished_slate", "Polished Knockturn Slate", stone(MapColor.DEEPSLATE));
        variants("knockturn_slate_tiles", "Knockturn Slate Tiles", stone(MapColor.DEEPSLATE));
        cube("knockturn_carved_slate", "Carved Knockturn Slate", stone(MapColor.DEEPSLATE));
        pillar("knockturn_slate_pillar", "Knockturn Slate Pillar", stone(MapColor.DEEPSLATE));
    }

    // -- The old families' missing shapes ---------------------------------------------------------------------------

    static {
        wallOf("diagon_brick_tiles_wall", "Diagon Brick Tile Wall", DiagonAlleyBlocks.DIAGON_BRICK_TILES.base(),
                stone(MapColor.TERRACOTTA_RED));
        wallOf("ministry_black_marble_tiles_wall", "Ministry Black Marble Tile Wall",
                MinistryBlocks.MINISTRY_BLACK_MARBLE_TILES.base(), stone(MapColor.COLOR_BLACK));
        wallOf("ministry_dark_tile_wall", "Ministry Dark Tile Wall", MinistryBlocks.MINISTRY_DARK_TILE.base(),
                stone(MapColor.COLOR_BLACK));
        wallOf("gringotts_white_marble_tiles_wall", "Gringotts White Marble Tile Wall",
                GringottsBlocks.GRINGOTTS_WHITE_MARBLE_TILES.base(), stone(MapColor.QUARTZ));
        stairsOf("hogwarts_flagstone_stairs", "Hogwarts Flagstone Stairs", HogwartsBlocks.HOGWARTS_FLAGSTONE.base(),
                stone(MapColor.COLOR_LIGHT_GRAY));
        stairsOf("hogwarts_floor_tile_stairs", "Hogwarts Floor Tile Stairs", HogwartsBlocks.HOGWARTS_FLOOR_TILE.base(),
                stone(MapColor.COLOR_LIGHT_GRAY));
        stairsOf("ministry_floor_tile_stairs", "Ministry Floor Tile Stairs", MinistryBlocks.MINISTRY_FLOOR_TILE.base(),
                stone(MapColor.COLOR_BLACK));
        stairsOf("gringotts_counting_floor_stairs", "Gringotts Counting Floor Stairs",
                GringottsBlocks.GRINGOTTS_COUNTING_FLOOR.base(), stone(MapColor.QUARTZ));
        pillar("hogsmeade_stone_pillar", "Hogsmeade Stone Pillar", stone(MapColor.COLOR_GRAY));
        pillar("gringotts_pale_marble_pillar", "Gringotts Pale Marble Pillar", stone(MapColor.SAND));
        pillar("diagon_brick_pillar", "Diagon Brick Pillar", stone(MapColor.TERRACOTTA_RED));
        cube("hogwarts_chiseled_stone_bricks", "Chiseled Hogwarts Stone Bricks", stone(MapColor.COLOR_GRAY));
        cube("hogsmeade_chiseled_stone_bricks", "Chiseled Hogsmeade Stone Bricks", stone(MapColor.COLOR_GRAY));
        cube("ministry_chiseled_black_marble", "Chiseled Ministry Black Marble", stone(MapColor.COLOR_BLACK));
        cube("gringotts_chiseled_white_marble", "Chiseled Gringotts White Marble", stone(MapColor.QUARTZ));
    }

    // -- Floors and roofs -------------------------------------------------------------------------------------------

    static {
        floor("ministry_checkered_marble", "Ministry Checkered Marble", stone(MapColor.COLOR_BLACK), false);
        floor("gilt_mosaic_floor", "Gilt Mosaic Floor", stone(MapColor.GOLD), false);
        floor("walnut_parquet", "Walnut Parquet", LocationBlockHelper.wood(MapColor.COLOR_BROWN), true);
        roof("slate_roof_tiles", "Slate Roof Tiles", stone(MapColor.COLOR_BLUE));
        roof("verdigris_roof_tiles", "Verdigris Roof Tiles", stone(MapColor.WARPED_NYLIUM));
    }

    // -- An arch, a column and a balustrade for every family (Hogwarts' are FurnitureBlocks') -----------------------

    /** A family's three pieces: the arch and column take the shared outlines; the balustrade is a stone fence. */
    public record Family(String key, String name, MapColor color) {}

    public static final List<Family> FAMILIES = List.of(
            new Family("ministry_marble", "Ministry Marble", MapColor.COLOR_BLACK),
            new Family("gringotts_marble", "Gringotts Marble", MapColor.QUARTZ),
            new Family("diagon_brick", "Diagon Brick", MapColor.TERRACOTTA_RED),
            new Family("hogsmeade_stone", "Hogsmeade Stone", MapColor.COLOR_GRAY),
            new Family("ancient_runestone", "Ancient Runestone", MapColor.COLOR_GRAY),
            new Family("knockturn_slate", "Knockturn Slate", MapColor.DEEPSLATE));
    /** The balustrades, for the fence tag. */
    public static final List<Supplier<FenceBlock>> BALUSTRADES = new ArrayList<>();
    /** The family arches, columns and balustrades, for the pickaxe tag. */
    public static final List<Supplier<? extends Block>> FAMILY_PIECES = new ArrayList<>();

    static {
        for (Family family : FAMILIES) {
            String key = family.key();
            Supplier<ArchBlock> arch = FurnitureBlocks.block(key + "_gothic_arch", family.name() + " Gothic Arch",
                    p -> new ArchBlock(p, "hogwarts_gothic_arch"), () -> stoneFurniture(family.color()));
            Supplier<ColumnBlock> column = FurnitureBlocks.block(key + "_column", family.name() + " Column",
                    p -> new ColumnBlock(p, "hogwarts_stone_column"), () -> stoneFurniture(family.color()));
            Supplier<FenceBlock> balustrade = FurnitureBlocks.block(key + "_balustrade", family.name() + " Balustrade",
                    FenceBlock::new, () -> stoneFurniture(family.color()));
            BALUSTRADES.add(balustrade);
            FAMILY_PIECES.add(arch);
            FAMILY_PIECES.add(column);
            FAMILY_PIECES.add(balustrade);
        }
    }

    private static BlockBehaviour.Properties stoneFurniture(MapColor color) {
        return stone(color).noOcclusion();
    }

    /** Pillars wear {@code <id>} on their sides and {@code <id>_top} on their ends, like a log. */
    public static boolean isPillar(Block block) {
        return block instanceof RotatedPillarBlock;
    }

    /** Slabs drop two when double. */
    public static boolean isSlab(Block block) {
        return block instanceof SlabBlock;
    }

    public static void init() { /* force class load, triggering static field registration */ }
}
