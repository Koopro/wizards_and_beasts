package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.block.deco.AnimatedDecoBlock;
import at.koopro.wizardsandbeasts.block.deco.AnimatedDecoBlockEntity;
import at.koopro.wizardsandbeasts.block.deco.DecoPileBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Set dressing for the wizarding streets and rooms (tasks/todo.md, "Deco items and blocks"). Models, textures and
 * animations come from {@code tools/deco_models.py}; this only says what each piece is, how it sounds and its outline.
 * Each animated rig has a block-entity type of its own, so its renderer knows its model without asking.
 */
@NullMarked
public final class DecoBlocks {

    private DecoBlocks() {}

    /**
     * Every deco block. Their blockstates, models and item models are hand-written by the deco tools
     * ({@code tools/deco_models*.py}) into {@code src/main/resources}, so datagen must not expect them.
     */
    public static final List<DeferredBlock<? extends Block>> ALL = new ArrayList<>();
    /** Every deco item, in creative-tab order. */
    public static final List<DeferredItem<BlockItem>> ITEMS = new ArrayList<>();
    /** Animated deco: block-entity type per block id, and the rig and sheet its renderer draws. */
    public static final Map<String, DeferredHolder<BlockEntityType<?>, BlockEntityType<AnimatedDecoBlockEntity>>> TYPES =
            new LinkedHashMap<>();
    public static final Map<String, String> RIGS = new LinkedHashMap<>();
    /** Every pile, for its count-keyed loot table. */
    public static final List<DeferredBlock<DecoPileBlock>> PILES = new ArrayList<>();
    /** Every static single piece, for its drop-itself loot table. */
    public static final List<DeferredBlock<at.koopro.wizardsandbeasts.block.deco.DecoBlock>> SINGLES = new ArrayList<>();

    private static BlockBehaviour.Properties props(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(color).sound(sound).strength(0.3f).noOcclusion()
                .pushReaction(PushReaction.DESTROY);
    }

    private static DeferredBlock<DecoPileBlock> pile(String id, MapColor color, SoundType sound, VoxelShape... shapes) {
        DeferredBlock<DecoPileBlock> block = ModBlocks.BLOCKS.registerBlock(id,
                properties -> new DecoPileBlock(properties, shapes), () -> props(color, sound));
        ITEMS.add(ModItems.ITEMS.registerSimpleBlockItem(id, block));
        ALL.add(block);
        PILES.add(block);
        return block;
    }

    private static DeferredBlock<at.koopro.wizardsandbeasts.block.deco.DecoBlock> simple(String id, MapColor color, SoundType sound,
                                                                         VoxelShape shape) {
        DeferredBlock<at.koopro.wizardsandbeasts.block.deco.DecoBlock> block = ModBlocks.BLOCKS.registerBlock(id,
                properties -> new at.koopro.wizardsandbeasts.block.deco.DecoBlock(properties, shape), () -> props(color, sound));
        ITEMS.add(ModItems.ITEMS.registerSimpleBlockItem(id, block));
        ALL.add(block);
        SINGLES.add(block);
        return block;
    }

    private static DeferredBlock<AnimatedDecoBlock> animated(String id, String rig, MapColor color, SoundType sound,
                                                             VoxelShape shape) {
        Supplier<BlockEntityType<AnimatedDecoBlockEntity>> type = () -> TYPES.get(id).get();
        DeferredBlock<AnimatedDecoBlock> block = ModBlocks.BLOCKS.registerBlock(id,
                properties -> new AnimatedDecoBlock(properties, shape, rig, type), () -> props(color, sound));
        TYPES.put(id, ModBlockEntities.BLOCK_ENTITY_TYPES.register(id,
                () -> new BlockEntityType<>(AnimatedDecoBlockEntity::new, Set.<Block>of(block.get()))));
        RIGS.put(id, rig);
        ITEMS.add(ModItems.ITEMS.registerSimpleBlockItem(id, block));
        ALL.add(block);
        return block;
    }

