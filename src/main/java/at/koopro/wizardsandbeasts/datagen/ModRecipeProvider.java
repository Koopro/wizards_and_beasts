package at.koopro.wizardsandbeasts.datagen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.recipes.SingleItemRecipeBuilder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.location.DiagonAlleyBlocks;
import at.koopro.wizardsandbeasts.block.location.GringottsBlocks;
import at.koopro.wizardsandbeasts.block.location.HogsmeadeBlocks;
import at.koopro.wizardsandbeasts.block.location.HogwartsBlocks;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.SlabSet;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.StairSet;
import at.koopro.wizardsandbeasts.block.location.LocationBlockHelper.VariantSet;
import at.koopro.wizardsandbeasts.block.location.MinistryBlocks;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.condition.ModuleEnabledCondition;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.WoodSet;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;

public class ModRecipeProvider extends RecipeProvider {

    /**
     * Conditional output for location-block recipes — gated behind {@link Module#STRUCTURES}.
     * Set before {@link #generateLocationBlockRecipes()} runs; the location-only helpers below
     * save through this so the whole decorative set toggles with the module.
     */
    private RecipeOutput structureSink;

    /**
     * One conditional {@link RecipeOutput} per module, built on demand. A recipe saved through
     * {@code sink(module)} carries a {@link ModuleEnabledCondition}, so switching the module off removes
     * the recipe from the book instead of leaving a craft that produces unreachable content.
     */
    private final Map<Module, RecipeOutput> sinks = new EnumMap<>(Module.class);

    public ModRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    private RecipeOutput sink(Module module) {
        return sinks.computeIfAbsent(module, m -> output.withConditions(new ModuleEnabledCondition(m)));
    }

    @Override
    protected void buildRecipes() {
        for (WoodSet woodSet : ModBlocks.ALL_WOOD_SETS) {
            generateWoodRecipes(woodSet);
        }
        generateConsumableRecipes();
        structureSink = output.withConditions(new ModuleEnabledCondition(Module.STRUCTURES));
        generateLocationBlockRecipes();
    }

    private void generateWoodRecipes(WoodSet woodSet) {
        Block log = woodSet.log().get();
        Block strippedLog = woodSet.strippedLog().get();
        Block woodBlock = woodSet.wood().get();
        Block strippedWood = woodSet.strippedWood().get();
        Block planks = woodSet.planks().get();
        Block slab = woodSet.slab().get();
        Block stairs = woodSet.stairs().get();
        RecipeOutput sink = sink(Module.WANDWOOD);

        planksFromLog(planks, log);
        planksFromLog(planks, strippedLog);
        planksFromLog(planks, woodBlock);
        planksFromLog(planks, strippedWood);

        // Inlined rather than calling the inherited woodFromLogs/slab helpers: those save straight to
        // this.output, which is the ungated sink. Bodies match vanilla exactly so the recipes are unchanged
        // apart from carrying the condition.
        woodFromLogs(woodBlock, log, sink);
        woodFromLogs(strippedWood, strippedLog, sink);

        slabBuilder(RecipeCategory.BUILDING_BLOCKS, slab, Ingredient.of(planks))
                .unlockedBy(getHasName(planks), has(planks))
                .save(sink);
        stairBuilder(stairs, Ingredient.of(planks))
                .unlockedBy("has_planks", has(planks))
                .save(sink);
    }

    private void woodFromLogs(Block result, Block log, RecipeOutput sink) {
        shaped(RecipeCategory.BUILDING_BLOCKS, result, 3)
                .define('#', log)
                .pattern("##")
                .pattern("##")
                .group("bark")
                .unlockedBy("has_log", has(log))
                .save(sink);
    }

    private void planksFromLog(Block planks, Block log) {
        ShapelessRecipeBuilder.shapeless(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.BUILDING_BLOCKS, planks, 4)
                .requires(log)
                .group("planks")
                .unlockedBy("has_log", has(log))
                .save(sink(Module.WANDWOOD), getConversionRecipeName(planks, log));
    }

