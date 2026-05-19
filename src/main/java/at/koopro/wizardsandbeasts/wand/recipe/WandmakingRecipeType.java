package at.koopro.wizardsandbeasts.wand.recipe;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WandmakingRecipeType implements RecipeType<WandmakingRecipe> {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<WandmakingRecipe>> INSTANCE =
            RECIPE_TYPES.register("wandmaking", WandmakingRecipeType::new);

    @Override
    public String toString() {
        return WizardsAndBeastsMod.MODID + ":wandmaking";
    }
}