    // -- Gringotts -----------------------------------------------------------------------------------------------------
    public static final DeferredBlock<DecoPileBlock> GALLEON_PILE = coins("galleon_pile", MapColor.GOLD);
    public static final DeferredBlock<DecoPileBlock> SICKLE_PILE = coins("sickle_pile", MapColor.METAL);
    public static final DeferredBlock<DecoPileBlock> KNUT_PILE = coins("knut_pile", MapColor.TERRACOTTA_ORANGE);
    public static final DeferredBlock<DecoPileBlock> MIXED_COIN_PILE = coins("mixed_coin_pile", MapColor.GOLD);
    public static final DeferredBlock<DecoPileBlock> GALLEON_STACKS = stacks("galleon_stacks", MapColor.GOLD);
    public static final DeferredBlock<DecoPileBlock> SICKLE_STACKS = stacks("sickle_stacks", MapColor.METAL);
    public static final DeferredBlock<DecoPileBlock> KNUT_STACKS = stacks("knut_stacks", MapColor.TERRACOTTA_ORANGE);
    public static final DeferredBlock<DecoPileBlock> COIN_POUCH = pile("coin_pouch", MapColor.COLOR_BROWN, SoundType.WOOL,
            Block.box(5, 0, 6, 10, 7, 10), Block.box(5, 0, 5, 12, 7, 12), Block.box(5, 0, 5, 14, 7, 13),
            Block.box(2, 0, 3, 14, 7, 13));

    private static DeferredBlock<DecoPileBlock> stacks(String id, MapColor color) {
        return pile(id, color, SoundType.CHAIN, Block.box(4, 0, 5, 7, 5, 8), Block.box(4, 0, 4, 11, 5, 8),
                Block.box(4, 0, 4, 12, 7, 12), Block.box(4, 0, 4, 12, 7, 14));
    }

    private static DeferredBlock<DecoPileBlock> coins(String id, MapColor color) {
        return pile(id, color, SoundType.CHAIN, Block.box(3, 0, 4, 13, 2, 12), Block.box(3, 0, 4, 13, 4, 12),
                Block.box(3, 0, 4, 13, 4, 14), Block.box(3, 0, 4, 14, 6, 14));
    }

    public static final DeferredBlock<AnimatedDecoBlock> GRINGOTTS_SCALES = animated("gringotts_scales",
            "gringotts_scales", MapColor.GOLD, SoundType.CHAIN, Block.box(1, 0, 5, 15, 13, 11));
    public static final DeferredBlock<AnimatedDecoBlock> GRINGOTTS_SCALES_SILVER = animated("gringotts_scales_silver",
            "gringotts_scales", MapColor.METAL, SoundType.CHAIN, Block.box(1, 0, 5, 15, 13, 11));
    public static final DeferredBlock<AnimatedDecoBlock> ENCHANTED_QUILL = animated("enchanted_quill",
            "enchanted_quill", MapColor.WOOL, SoundType.WOOL, Block.box(2, 0, 3, 14, 4, 13));
    public static final DeferredBlock<AnimatedDecoBlock> ENCHANTED_QUILL_RAVEN = animated("enchanted_quill_raven",
            "enchanted_quill", MapColor.WOOL, SoundType.WOOL, Block.box(2, 0, 3, 14, 4, 13));
    public static final DeferredBlock<AnimatedDecoBlock> ENCHANTED_QUILL_PEACOCK = animated("enchanted_quill_peacock",
            "enchanted_quill", MapColor.WOOL, SoundType.WOOL, Block.box(2, 0, 3, 14, 4, 13));

    // -- Shops -----------------------------------------------------------------------------------------------------------
    public static final DeferredBlock<DecoPileBlock> POTION_BOTTLES = bottles("potion_bottles");
    public static final DeferredBlock<DecoPileBlock> POTION_BOTTLES_DARK = bottles("potion_bottles_dark");
    public static final DeferredBlock<DecoPileBlock> POTION_BOTTLES_BRIGHT = bottles("potion_bottles_bright");
    public static final DeferredBlock<DecoPileBlock> BOOK_STACK = books("book_stack", MapColor.COLOR_RED);
    public static final DeferredBlock<DecoPileBlock> BOOK_STACK_LEATHER = books("book_stack_leather", MapColor.COLOR_BROWN);
    public static final DeferredBlock<DecoPileBlock> BOOK_STACK_BRIGHT = books("book_stack_bright", MapColor.COLOR_YELLOW);