    private void generateConsumableRecipes() {
        ShapelessRecipeBuilder.shapeless(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, CurrencyItemRegistry.SICKLE.get(), CurrencyHelper.SICKLES_PER_GALLEON)
                .requires(CurrencyItemRegistry.GALLEON.get())
                .unlockedBy("has_galleon", has(CurrencyItemRegistry.GALLEON.get()))
                .save(sink(Module.GRINGOTTS), "wizards_and_beasts:currency/galleon_to_sickle");

        ShapelessRecipeBuilder.shapeless(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, CurrencyItemRegistry.KNUT.get(), CurrencyHelper.KNUTS_PER_SICKLE)
                .requires(CurrencyItemRegistry.SICKLE.get())
                .unlockedBy("has_sickle", has(CurrencyItemRegistry.SICKLE.get()))
                .save(sink(Module.GRINGOTTS), "wizards_and_beasts:currency/sickle_to_knut");

        ShapelessRecipeBuilder.shapeless(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.FOOD, ConsumableItemRegistry.TREACLE_TART.get())
                .requires(Items.WHEAT)
                .requires(Items.SUGAR)
                .requires(Items.EGG)
                .unlockedBy("has_sugar", has(Items.SUGAR))
                .save(sink(Module.WIZARDING_FOOD));

        ShapelessRecipeBuilder.shapeless(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.FOOD, ConsumableItemRegistry.PUMPKIN_PASTY.get(), 2)
                .requires(Items.PUMPKIN)
                .requires(Items.WHEAT)
                .requires(Items.SUGAR)
                .unlockedBy("has_pumpkin", has(Items.PUMPKIN))
                .save(sink(Module.WIZARDING_FOOD));

        ShapelessRecipeBuilder.shapeless(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.FOOD, ConsumableItemRegistry.FIZZING_WHIZZBEE.get(), 2)
                .requires(Items.HONEYCOMB)
                .requires(Items.SUGAR)
                .unlockedBy("has_honeycomb", has(Items.HONEYCOMB))
                .save(sink(Module.WIZARDING_FOOD));

        ShapelessRecipeBuilder.shapeless(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.FOOD, ConsumableItemRegistry.PEPPERMINT_TOAD.get(), 2)
                .requires(Items.COCOA_BEANS)
                .requires(Items.SUGAR)
                .unlockedBy("has_cocoa_beans", has(Items.COCOA_BEANS))
                .save(sink(Module.WIZARDING_FOOD));

        ShapelessRecipeBuilder.shapeless(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, MiscItemRegistry.FLOO_POWDER.get(), 8)
                .requires(Items.BLAZE_POWDER)
                .requires(Items.GRAY_DYE)
                .requires(Items.GLOWSTONE_DUST)
                .unlockedBy("has_blaze_powder", has(Items.BLAZE_POWDER))
                .save(sink(Module.FLOO_NETWORK));

        ShapedRecipeBuilder.shaped(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.TOOLS, MiscItemRegistry.DELUMINATOR.get())
                .pattern(" IT")
                .pattern("IRI")
                .pattern(" II")
                .define('I', Items.IRON_INGOT)
                .define('T', Items.TORCH)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(sink(Module.ARTEFACTS));

        ShapedRecipeBuilder.shaped(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.TOOLS, TrinketItemRegistry.REMEMBRALL.get())
                .pattern(" G ")
                .pattern("GRG")
                .pattern(" G ")
                .define('G', Items.GLASS)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(sink(Module.ARTEFACTS));

        ShapedRecipeBuilder.shaped(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.TOOLS, TrinketItemRegistry.OMNI_OCULARS.get())
                .pattern("ALA")
                .pattern("ASA")
                .pattern("AAA")
                .define('A', Items.AMETHYST_SHARD)
                .define('L', Items.LEATHER)
                .define('S', Items.SPYGLASS)
                .unlockedBy("has_spyglass", has(Items.SPYGLASS))
                .save(sink(Module.ARTEFACTS));

        ShapedRecipeBuilder.shaped(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, ConsumableItemRegistry.FAMOUS_WIZARD_CARD.get(), 2)
                .pattern("PI")
                .pattern("PG")
                .define('P', Items.PAPER)
                .define('I', MiscItemRegistry.INK_BOTTLE.get())
                .define('G', CurrencyItemRegistry.KNUT.get())
                .unlockedBy("has_paper", has(Items.PAPER))
                .save(sink(Module.WIZARDING_FOOD));
    }

