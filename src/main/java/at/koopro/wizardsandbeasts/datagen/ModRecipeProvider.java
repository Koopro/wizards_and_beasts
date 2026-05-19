package at.koopro.wizardsandbeasts.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.WoodSet;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;

import java.util.concurrent.CompletableFuture;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;

public class ModRecipeProvider extends RecipeProvider {

    public ModRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        for (WoodSet woodSet : ModBlocks.ALL_WOOD_SETS) {
            generateWoodRecipes(woodSet);
        }
        generateConsumableRecipes();
    }

    private void generateWoodRecipes(WoodSet woodSet) {
        Block log = woodSet.log().get();
        Block strippedLog = woodSet.strippedLog().get();
        Block woodBlock = woodSet.wood().get();
        Block strippedWood = woodSet.strippedWood().get();
        Block planks = woodSet.planks().get();
        Block slab = woodSet.slab().get();
        Block stairs = woodSet.stairs().get();

        planksFromLog(planks, log);
        planksFromLog(planks, strippedLog);
        planksFromLog(planks, woodBlock);
        planksFromLog(planks, strippedWood);

        woodFromLogs(woodBlock, log);
        woodFromLogs(strippedWood, strippedLog);

        slab(RecipeCategory.BUILDING_BLOCKS, slab, planks);
        stairBuilder(stairs, Ingredient.of(planks))
                .unlockedBy("has_planks", has(planks))
                .save(output);
    }

    private void planksFromLog(Block planks, Block log) {
        ShapelessRecipeBuilder.shapeless(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.BUILDING_BLOCKS, planks, 4)
                .requires(log)
                .group("planks")
                .unlockedBy("has_log", has(log))
                .save(output, getConversionRecipeName(planks, log));
    }

    private void generateConsumableRecipes() {
        ShapelessRecipeBuilder.shapeless(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, CurrencyItemRegistry.SICKLE.get(), CurrencyHelper.SICKLES_PER_GALLEON)
                .requires(CurrencyItemRegistry.GALLEON.get())
                .unlockedBy("has_galleon", has(CurrencyItemRegistry.GALLEON.get()))
                .save(output, "wizards_and_beasts:currency/galleon_to_sickle");

        ShapelessRecipeBuilder.shapeless(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, CurrencyItemRegistry.KNUT.get(), CurrencyHelper.KNUTS_PER_SICKLE)
                .requires(CurrencyItemRegistry.SICKLE.get())
                .unlockedBy("has_sickle", has(CurrencyItemRegistry.SICKLE.get()))
                .save(output, "wizards_and_beasts:currency/sickle_to_knut");

        ShapelessRecipeBuilder.shapeless(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.FOOD, ConsumableItemRegistry.TREACLE_TART.get())
                .requires(Items.WHEAT)
                .requires(Items.SUGAR)
                .requires(Items.EGG)
                .unlockedBy("has_sugar", has(Items.SUGAR))
                .save(output);

        ShapelessRecipeBuilder.shapeless(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.FOOD, ConsumableItemRegistry.PUMPKIN_PASTY.get(), 2)
                .requires(Items.PUMPKIN)
                .requires(Items.WHEAT)
                .requires(Items.SUGAR)
                .unlockedBy("has_pumpkin", has(Items.PUMPKIN))
                .save(output);

        ShapelessRecipeBuilder.shapeless(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.FOOD, ConsumableItemRegistry.FIZZING_WHIZZBEE.get(), 2)
                .requires(Items.HONEYCOMB)
                .requires(Items.SUGAR)
                .unlockedBy("has_honeycomb", has(Items.HONEYCOMB))
                .save(output);

        ShapelessRecipeBuilder.shapeless(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.FOOD, ConsumableItemRegistry.PEPPERMINT_TOAD.get(), 2)
                .requires(Items.COCOA_BEANS)
                .requires(Items.SUGAR)
                .unlockedBy("has_cocoa_beans", has(Items.COCOA_BEANS))
                .save(output);

        ShapelessRecipeBuilder.shapeless(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, MiscItemRegistry.FLOO_POWDER.get(), 2)
                .requires(Items.GUNPOWDER)
                .requires(Items.BLAZE_POWDER)
                .unlockedBy("has_blaze_powder", has(Items.BLAZE_POWDER))
                .save(output);

        ShapedRecipeBuilder.shaped(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.TOOLS, MiscItemRegistry.DELUMINATOR.get())
                .pattern(" IT")
                .pattern("IRI")
                .pattern(" II")
                .define('I', Items.IRON_INGOT)
                .define('T', Items.TORCH)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(output);

        ShapedRecipeBuilder.shaped(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.TOOLS, TrinketItemRegistry.REMEMBRALL.get())
                .pattern(" G ")
                .pattern("GRG")
                .pattern(" G ")
                .define('G', Items.GLASS)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(output);

        ShapedRecipeBuilder.shaped(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.TOOLS, TrinketItemRegistry.OMNI_OCULARS.get())
                .pattern("ALA")
                .pattern("ASA")
                .pattern("AAA")
                .define('A', Items.AMETHYST_SHARD)
                .define('L', Items.LEATHER)
                .define('S', Items.SPYGLASS)
                .unlockedBy("has_spyglass", has(Items.SPYGLASS))
                .save(output);

        ShapedRecipeBuilder.shaped(registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, ConsumableItemRegistry.FAMOUS_WIZARD_CARD.get(), 2)
                .pattern("PI")
                .pattern("PG")
                .define('P', Items.PAPER)
                .define('I', MiscItemRegistry.INK_BOTTLE.get())
                .define('G', CurrencyItemRegistry.KNUT.get())
                .unlockedBy("has_paper", has(Items.PAPER))
                .save(output);
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