    private static DeferredBlock<DecoPileBlock> bottles(String id) {
        return pile(id, MapColor.COLOR_PURPLE, SoundType.GLASS, Block.box(5, 0, 5, 11, 7, 11),
                Block.box(2, 0, 5, 11, 7, 11), Block.box(2, 0, 2, 13, 8, 11), Block.box(2, 0, 2, 14, 8, 14));
    }

    private static DeferredBlock<DecoPileBlock> books(String id, MapColor color) {
        return pile(id, color, SoundType.WOOL, Block.box(3, 0, 2, 13, 3, 14), Block.box(3, 0, 2, 13, 5, 14),
                Block.box(3, 0, 2, 13, 8, 14), Block.box(3, 0, 2, 13, 10, 14));
    }
    public static final DeferredBlock<AnimatedDecoBlock> OWL_CAGE_SNOWY = animated("owl_cage_snowy", "owl_cage",
            MapColor.GOLD, SoundType.CHAIN, Block.box(3, 0, 3, 13, 15, 13));
    public static final DeferredBlock<AnimatedDecoBlock> OWL_CAGE_BARN = animated("owl_cage_barn", "owl_cage",
            MapColor.GOLD, SoundType.CHAIN, Block.box(3, 0, 3, 13, 15, 13));
    public static final DeferredBlock<AnimatedDecoBlock> OWL_CAGE_TAWNY = animated("owl_cage_tawny", "owl_cage",
            MapColor.GOLD, SoundType.CHAIN, Block.box(3, 0, 3, 13, 15, 13));

    // -- Batch 2: Ollivanders, the apothecary, studies, Knockturn ----------------------------------------------------
    public static final DeferredBlock<DecoPileBlock> WAND_BOXES = wandBoxes("wand_boxes", MapColor.COLOR_GREEN);
    public static final DeferredBlock<DecoPileBlock> WAND_BOXES_PALE = wandBoxes("wand_boxes_pale", MapColor.SAND);
    public static final DeferredBlock<DecoPileBlock> SPECIMEN_JARS = jars("specimen_jars", MapColor.COLOR_LIGHT_GREEN);
    public static final DeferredBlock<DecoPileBlock> SPECIMEN_JARS_MURKY = jars("specimen_jars_murky", MapColor.COLOR_GREEN);
    public static final DeferredBlock<DecoPileBlock> SCROLL_PILE = scrolls("scroll_pile");
    public static final DeferredBlock<DecoPileBlock> SCROLL_PILE_SEALED = scrolls("scroll_pile_sealed");

    private static DeferredBlock<DecoPileBlock> wandBoxes(String id, MapColor color) {
        return pile(id, color, SoundType.WOOD, Block.box(3, 0, 3, 6, 2, 13), Block.box(3, 0, 2, 10, 2, 13),
                Block.box(3, 0, 2, 10, 4, 14), Block.box(3, 0, 2, 14, 4, 14));
    }

    private static DeferredBlock<DecoPileBlock> jars(String id, MapColor color) {
        return pile(id, color, SoundType.GLASS, Block.box(6, 0, 6, 9, 5, 9), Block.box(2, 0, 6, 9, 6, 11),
                Block.box(2, 0, 3, 13, 6, 11), Block.box(2, 0, 3, 14, 6, 14));
    }

    private static DeferredBlock<DecoPileBlock> scrolls(String id) {
        return pile(id, MapColor.SAND, SoundType.WOOL, Block.box(3, 0, 4, 11, 2, 6), Block.box(3, 0, 4, 13, 2, 10),
                Block.box(3, 0, 4, 13, 4, 10), Block.box(2, 0, 4, 13, 4, 13));
    }

