package at.koopro.wizardsandbeasts.wand.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.List;

public record WandmakingRecipe(
        Identifier woodKey,
        Identifier coreKey,
        float minimumBenchTier,
        float resultLengthMin,
        float resultLengthMax,
        float resultIntegrity) implements Recipe<RecipeInput> {

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        // Prompt B: WandmakersBenchMenu resolves the output wand stack here.
        return ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<? extends Recipe<RecipeInput>> getSerializer() {
        return WandmakingRecipeSerializer.INSTANCE.get();
    }

    @Override
    public RecipeType<? extends Recipe<RecipeInput>> getType() {
        return WandmakingRecipeType.INSTANCE.get();
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return null;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(List.of());
    }
}