    // ─── Location decorative blocks ─────────────────────────────────────────────
    // Base recipes follow the ingredient hints authored on each block field; every slab/stairs/wall
    // variant also gets crafting recipes plus stonecutter shortcuts from its own base.

    private void generateLocationBlockRecipes() {
        // ── Ministry of Magic ──
        tinted(MinistryBlocks.MINISTRY_BLACK_MARBLE.base().get(), 8, Items.BLACK_DYE, Items.BLACKSTONE);
        craftVariants(MinistryBlocks.MINISTRY_BLACK_MARBLE);
        boxFrom(MinistryBlocks.MINISTRY_BLACK_MARBLE_TILES.base().get(), MinistryBlocks.MINISTRY_BLACK_MARBLE.base().get());
        craftStairSet(MinistryBlocks.MINISTRY_BLACK_MARBLE_TILES);
        stonecut(MinistryBlocks.MINISTRY_BLACK_MARBLE_TILES.base().get(), 1, MinistryBlocks.MINISTRY_BLACK_MARBLE.base().get());
        pillarFrom(MinistryBlocks.MINISTRY_BLACK_MARBLE_PILLAR.block().get(), MinistryBlocks.MINISTRY_BLACK_MARBLE.base().get());
        stonecut(MinistryBlocks.MINISTRY_BLACK_MARBLE_PILLAR.block().get(), 1, MinistryBlocks.MINISTRY_BLACK_MARBLE.base().get());

        tinted(MinistryBlocks.MINISTRY_GILDED_BLACK_MARBLE.base().get(), 8, Items.GOLD_INGOT, Items.BLACKSTONE);
        craftVariants(MinistryBlocks.MINISTRY_GILDED_BLACK_MARBLE);
        combine(MinistryBlocks.MINISTRY_GILDED_TRIM.block().get(), 1, Items.GOLD_INGOT, MinistryBlocks.MINISTRY_BLACK_MARBLE.base().get());

        boxFrom(MinistryBlocks.MINISTRY_DARK_TILE.base().get(), Items.DEEPSLATE_TILES);
        craftStairSet(MinistryBlocks.MINISTRY_DARK_TILE);
        alt2x2(MinistryBlocks.MINISTRY_FLOOR_TILE.base().get(), 4, Items.QUARTZ_BLOCK, Items.DIORITE);
        craftSlabSet(MinistryBlocks.MINISTRY_FLOOR_TILE);
        boxFrom(MinistryBlocks.MINISTRY_WALL_PANEL.block().get(), Items.POLISHED_DEEPSLATE);

        // ── Hogwarts Castle ──
        tinted(HogwartsBlocks.HOGWARTS_STONE.base().get(), 8, Items.COBBLESTONE, Items.STONE);
        craftVariants(HogwartsBlocks.HOGWARTS_STONE);
        boxFrom(HogwartsBlocks.HOGWARTS_STONE_BRICKS.base().get(), HogwartsBlocks.HOGWARTS_STONE.base().get());
        craftVariants(HogwartsBlocks.HOGWARTS_STONE_BRICKS);
        stonecut(HogwartsBlocks.HOGWARTS_STONE_BRICKS.base().get(), 1, HogwartsBlocks.HOGWARTS_STONE.base().get());
        smelt(HogwartsBlocks.HOGWARTS_CRACKED_STONE_BRICKS.block().get(), HogwartsBlocks.HOGWARTS_STONE_BRICKS.base().get());
        mossy(HogwartsBlocks.HOGWARTS_MOSSY_STONE_BRICKS.base().get(), HogwartsBlocks.HOGWARTS_STONE_BRICKS.base().get(), Items.VINE);
        craftVariants(HogwartsBlocks.HOGWARTS_MOSSY_STONE_BRICKS);
        pillarFrom(HogwartsBlocks.HOGWARTS_STONE_PILLAR.block().get(), HogwartsBlocks.HOGWARTS_STONE.base().get());
        stonecut(HogwartsBlocks.HOGWARTS_STONE_PILLAR.block().get(), 1, HogwartsBlocks.HOGWARTS_STONE.base().get());
        alt2x2(HogwartsBlocks.HOGWARTS_DARK_STONE.base().get(), 4, Items.DEEPSLATE, HogwartsBlocks.HOGWARTS_STONE.base().get());
        craftVariants(HogwartsBlocks.HOGWARTS_DARK_STONE);
        boxFrom(HogwartsBlocks.HOGWARTS_FLAGSTONE.base().get(), HogwartsBlocks.HOGWARTS_STONE.base().get());
        craftSlabSet(HogwartsBlocks.HOGWARTS_FLAGSTONE);
        alt2x2(HogwartsBlocks.HOGWARTS_FLOOR_TILE.base().get(), 4, Items.QUARTZ_BLOCK, HogwartsBlocks.HOGWARTS_STONE.base().get());
        craftSlabSet(HogwartsBlocks.HOGWARTS_FLOOR_TILE);
        alt2x2(HogwartsBlocks.ENCHANTED_CEILING_TILE.block().get(), 4, Items.LAPIS_LAZULI, Items.QUARTZ);

        // ── Diagon Alley ──
        alt2x2(DiagonAlleyBlocks.DIAGON_BRICK.base().get(), 4, Items.BRICKS, Items.TERRACOTTA);
        craftVariants(DiagonAlleyBlocks.DIAGON_BRICK);
        boxFrom(DiagonAlleyBlocks.DIAGON_BRICK_TILES.base().get(), DiagonAlleyBlocks.DIAGON_BRICK.base().get());
        craftStairSet(DiagonAlleyBlocks.DIAGON_BRICK_TILES);
        stonecut(DiagonAlleyBlocks.DIAGON_BRICK_TILES.base().get(), 1, DiagonAlleyBlocks.DIAGON_BRICK.base().get());
        smelt(DiagonAlleyBlocks.DIAGON_WORN_BRICK.base().get(), DiagonAlleyBlocks.DIAGON_BRICK.base().get());
        craftVariants(DiagonAlleyBlocks.DIAGON_WORN_BRICK);
        tinted(DiagonAlleyBlocks.DIAGON_COBBLESTONE.base().get(), 8, Items.FLINT, Items.COBBLESTONE);
        craftVariants(DiagonAlleyBlocks.DIAGON_COBBLESTONE);
        combine(DiagonAlleyBlocks.DIAGON_SHOPFRONT_WOOD.base().get(), 1, Items.DARK_OAK_LOG, Items.COPPER_INGOT);
        craftStairSet(DiagonAlleyBlocks.DIAGON_SHOPFRONT_WOOD);
        combine(DiagonAlleyBlocks.DIAGON_SHOPFRONT_PLANKS.base().get(), 1, Items.DARK_OAK_PLANKS, Items.COPPER_INGOT);
        craftStairSet(DiagonAlleyBlocks.DIAGON_SHOPFRONT_PLANKS);
        tinted(DiagonAlleyBlocks.DIAGON_PAINTED_WOOD_GREEN.base().get(), 8, Items.GREEN_DYE, Items.OAK_PLANKS);
        craftStairSet(DiagonAlleyBlocks.DIAGON_PAINTED_WOOD_GREEN);
        tinted(DiagonAlleyBlocks.DIAGON_PAINTED_WOOD_PURPLE.base().get(), 8, Items.PURPLE_DYE, Items.OAK_PLANKS);
        craftStairSet(DiagonAlleyBlocks.DIAGON_PAINTED_WOOD_PURPLE);
        alt2x2(DiagonAlleyBlocks.DIAGON_STREET_STONE.get(), 4, Items.STONE_BRICKS, Items.GRAVEL);
        slabFrom(DiagonAlleyBlocks.DIAGON_STREET_STONE_SLAB.get(), DiagonAlleyBlocks.DIAGON_STREET_STONE.get());
        stonecut(DiagonAlleyBlocks.DIAGON_STREET_STONE_SLAB.get(), 2, DiagonAlleyBlocks.DIAGON_STREET_STONE.get());
        pressurePlateFrom(DiagonAlleyBlocks.DIAGON_STREET_STONE_PRESSURE_PLATE.get(), DiagonAlleyBlocks.DIAGON_STREET_STONE.get());

        // ── Hogsmeade ──
        alt2x2(HogsmeadeBlocks.HOGSMEADE_STONE.base().get(), 4, Items.STONE, Items.ANDESITE);
        craftVariants(HogsmeadeBlocks.HOGSMEADE_STONE);
        boxFrom(HogsmeadeBlocks.HOGSMEADE_STONE_BRICKS.base().get(), HogsmeadeBlocks.HOGSMEADE_STONE.base().get());
        craftVariants(HogsmeadeBlocks.HOGSMEADE_STONE_BRICKS);
        stonecut(HogsmeadeBlocks.HOGSMEADE_STONE_BRICKS.base().get(), 1, HogsmeadeBlocks.HOGSMEADE_STONE.base().get());
        smelt(HogsmeadeBlocks.HOGSMEADE_WORN_STONE.base().get(), HogsmeadeBlocks.HOGSMEADE_STONE.base().get());
        craftVariants(HogsmeadeBlocks.HOGSMEADE_WORN_STONE);
        combine(HogsmeadeBlocks.THREE_BROOMSTICKS_TIMBER.base().get(), 1, Items.DARK_OAK_LOG, Items.IRON_NUGGET);
        craftStairSet(HogsmeadeBlocks.THREE_BROOMSTICKS_TIMBER);
        boxFrom(HogsmeadeBlocks.THREE_BROOMSTICKS_PLANKS.base().get(), Items.DARK_OAK_PLANKS);
        craftStairSet(HogsmeadeBlocks.THREE_BROOMSTICKS_PLANKS);
        tinted(HogsmeadeBlocks.HONEYDUKES_PASTEL_PINK.base().get(), 8, Items.PINK_DYE, Items.QUARTZ_BLOCK);
        craftStairSet(HogsmeadeBlocks.HONEYDUKES_PASTEL_PINK);
        tinted(HogsmeadeBlocks.HONEYDUKES_PASTEL_YELLOW.base().get(), 8, Items.YELLOW_DYE, Items.QUARTZ_BLOCK);
        craftStairSet(HogsmeadeBlocks.HONEYDUKES_PASTEL_YELLOW);
        combine(HogsmeadeBlocks.HOGSMEADE_ROOF_TILE.base().get(), 2, Items.STONE, Items.COBBLESTONE_SLAB);
        craftStairSet(HogsmeadeBlocks.HOGSMEADE_ROOF_TILE);
        alt2x2(HogsmeadeBlocks.HOGSMEADE_CHIMNEY_BRICK.base().get(), 4, Items.BRICKS, Items.RED_TERRACOTTA);
        craftVariants(HogsmeadeBlocks.HOGSMEADE_CHIMNEY_BRICK);

        // ── Gringotts Bank ──
        alt2x2(GringottsBlocks.GRINGOTTS_WHITE_MARBLE.base().get(), 4, Items.QUARTZ_BLOCK, Items.CALCITE);
        craftVariants(GringottsBlocks.GRINGOTTS_WHITE_MARBLE);
        pillarFrom(GringottsBlocks.GRINGOTTS_WHITE_MARBLE_PILLAR.block().get(), GringottsBlocks.GRINGOTTS_WHITE_MARBLE.base().get());
        stonecut(GringottsBlocks.GRINGOTTS_WHITE_MARBLE_PILLAR.block().get(), 1, GringottsBlocks.GRINGOTTS_WHITE_MARBLE.base().get());
        boxFrom(GringottsBlocks.GRINGOTTS_WHITE_MARBLE_TILES.base().get(), GringottsBlocks.GRINGOTTS_WHITE_MARBLE.base().get());
        craftStairSet(GringottsBlocks.GRINGOTTS_WHITE_MARBLE_TILES);
        stonecut(GringottsBlocks.GRINGOTTS_WHITE_MARBLE_TILES.base().get(), 1, GringottsBlocks.GRINGOTTS_WHITE_MARBLE.base().get());
        alt2x2(GringottsBlocks.GRINGOTTS_PALE_MARBLE.base().get(), 4, Items.CALCITE, Items.DIORITE);
        craftVariants(GringottsBlocks.GRINGOTTS_PALE_MARBLE);
        combine(GringottsBlocks.GRINGOTTS_GOLD_TRIM.block().get(), 1, Items.GOLD_INGOT, GringottsBlocks.GRINGOTTS_WHITE_MARBLE.base().get());
        tinted(GringottsBlocks.GRINGOTTS_IRON_VAULT_STONE.base().get(), 8, Items.IRON_INGOT, Items.STONE);
        craftVariants(GringottsBlocks.GRINGOTTS_IRON_VAULT_STONE);
        boxFrom(GringottsBlocks.GRINGOTTS_VAULT_BRICKS.base().get(), GringottsBlocks.GRINGOTTS_IRON_VAULT_STONE.base().get());
        craftVariants(GringottsBlocks.GRINGOTTS_VAULT_BRICKS);
        stonecut(GringottsBlocks.GRINGOTTS_VAULT_BRICKS.base().get(), 1, GringottsBlocks.GRINGOTTS_IRON_VAULT_STONE.base().get());
        tinted(GringottsBlocks.GRINGOTTS_GOBLIN_STONEWORK.base().get(), 8, Items.GOLD_NUGGET, Items.CHISELED_STONE_BRICKS);
        craftVariants(GringottsBlocks.GRINGOTTS_GOBLIN_STONEWORK);
        alt2x2(GringottsBlocks.GRINGOTTS_COUNTING_FLOOR.base().get(), 4, GringottsBlocks.GRINGOTTS_WHITE_MARBLE.base().get(), Items.GOLD_INGOT);
        craftSlabSet(GringottsBlocks.GRINGOTTS_COUNTING_FLOOR);
    }