    public static final DeferredBlock<AnimatedDecoBlock> BUBBLING_POT_PEWTER = pot("bubbling_pot_pewter");
    public static final DeferredBlock<AnimatedDecoBlock> BUBBLING_POT_BRASS = pot("bubbling_pot_brass");
    public static final DeferredBlock<AnimatedDecoBlock> BUBBLING_POT_COPPER = pot("bubbling_pot_copper");
    public static final DeferredBlock<AnimatedDecoBlock> CRYSTAL_BALL = animated("crystal_ball", "crystal_ball",
            MapColor.COLOR_LIGHT_BLUE, SoundType.GLASS, Block.box(5, 0, 5, 11, 10, 11));
    public static final DeferredBlock<AnimatedDecoBlock> CRYSTAL_BALL_SMOKY = animated("crystal_ball_smoky",
            "crystal_ball", MapColor.COLOR_PURPLE, SoundType.GLASS, Block.box(5, 0, 5, 11, 10, 11));
    public static final DeferredBlock<AnimatedDecoBlock> HOURGLASS = animated("hourglass", "hourglass", MapColor.GOLD,
            SoundType.GLASS, Block.box(4, 0, 4, 12, 12, 12));
    public static final DeferredBlock<AnimatedDecoBlock> HOURGLASS_SILVER = animated("hourglass_silver", "hourglass",
            MapColor.METAL, SoundType.GLASS, Block.box(4, 0, 4, 12, 12, 12));
    public static final DeferredBlock<AnimatedDecoBlock> ORRERY = animated("orrery", "orrery", MapColor.GOLD,
            SoundType.CHAIN, Block.box(4, 0, 4, 12, 12, 12));
    public static final DeferredBlock<AnimatedDecoBlock> ORRERY_SILVER = animated("orrery_silver", "orrery",
            MapColor.METAL, SoundType.CHAIN, Block.box(4, 0, 4, 12, 12, 12));
    public static final DeferredBlock<AnimatedDecoBlock> SHRUNKEN_HEADS = animated("shrunken_heads", "shrunken_heads",
            MapColor.COLOR_BROWN, SoundType.WOOL, Block.box(5, 1, 6, 11, 16, 10));
    public static final DeferredBlock<AnimatedDecoBlock> HANGING_SHRUNKEN_HEAD = animated("hanging_shrunken_head", "shrunken_head",
            MapColor.COLOR_BROWN, SoundType.WOOL, Block.box(5, 4, 5, 11, 16, 11));

    private static DeferredBlock<AnimatedDecoBlock> pot(String id) {
        return animated(id, "bubbling_pot", MapColor.METAL, SoundType.METAL, Block.box(2, 0, 3, 14, 9, 13));
    }

