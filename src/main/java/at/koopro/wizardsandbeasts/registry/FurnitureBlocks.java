package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.block.furniture.ArchBlock;
import at.koopro.wizardsandbeasts.block.furniture.ColumnBlock;
import at.koopro.wizardsandbeasts.block.furniture.ConnectingFurnitureBlock;
import at.koopro.wizardsandbeasts.block.furniture.FloatingCandlesBlock;
import at.koopro.wizardsandbeasts.block.furniture.FurnitureBlock;
import at.koopro.wizardsandbeasts.block.furniture.FurnitureBlock.Spec;
import at.koopro.wizardsandbeasts.block.furniture.MagicalLanternBlock;
import at.koopro.wizardsandbeasts.block.furniture.MovingPropBlock;
import at.koopro.wizardsandbeasts.block.furniture.MovingPropBlockEntity;
import at.koopro.wizardsandbeasts.block.furniture.PropPartBlock;
import at.koopro.wizardsandbeasts.block.furniture.CurtainBlock;
import at.koopro.wizardsandbeasts.block.furniture.DisplayCaseBlock;
import at.koopro.wizardsandbeasts.block.furniture.DisplayCaseBlockEntity;
import at.koopro.wizardsandbeasts.block.furniture.FourPosterBedBlock;
import at.koopro.wizardsandbeasts.block.furniture.FurnitureStorageBlockEntity;
import at.koopro.wizardsandbeasts.block.furniture.StorageFurnitureBlock;
import at.koopro.wizardsandbeasts.block.furniture.TallStorageBlock;
import at.koopro.wizardsandbeasts.block.furniture.TallFurnitureBlock;
import at.koopro.wizardsandbeasts.block.furniture.GrandfatherClockBlock;
import at.koopro.wizardsandbeasts.block.furniture.GlowingDecoBlock;
import net.minecraft.world.item.DyeColor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The world-decoration collections (tasks/todo.md, World Decoration and Architecture Renaissance): architecture,
 * furniture and magical decoration, grouped by the collection they belong to.
 *
 * <p>Models, blockstates, item models and textures are written by {@code tools/furniture_models.py} into
 * {@code src/main/resources} (so datagen leaves them alone, see {@link #ALL}); outlines come from the same boxes
 * through {@link at.koopro.wizardsandbeasts.block.furniture.FurnitureShapes}. Loot, tags and names are datagen's.
 */
public final class FurnitureBlocks {

    private FurnitureBlocks() {}

    /** Every block here; their assets are hand-authored, so the model provider must not expect them. */
    public static final List<DeferredBlock<? extends Block>> ALL = new ArrayList<>();
    /** Every block that drops itself. */
    public static final List<DeferredBlock<? extends Block>> DROPS_SELF = new ArrayList<>();
    /** Every item, in creative-tab order. */
    public static final List<DeferredItem<BlockItem>> ITEMS = new ArrayList<>();
    /** English names, for the language provider. */
    public static final Map<String, String> NAMES = new LinkedHashMap<>();

    static <T extends Block> DeferredBlock<T> block(String id, String name, Function<BlockBehaviour.Properties, T> factory,
                                                             Supplier<BlockBehaviour.Properties> properties) {
        DeferredBlock<T> block = ModBlocks.BLOCKS.registerBlock(id, factory, properties);
        ALL.add(block);
        DROPS_SELF.add(block);
        ITEMS.add(ModItems.ITEMS.registerSimpleBlockItem(id, block));
        NAMES.put(id, name);
        return block;
    }

    private static BlockBehaviour.Properties stone(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(1.5f, 6.0f).requiresCorrectToolForDrops()
                .sound(SoundType.STONE).noOcclusion();
    }

    private static BlockBehaviour.Properties wood(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(2.0f, 3.0f).sound(SoundType.WOOD)
                .ignitedByLava().noOcclusion();
    }

    private static BlockBehaviour.Properties glass() {
        return BlockBehaviour.Properties.of().strength(0.3f).sound(SoundType.GLASS).noOcclusion()
                .isValidSpawn(Blocks::never).isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false);
    }

    private static BlockBehaviour.Properties metal(int light) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).requiresCorrectToolForDrops()
                .sound(SoundType.LANTERN).lightLevel(state -> light).noOcclusion().pushReaction(PushReaction.DESTROY);
    }

    // -- Academic: Hogwarts sandstone, castle oak, iron, crimson and gilt ------------------------------------------

    public static final DeferredBlock<ArchBlock> HOGWARTS_GOTHIC_ARCH = block("hogwarts_gothic_arch",
            "Hogwarts Gothic Arch", p -> new ArchBlock(p, "hogwarts_gothic_arch"), () -> stone(MapColor.COLOR_GRAY));
    public static final DeferredBlock<ColumnBlock> HOGWARTS_STONE_COLUMN = block("hogwarts_stone_column",
            "Hogwarts Stone Column", p -> new ColumnBlock(p, "hogwarts_stone_column"), () -> stone(MapColor.COLOR_GRAY));
    public static final DeferredBlock<FenceBlock> HOGWARTS_STONE_BALUSTRADE = block("hogwarts_stone_balustrade",
            "Hogwarts Stone Balustrade", FenceBlock::new, () -> stone(MapColor.COLOR_GRAY));
    public static final DeferredBlock<IronBarsBlock> LEADED_GLASS_PANE = block("leaded_glass_pane",
            "Leaded Glass Pane", IronBarsBlock::new, FurnitureBlocks::glass);
    public static final DeferredBlock<IronBarsBlock> CRIMSON_LEADED_GLASS_PANE = block("crimson_leaded_glass_pane",
            "Crimson Stained Glass Window", IronBarsBlock::new, FurnitureBlocks::glass);
    public static final DeferredBlock<IronBarsBlock> AZURE_LEADED_GLASS_PANE = block("azure_leaded_glass_pane",
            "Azure Stained Glass Window", IronBarsBlock::new, FurnitureBlocks::glass);
    public static final DeferredBlock<IronBarsBlock> EMERALD_LEADED_GLASS_PANE = block("emerald_leaded_glass_pane",
            "Emerald Stained Glass Window", IronBarsBlock::new, FurnitureBlocks::glass);
    public static final DeferredBlock<IronBarsBlock> AMBER_LEADED_GLASS_PANE = block("amber_leaded_glass_pane",
            "Amber Stained Glass Window", IronBarsBlock::new, FurnitureBlocks::glass);
    public static final DeferredBlock<IronBarsBlock> VIOLET_LEADED_GLASS_PANE = block("violet_leaded_glass_pane",
            "Violet Stained Glass Window", IronBarsBlock::new, FurnitureBlocks::glass);
    public static final DeferredBlock<FurnitureBlock> ACADEMY_NOTICE_BOARD = block("academy_notice_board",
            "Academy Notice Board", p -> new FurnitureBlock(p, Spec.wall("academy_notice_board")),
            () -> wood(MapColor.COLOR_BROWN).pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<FurnitureBlock> MINISTRY_GILT_PLAQUE = block("ministry_gilt_plaque",
            "Ministry Gilt Plaque", p -> new FurnitureBlock(p, Spec.wall("ministry_gilt_plaque")),
            () -> metal(0).pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<FurnitureBlock> SPIRE_FINIAL = block("spire_finial", "Gilt Spire Finial",
            p -> new FurnitureBlock(p, Spec.of("spire_finial")), () -> metal(0));
    public static final DeferredBlock<FurnitureBlock> ACADEMY_DESK = block("academy_desk", "Academy Desk",
            p -> new FurnitureBlock(p, Spec.of("academy_desk")), () -> wood(MapColor.COLOR_BROWN));
    public static final DeferredBlock<FurnitureBlock> ACADEMY_CHAIR = block("academy_chair", "Academy Chair",
            p -> new FurnitureBlock(p, Spec.seat("academy_chair", 0.69f)), () -> wood(MapColor.COLOR_BROWN));
    public static final DeferredBlock<ConnectingFurnitureBlock> REFECTORY_TABLE = block("refectory_table",
            "Refectory Table", p -> new ConnectingFurnitureBlock(p, Spec.of("refectory_table")),
            () -> wood(MapColor.COLOR_BROWN));
    public static final DeferredBlock<ConnectingFurnitureBlock> REFECTORY_BENCH = block("refectory_bench",
            "Refectory Bench", p -> new ConnectingFurnitureBlock(p, Spec.seat("refectory_bench", 0.5f)),
            () -> wood(MapColor.COLOR_BROWN));
    public static final DeferredBlock<CarpetBlock> ACADEMY_CARPET = block("academy_carpet", "Academy Carpet",
            CarpetBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(Blocks.RED_CARPET));
    public static final DeferredBlock<FloatingCandlesBlock> FLOATING_CANDLES = block("floating_candles",
            "Floating Candles", FloatingCandlesBlock::new, () -> BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                    .strength(0.1f).sound(SoundType.CANDLE).lightLevel(state -> 14).noCollision().noOcclusion()
                    .pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<FurnitureBlock> IRON_WALL_SCONCE = block("iron_wall_sconce", "Iron Wall Sconce",
            p -> new FurnitureBlock(p, Spec.wall("iron_wall_sconce")), () -> metal(13));
    public static final DeferredBlock<MovingPropBlock> MAGICAL_WALL_CLOCK = block("magical_wall_clock",
            "Magical Wall Clock", p -> new MovingPropBlock(p, Spec.wall("magical_wall_clock"), MovingPropBlock.Kind.CLOCK),
            () -> wood(MapColor.COLOR_BROWN).pushReaction(PushReaction.DESTROY));

    // -- Library: walnut, brass, green leather ---------------------------------------------------------------------

    public static final DeferredBlock<ConnectingFurnitureBlock> LIBRARY_BOOKSHELF = block("library_bookshelf",
            "Library Bookshelf", p -> new ConnectingFurnitureBlock(p, Spec.of("library_bookshelf")),
            () -> wood(MapColor.COLOR_BROWN));
    public static final DeferredBlock<ConnectingFurnitureBlock> LIBRARY_BOOKSHELF_SPARSE = block(
            "library_bookshelf_sparse", "Half-Empty Library Bookshelf",
            p -> new ConnectingFurnitureBlock(p, Spec.of("library_bookshelf")), () -> wood(MapColor.COLOR_BROWN));
    public static final DeferredBlock<LadderBlock> LIBRARY_LADDER = block("library_ladder", "Library Ladder",
            LadderBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(Blocks.LADDER));
    public static final DeferredBlock<FurnitureBlock> READING_ARMCHAIR = block("reading_armchair", "Reading Armchair",
            p -> new FurnitureBlock(p, Spec.seat("reading_armchair", 0.5f)), () -> wood(MapColor.COLOR_GREEN)
                    .sound(SoundType.WOOL));
    public static final DeferredBlock<CarpetBlock> LIBRARY_CARPET = block("library_carpet", "Library Carpet",
            CarpetBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(Blocks.GREEN_CARPET));
    public static final DeferredBlock<MagicalLanternBlock> MAGICAL_LANTERN = block("magical_lantern",
            "Magical Lantern", MagicalLanternBlock::new, () -> metal(15));
    public static final DeferredBlock<MovingPropBlock> DESK_GLOBE = block("desk_globe", "Desk Globe",
            p -> new MovingPropBlock(p, Spec.of("desk_globe"), MovingPropBlock.Kind.GLOBE),
            () -> wood(MapColor.COLOR_BROWN).strength(1.0f).pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<MovingPropBlock> FLOATING_BOOK = block("floating_book", "Floating Book",
            p -> new MovingPropBlock(p, Spec.of("floating_book"), MovingPropBlock.Kind.BOOK),
            () -> wood(MapColor.COLOR_RED).strength(0.5f).sound(SoundType.WOOL).noCollision().lightLevel(state -> 6)
                    .pushReaction(PushReaction.DESTROY));

    // -- W3: furniture for every collection --------------------------------------------------------------------------

    /** Beds drop from their head half only; two-high storage from its lower half. */
    public static final List<DeferredBlock<? extends Block>> BEDS = new ArrayList<>();
    public static final List<DeferredBlock<? extends Block>> TALL = new ArrayList<>();

    private static DeferredBlock<FourPosterBedBlock> bed(String id, String name, DyeColor colour) {
        DeferredBlock<FourPosterBedBlock> block = ModBlocks.BLOCKS.registerBlock(id, p -> new FourPosterBedBlock(colour, p),
                () -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(0.4f)
                        .noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY));
        ALL.add(block);
        BEDS.add(block);
        ITEMS.add(ModItems.ITEMS.registerItem(id, p -> new net.minecraft.world.item.BedItem(block.get(), p),
                p -> p.stacksTo(1).useBlockDescriptionPrefix()));
        NAMES.put(id, name);
        return block;
    }

    private static <T extends Block> DeferredBlock<T> tall(String id, String name, java.util.function.Function<BlockBehaviour.Properties, T> factory,
                                                            Supplier<BlockBehaviour.Properties> properties) {
        DeferredBlock<T> block = ModBlocks.BLOCKS.registerBlock(id, factory, properties);
        ALL.add(block);
        TALL.add(block);
        ITEMS.add(ModItems.ITEMS.registerItem(id, p -> new net.minecraft.world.item.DoubleHighBlockItem(block.get(), p),
                p -> p.useBlockDescriptionPrefix()));
        NAMES.put(id, name);
        return block;
    }

    // Dormitory
    public static final DeferredBlock<FourPosterBedBlock> FOUR_POSTER_BED_CRIMSON = bed("four_poster_bed_crimson",
            "Crimson Four-Poster Bed", DyeColor.RED);
    public static final DeferredBlock<FourPosterBedBlock> FOUR_POSTER_BED_EMERALD = bed("four_poster_bed_emerald",
            "Emerald Four-Poster Bed", DyeColor.GREEN);
    public static final DeferredBlock<FourPosterBedBlock> FOUR_POSTER_BED_AZURE = bed("four_poster_bed_azure",
            "Azure Four-Poster Bed", DyeColor.BLUE);
    public static final DeferredBlock<FourPosterBedBlock> FOUR_POSTER_BED_AMBER = bed("four_poster_bed_amber",
            "Amber Four-Poster Bed", DyeColor.YELLOW);
    public static final DeferredBlock<FourPosterBedBlock> FOUR_POSTER_BED_IVORY = bed("four_poster_bed_ivory",
            "Ivory Four-Poster Bed", DyeColor.WHITE);
    public static final DeferredBlock<TallStorageBlock> WALNUT_WARDROBE = tall("walnut_wardrobe", "Walnut Wardrobe",
            p -> new TallStorageBlock(p, Spec.of("wardrobe"), 3), () -> wood(MapColor.COLOR_BROWN));
    public static final DeferredBlock<StorageFurnitureBlock> CHEST_OF_DRAWERS = block("chest_of_drawers",
            "Chest of Drawers", p -> new StorageFurnitureBlock(p, Spec.of("chest_of_drawers"), 2),
            () -> wood(MapColor.COLOR_BROWN));
    public static final DeferredBlock<CurtainBlock> CURTAIN_CRIMSON = block("curtain_crimson", "Crimson Curtain",
            p -> new CurtainBlock(p, Spec.of("curtain")), FurnitureBlocks::cloth);
    public static final DeferredBlock<CurtainBlock> CURTAIN_EMERALD = block("curtain_emerald", "Emerald Curtain",
            p -> new CurtainBlock(p, Spec.of("curtain")), FurnitureBlocks::cloth);
    public static final DeferredBlock<CurtainBlock> CURTAIN_AZURE = block("curtain_azure", "Azure Curtain",
            p -> new CurtainBlock(p, Spec.of("curtain")), FurnitureBlocks::cloth);
    public static final DeferredBlock<CarpetBlock> DORMITORY_CARPET = block("dormitory_carpet", "Dormitory Carpet",
            CarpetBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(Blocks.BLUE_CARPET));

    // Library and shops
    public static final DeferredBlock<DisplayCaseBlock> DISPLAY_CASE_BLOCK = block("display_case", "Display Case",
            p -> new DisplayCaseBlock(p, Spec.of("display_case")), () -> wood(MapColor.COLOR_BROWN).sound(SoundType.GLASS));
    public static final DeferredBlock<FurnitureBlock> ROUND_TABLE = block("round_table", "Round Table",
            p -> new FurnitureBlock(p, Spec.of("round_table")), () -> wood(MapColor.COLOR_BROWN));
    public static final DeferredBlock<FurnitureBlock> SIDE_TABLE = block("side_table", "Side Table",
            p -> new FurnitureBlock(p, Spec.of("side_table")), () -> wood(MapColor.COLOR_BROWN));
    public static final DeferredBlock<FurnitureBlock> WINGBACK_ARMCHAIR_CRIMSON = block("wingback_armchair_crimson",
            "Crimson Wingback Armchair", p -> new FurnitureBlock(p, Spec.seat("reading_armchair", 0.5f)),
            () -> wood(MapColor.COLOR_RED).sound(SoundType.WOOL));
    public static final DeferredBlock<FurnitureBlock> WINGBACK_ARMCHAIR_AZURE = block("wingback_armchair_azure",
            "Azure Wingback Armchair", p -> new FurnitureBlock(p, Spec.seat("reading_armchair", 0.5f)),
            () -> wood(MapColor.COLOR_BLUE).sound(SoundType.WOOL));
    public static final DeferredBlock<FurnitureBlock> WINGBACK_ARMCHAIR_PLUM = block("wingback_armchair_plum",
            "Plum Wingback Armchair", p -> new FurnitureBlock(p, Spec.seat("reading_armchair", 0.5f)),
            () -> wood(MapColor.COLOR_PURPLE).sound(SoundType.WOOL));

    // Potion laboratory
    public static final DeferredBlock<FurnitureBlock> POTION_WORKBENCH = block("potion_workbench", "Potion Workbench",
            p -> new FurnitureBlock(p, Spec.of("potion_workbench")), () -> wood(MapColor.COLOR_BROWN));
    public static final DeferredBlock<StorageFurnitureBlock> APOTHECARY_CABINET = block("apothecary_cabinet",
            "Apothecary Cabinet", p -> new StorageFurnitureBlock(p, Spec.of("apothecary_cabinet"), 2),
            () -> wood(MapColor.COLOR_BROWN));
    public static final DeferredBlock<StorageFurnitureBlock> POTION_RACK = block("potion_rack", "Potion Rack",
            p -> new StorageFurnitureBlock(p, Spec.wall("potion_rack"), 1),
            () -> wood(MapColor.COLOR_BROWN).pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<StorageFurnitureBlock> STORAGE_CRATE = block("storage_crate", "Storage Crate",
            p -> new StorageFurnitureBlock(p, Spec.of("storage_crate"), 1), () -> wood(MapColor.WOOD));

    // Ministry
    public static final DeferredBlock<FurnitureBlock> MINISTRY_LEATHER_CHAIR = block("ministry_leather_chair",
            "Ministry Leather Chair", p -> new FurnitureBlock(p, Spec.seat("ministry_leather_chair", 0.56f)),
            () -> wood(MapColor.COLOR_BLACK));
    public static final DeferredBlock<StorageFurnitureBlock> MINISTRY_FILING_CABINET = block("ministry_filing_cabinet",
            "Ministry Filing Cabinet", p -> new StorageFurnitureBlock(p, Spec.of("ministry_filing_cabinet"), 2),
            () -> metal(0).sound(SoundType.METAL));
    public static final DeferredBlock<CarpetBlock> MINISTRY_CARPET = block("ministry_carpet", "Ministry Carpet",
            CarpetBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(Blocks.GREEN_CARPET));
    public static final DeferredBlock<CarpetBlock> GRINGOTTS_CARPET = block("gringotts_carpet", "Gringotts Carpet",
            CarpetBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(Blocks.RED_CARPET));

    // Dark wizards
    public static final DeferredBlock<FurnitureBlock> KNOCKTURN_THRONE = block("knockturn_throne", "Knockturn Throne",
            p -> new FurnitureBlock(p, Spec.seat("knockturn_throne", 0.56f)), () -> wood(MapColor.COLOR_BLACK));
    public static final DeferredBlock<CarpetBlock> KNOCKTURN_CARPET = block("knockturn_carpet", "Knockturn Carpet",
            CarpetBlock::new, () -> BlockBehaviour.Properties.ofFullCopy(Blocks.PURPLE_CARPET));

    private static BlockBehaviour.Properties cloth() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).sound(SoundType.WOOL).strength(0.8f)
                .noCollision().noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY);
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FurnitureStorageBlockEntity>> FURNITURE_STORAGE =
            ModBlockEntities.BLOCK_ENTITY_TYPES.register("furniture_storage", () -> new BlockEntityType<>(
                    FurnitureStorageBlockEntity::new, Set.<Block>of(WALNUT_WARDROBE.get(), CHEST_OF_DRAWERS.get(),
                    APOTHECARY_CABINET.get(), POTION_RACK.get(), STORAGE_CRATE.get(), MINISTRY_FILING_CABINET.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DisplayCaseBlockEntity>> DISPLAY_CASE =
            ModBlockEntities.BLOCK_ENTITY_TYPES.register("display_case", () -> new BlockEntityType<>(
                    DisplayCaseBlockEntity::new, Set.<Block>of(DISPLAY_CASE_BLOCK.get())));

    // -- W4: magical decoration ------------------------------------------------------------------------------------

    private static BlockBehaviour.Properties stoneDeco(int light) {
        return stone(MapColor.COLOR_GRAY).lightLevel(state -> light);
    }

    private static DeferredBlock<GlowingDecoBlock> glowing(String id, String name, Spec spec, GlowingDecoBlock.Emission emission,
                                                           int argb, double x, double y, double z,
                                                           Supplier<BlockBehaviour.Properties> properties) {
        return block(id, name, p -> new GlowingDecoBlock(p, spec, emission, argb, x, y, z), properties);
    }

    public static final DeferredBlock<MagicalLanternBlock> WARM_LANTERN = block("warm_lantern", "Warm Lantern",
            p -> new MagicalLanternBlock(p, 0xFFFFC870), () -> metal(15));
    public static final DeferredBlock<MagicalLanternBlock> GREEN_FLAME_LANTERN = block("green_flame_lantern",
            "Green-Flame Lantern", p -> new MagicalLanternBlock(p, 0xFF9FE07A), () -> metal(13));
    public static final DeferredBlock<GlowingDecoBlock> CANDELABRA = glowing("candelabra", "Candelabra",
            Spec.of("candelabra"), GlowingDecoBlock.Emission.SMOKE, 0, 8, 16, 8, () -> metal(14));
    public static final DeferredBlock<GlowingDecoBlock> RUBY_CRYSTAL_CLUSTER = glowing("ruby_crystal_cluster",
            "Ruby Crystal Cluster", Spec.of("crystal_cluster"), GlowingDecoBlock.Emission.MOTES, 0xFFFF5A6E, 8, 10, 8,
            () -> crystal(MapColor.COLOR_RED));
    public static final DeferredBlock<GlowingDecoBlock> EMERALD_CRYSTAL_CLUSTER = glowing("emerald_crystal_cluster",
            "Emerald Crystal Cluster", Spec.of("crystal_cluster"), GlowingDecoBlock.Emission.MOTES, 0xFF6EE89A, 8, 10, 8,
            () -> crystal(MapColor.EMERALD));
    public static final DeferredBlock<GlowingDecoBlock> SAPPHIRE_CRYSTAL_CLUSTER = glowing("sapphire_crystal_cluster",
            "Sapphire Crystal Cluster", Spec.of("crystal_cluster"), GlowingDecoBlock.Emission.MOTES, 0xFF6E9CFF, 8, 10, 8,
            () -> crystal(MapColor.COLOR_BLUE));
    public static final DeferredBlock<GlowingDecoBlock> MOONSTONE_CRYSTAL_CLUSTER = glowing("moonstone_crystal_cluster",
            "Moonstone Crystal Cluster", Spec.of("crystal_cluster"), GlowingDecoBlock.Emission.MOTES, 0xFFDDEEFF, 8, 10, 8,
            () -> crystal(MapColor.SNOW));
    public static final DeferredBlock<FurnitureBlock> ENCHANTED_MIRROR = block("enchanted_mirror", "Enchanted Mirror",
            p -> new FurnitureBlock(p, Spec.wall("wall_mirror")), () -> wood(MapColor.GOLD).sound(SoundType.GLASS)
                    .lightLevel(state -> 3).pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<FurnitureBlock> DARK_MIRROR = block("dark_mirror", "Dark Mirror",
            p -> new FurnitureBlock(p, Spec.wall("wall_mirror")), () -> wood(MapColor.COLOR_BLACK).sound(SoundType.GLASS)
                    .pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<GrandfatherClockBlock> GRANDFATHER_CLOCK = tall("grandfather_clock",
            "Grandfather Clock", p -> new GrandfatherClockBlock(p, Spec.of("grandfather_clock")),
            () -> wood(MapColor.COLOR_BROWN));
    public static final List<DeferredBlock<FurnitureBlock>> PORTRAITS = new ArrayList<>();
    static {
        for (String[] sitter : new String[][]{{"bearded_headmaster", "Portrait of a Bearded Headmaster"},
                {"stern_witch", "Portrait of a Stern Witch"}, {"goblin_banker", "Portrait of a Goblin Banker"},
                {"armoured_knight", "Portrait of an Armoured Knight"}, {"lady_in_a_ruff", "Portrait of a Lady in a Ruff"},
                {"spectacled_scholar", "Portrait of a Spectacled Scholar"}, {"hooded_dark_wizard", "Portrait of a Hooded Dark Wizard"},
                {"jolly_friar", "Portrait of a Jolly Friar"}}) {
            PORTRAITS.add(block("portrait_" + sitter[0], sitter[1], p -> new FurnitureBlock(p, Spec.wall("portrait")),
                    () -> wood(MapColor.GOLD).pushReaction(PushReaction.DESTROY)));
        }
    }
    public static final DeferredBlock<FurnitureBlock> GOLDEN_TROPHY_CUP = block("golden_trophy_cup", "Golden Trophy Cup",
            p -> new FurnitureBlock(p, Spec.of("trophy_cup")), () -> metal(0).pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<FurnitureBlock> SILVER_TROPHY_CUP = block("silver_trophy_cup", "Silver Trophy Cup",
            p -> new FurnitureBlock(p, Spec.of("trophy_cup")), () -> metal(0).pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<FurnitureBlock> HERALDIC_SHIELD = block("heraldic_shield", "Heraldic Shield",
            p -> new FurnitureBlock(p, Spec.wall("heraldic_shield")), () -> wood(MapColor.COLOR_RED)
                    .pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<FurnitureBlock> OWL_STATUE = block("owl_statue", "Owl Statue",
            p -> new FurnitureBlock(p, Spec.of("owl_statue")), () -> stone(MapColor.COLOR_GRAY));
    public static final DeferredBlock<FurnitureBlock> GARGOYLE_STATUE = block("gargoyle_statue", "Gargoyle Statue",
            p -> new FurnitureBlock(p, Spec.of("gargoyle_statue")), () -> stone(MapColor.COLOR_GRAY));
    public static final DeferredBlock<TallFurnitureBlock> WIZARD_STATUE = tall("wizard_statue", "Wizard Statue",
            p -> new TallFurnitureBlock(p, Spec.of("wizard_statue")), () -> stone(MapColor.COLOR_GRAY));
    public static final DeferredBlock<GlowingDecoBlock> HOGWARTS_FIREPLACE = glowing("hogwarts_fireplace",
            "Hogwarts Fireplace", Spec.of("fireplace"), GlowingDecoBlock.Emission.HEARTH, 0, 8, 3, 10, () -> stoneDeco(13));
    public static final DeferredBlock<GlowingDecoBlock> MINISTRY_FIREPLACE = glowing("ministry_fireplace",
            "Ministry Fireplace", Spec.of("fireplace"), GlowingDecoBlock.Emission.GREEN_HEARTH, 0xFF6EE87A, 8, 3, 10,
            () -> stoneDeco(13));
    public static final DeferredBlock<GlowingDecoBlock> KNOCKTURN_FIREPLACE = glowing("knockturn_fireplace",
            "Knockturn Fireplace", Spec.of("fireplace"), GlowingDecoBlock.Emission.SOUL_HEARTH, 0, 8, 3, 10,
            () -> stoneDeco(10));
    public static final DeferredBlock<Block> GLOWING_RUNESTONE = block("glowing_runestone", "Glowing Runestone",
            Block::new, () -> stoneDeco(7));

    private static BlockBehaviour.Properties crystal(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(1.5f).sound(SoundType.AMETHYST_CLUSTER)
                .lightLevel(state -> 8).noOcclusion().requiresCorrectToolForDrops().pushReaction(PushReaction.DESTROY);
    }

    // -- Internal --------------------------------------------------------------------------------------------------

    /** The moving parts' models; never placed, no item, no loot. */
    public static final DeferredBlock<PropPartBlock> PROP_PART = registerPart();

    private static DeferredBlock<PropPartBlock> registerPart() {
        DeferredBlock<PropPartBlock> block = ModBlocks.BLOCKS.registerBlock("prop_part", PropPartBlock::new,
                () -> BlockBehaviour.Properties.of().noLootTable().noOcclusion().noCollision().strength(-1f, 3600000f));
        ALL.add(block);
        return block;
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MovingPropBlockEntity>> MOVING_PROP =
            ModBlockEntities.BLOCK_ENTITY_TYPES.register("moving_prop", () -> new BlockEntityType<>(
                    MovingPropBlockEntity::new, Set.<Block>of(MAGICAL_WALL_CLOCK.get(), DESK_GLOBE.get(),
                    FLOATING_BOOK.get(), GRANDFATHER_CLOCK.get())));

    public static void init() { /* force class load, triggering static field registration */ }
}