    // --- crafting/stonecutter helpers for the variant records ---

    private void craftVariants(VariantSet set) {
        Block base = set.base().get();
        Block slab = set.slab().get();
        Block stairs = set.stairs().get();
        Block wall = set.wall().get();
        slabFrom(slab, base);
        stairBuilder(stairs, Ingredient.of(base)).unlockedBy("has_" + path(base), has(base)).save(structureSink);
        wallShaped(wall, base);
        stonecut(slab, 2, base);
        stonecut(stairs, 1, base);
        stonecut(wall, 1, base);
    }

    private void craftStairSet(StairSet set) {
        Block base = set.base().get();
        Block slab = set.slab().get();
        Block stairs = set.stairs().get();
        slabFrom(slab, base);
        stairBuilder(stairs, Ingredient.of(base)).unlockedBy("has_" + path(base), has(base)).save(structureSink);
        stonecut(slab, 2, base);
        stonecut(stairs, 1, base);
    }

    private void craftSlabSet(SlabSet set) {
        Block base = set.base().get();
        Block slab = set.slab().get();
        slabFrom(slab, base);
        stonecut(slab, 2, base);
    }

    /** Gated slab recipe (3-wide → 6), replacing the builtin {@code slab()} so output routes through the module sink. */
    private void slabFrom(ItemLike slab, ItemLike base) {
        ShapedRecipeBuilder.shaped(items(), RecipeCategory.BUILDING_BLOCKS, slab, 6)
                .pattern("###")
                .define('#', base)
                .unlockedBy("has_" + path(base), has(base))
                .save(structureSink);
    }