    // -- Batch 3: shops, the pub, Knockturn ----------------------------------------------------------------------
    public static final DeferredBlock<at.koopro.wizardsandbeasts.block.deco.DecoBlock> DRESS_FORM_BLACK = simple("dress_form_black",
            MapColor.COLOR_BLACK, SoundType.WOOL, Block.box(4, 0, 5, 12, 16, 11));
    public static final DeferredBlock<at.koopro.wizardsandbeasts.block.deco.DecoBlock> DRESS_FORM_GREEN = simple("dress_form_green",
            MapColor.COLOR_GREEN, SoundType.WOOL, Block.box(4, 0, 5, 12, 16, 11));
    public static final DeferredBlock<at.koopro.wizardsandbeasts.block.deco.DecoBlock> DRESS_FORM_PLUM = simple("dress_form_plum",
            MapColor.COLOR_PURPLE, SoundType.WOOL, Block.box(4, 0, 5, 12, 16, 11));
    public static final DeferredBlock<at.koopro.wizardsandbeasts.block.deco.DecoBlock> QUAFFLE_CRATE = simple("quaffle_crate",
            MapColor.WOOD, SoundType.WOOD, Block.box(2, 0, 2, 14, 8, 14));
    public static final DeferredBlock<at.koopro.wizardsandbeasts.block.deco.DecoBlock> BLUDGER_CRATE = simple("bludger_crate",
            MapColor.WOOD, SoundType.WOOD, Block.box(2, 0, 2, 14, 8, 14));
    public static final DeferredBlock<at.koopro.wizardsandbeasts.block.deco.DecoBlock> WIZARD_CHESS = simple("wizard_chess",
            MapColor.WOOD, SoundType.STONE, Block.box(3, 0, 3, 13, 5, 13));
    public static final DeferredBlock<at.koopro.wizardsandbeasts.block.deco.DecoBlock> HERB_BUNDLE_LAVENDER = simple("herb_bundle_lavender",
            MapColor.COLOR_PURPLE, SoundType.GRASS, Block.box(5, 3, 5, 11, 16, 11));
    public static final DeferredBlock<at.koopro.wizardsandbeasts.block.deco.DecoBlock> HERB_BUNDLE_WOLFSBANE = simple("herb_bundle_wolfsbane",
            MapColor.COLOR_BLUE, SoundType.GRASS, Block.box(5, 3, 5, 11, 16, 11));
    public static final DeferredBlock<at.koopro.wizardsandbeasts.block.deco.DecoBlock> HERB_BUNDLE_GARLIC = simple("herb_bundle_garlic",
            MapColor.SAND, SoundType.GRASS, Block.box(5, 3, 5, 11, 16, 11));
    public static final DeferredBlock<at.koopro.wizardsandbeasts.block.deco.DecoBlock> DESK_SET = simple("desk_set",
            MapColor.COLOR_GREEN, SoundType.WOOD, Block.box(2, 0, 4, 14, 3, 12));
    public static final DeferredBlock<DecoPileBlock> BROOM_BARREL = pile("broom_barrel", MapColor.WOOD, SoundType.WOOD,
            Block.box(4, 0, 4, 12, 16, 12), Block.box(4, 0, 4, 12, 16, 12), Block.box(4, 0, 4, 12, 16, 12),
            Block.box(4, 0, 4, 12, 16, 12));
    public static final DeferredBlock<DecoPileBlock> BUTTERBEER_TANKARDS = pile("butterbeer_tankards", MapColor.METAL,
            SoundType.METAL, Block.box(5, 0, 5, 10, 5, 9), Block.box(5, 0, 5, 14, 5, 12), Block.box(2, 0, 5, 14, 5, 14),
            Block.box(2, 0, 3, 14, 5, 14));
    public static final DeferredBlock<DecoPileBlock> FIREWHISKY_BOTTLES = pile("firewhisky_bottles",
            MapColor.COLOR_ORANGE, SoundType.GLASS, Block.box(6, 0, 6, 9, 9, 9), Block.box(6, 0, 6, 13, 9, 12),
            Block.box(3, 0, 6, 13, 9, 12), Block.box(3, 0, 3, 13, 9, 12));
    public static final DeferredBlock<DecoPileBlock> LUGGAGE = pile("luggage", MapColor.COLOR_BROWN, SoundType.WOOD,
            Block.box(2, 0, 4, 13, 5, 12), Block.box(2, 0, 4, 13, 9, 12), Block.box(2, 0, 4, 13, 12, 12),
            Block.box(2, 0, 4, 16, 12, 16));
    public static final DeferredBlock<AnimatedDecoBlock> DAILY_PROPHET_STACK = animated("daily_prophet_stack",
            "newspaper", MapColor.WOOL, SoundType.WOOL, Block.box(3, 0, 3, 13, 4, 13));
    public static final DeferredBlock<AnimatedDecoBlock> QUIBBLER_STACK = animated("quibbler_stack", "newspaper",
            MapColor.COLOR_YELLOW, SoundType.WOOL, Block.box(3, 0, 3, 13, 4, 13));
    public static final DeferredBlock<AnimatedDecoBlock> SPIDER_CAGE = animated("spider_cage", "spider_cage",
            MapColor.METAL, SoundType.CHAIN, Block.box(3, 0, 3, 13, 12, 13));

    /** Loads the class, so every holder above is queued before the registries fire. */
    public static void init() {}

    public static List<DeferredItem<BlockItem>> items() {
        return Collections.unmodifiableList(ITEMS);
    }
}
