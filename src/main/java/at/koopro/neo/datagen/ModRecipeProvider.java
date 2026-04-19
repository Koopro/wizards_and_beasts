package at.koopro.neo.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import at.koopro.neo.registry.ModBlocks;
import at.koopro.neo.registry.WoodSet;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider {

    public ModRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        for (WoodSet woodSet : ModBlocks.ALL_WOOD_SETS) {
            generateWoodRecipes(woodSet);
        }
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
            return "Neo Recipes";
        }
    }
}