    /** Gated pressure-plate recipe (two-wide → 1), routed through the module sink. */
    private void pressurePlateFrom(ItemLike plate, ItemLike base) {
        ShapedRecipeBuilder.shaped(items(), RecipeCategory.REDSTONE, plate, 1)
                .pattern("##")
                .define('#', base)
                .unlockedBy("has_" + path(base), has(base))
                .save(structureSink);
    }

    // --- primitive recipe builders ---

    /** 3x3: eight {@code surround} ringing one {@code accent} → {@code count} result. */
    private void tinted(ItemLike result, int count, ItemLike accent, ItemLike surround) {
        ShapedRecipeBuilder.shaped(items(), RecipeCategory.BUILDING_BLOCKS, result, count)
                .pattern("###")
                .pattern("#X#")
                .pattern("###")
                .define('#', surround)
                .define('X', accent)
                .unlockedBy("has_" + path(surround), has(surround))
                .save(structureSink);
    }

    /** 2x2 checker of {@code a}/{@code b} → {@code count} result. */
    private void alt2x2(ItemLike result, int count, ItemLike a, ItemLike b) {
        ShapedRecipeBuilder.shaped(items(), RecipeCategory.BUILDING_BLOCKS, result, count)
                .pattern("AB")
                .pattern("BA")
                .define('A', a)
                .define('B', b)
                .unlockedBy("has_" + path(a), has(a))
                .save(structureSink);
    }

