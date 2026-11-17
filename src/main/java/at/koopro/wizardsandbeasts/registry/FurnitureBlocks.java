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

    private static <T extends Block> DeferredBlock<T> block(String id, String name, Function<BlockBehaviour.Properties, T> factory,
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
                    FLOATING_BOOK.get())));

    public static void init() { /* force class load, triggering static field registration */ }
}