    /** 2x2 of one ingredient → 4 result (the brick/tile densification pattern). */
    private void boxFrom(ItemLike result, ItemLike from) {
        ShapedRecipeBuilder.shaped(items(), RecipeCategory.BUILDING_BLOCKS, result, 4)
                .pattern("##")
                .pattern("##")
                .define('#', from)
                .unlockedBy("has_" + path(from), has(from))
                .save(structureSink);
    }

    /** Two stacked ingredients → 2 pillar. */
    private void pillarFrom(ItemLike result, ItemLike from) {
        ShapedRecipeBuilder.shaped(items(), RecipeCategory.BUILDING_BLOCKS, result, 2)
                .pattern("#")
                .pattern("#")
                .define('#', from)
                .unlockedBy("has_" + path(from), has(from))
                .save(structureSink);
    }

    /** Six-wall shaped recipe (two rows of three). */
    private void wallShaped(ItemLike wall, ItemLike from) {
        ShapedRecipeBuilder.shaped(items(), RecipeCategory.BUILDING_BLOCKS, wall, 6)
                .pattern("###")
                .pattern("###")
                .define('#', from)
                .unlockedBy("has_" + path(from), has(from))
                .save(structureSink);
    }

    private void combine(ItemLike result, int count, ItemLike a, ItemLike b) {
        ShapelessRecipeBuilder.shapeless(items(), RecipeCategory.BUILDING_BLOCKS, result, count)
                .requires(a)
                .requires(b)
                .unlockedBy("has_" + path(a), has(a))
                .save(structureSink);
    }

    private void mossy(ItemLike result, ItemLike base, ItemLike moss) {
        ShapelessRecipeBuilder.shapeless(items(), RecipeCategory.BUILDING_BLOCKS, result)
                .requires(base)
                .requires(moss)
                .unlockedBy("has_" + path(base), has(base))
                .save(structureSink);
    }

    private void smelt(ItemLike result, ItemLike from) {
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(from), RecipeCategory.BUILDING_BLOCKS, result, 0.1f, 200)
                .unlockedBy("has_" + path(from), has(from))
                .save(structureSink, recipeId(path(result) + "_from_smelting"));
    }

    private void stonecut(ItemLike result, int count, ItemLike from) {
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(from), RecipeCategory.BUILDING_BLOCKS, result, count)
                .unlockedBy("has_" + path(from), has(from))
                .save(structureSink, recipeId("stonecutting/" + path(result) + "_from_" + path(from)));
    }

    private HolderGetter<Item> items() {
        return registries.lookupOrThrow(Registries.ITEM);
    }

    private static String path(ItemLike item) {
        return BuiltInRegistries.ITEM.getKey(item.asItem()).getPath();
    }

    private static String recipeId(String path) {
        return WizardsAndBeastsMod.MODID + ":" + path;
    }

    public static class Runner extends RecipeProvider.Runner {
        public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
            return new ModRecipeProvider(registries, output);
        }

        @Override
        public String getName() {
            return "WizardsAndBeastsMod Recipes";
        }
    }
}
