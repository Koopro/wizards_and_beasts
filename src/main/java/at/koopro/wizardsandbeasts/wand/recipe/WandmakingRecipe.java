package at.koopro.wizardsandbeasts.wand.recipe;

import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

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

    /**
     * Always empty, and it has to be: a wand's flexibility is chosen on the bench and its length is rolled
     * per craft, so neither is recoverable from a {@link RecipeInput}. {@link #createWand} is the real
     * builder — call that with the two values this signature cannot carry.
     */
    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    /**
     * The wand this recipe describes.
     *
     * <p>The bench menu and the JEI category both used to build this stack themselves, from the same six
     * fields, and they had already drifted — the viewer's copy set no flexibility and never refreshed the
     * elder-wand marker, so an elder wand looked ordinary in the recipe list. One builder, two callers.
     *
     * @param flexibility the bench's current selection, or {@code null} for a display stack that is not
     *                    claiming a particular flexibility
     * @param lengthInches a value from {@link #rollLength}, or {@link #resultLengthMin()} for a display
     *                     stack — showing a fixed length as if it were the outcome would be a lie
     */
    public ItemStack createWand(@Nullable WandFlexibility flexibility, float lengthInches) {
        ItemStack wand = new ItemStack(WandItemRegistry.WAND.get());
        wand.set(WandComponents.WAND_WOOD.get(), woodKey);
        wand.set(WandComponents.WAND_CORE.get(), coreKey);
        if (flexibility != null) {
            wand.set(WandComponents.WAND_FLEXIBILITY.get(), flexibility);
        }
        wand.set(WandComponents.WAND_LENGTH.get(), lengthInches);
        wand.set(WandComponents.WAND_INTEGRITY.get(), resultIntegrity);
        wand.set(WandComponents.WAND_MASTER.get(), Optional.empty());
        ModDataComponents.refreshElderWandMarker(wand);
        return wand;
    }

    /** A length from this recipe's range. Rolled per craft, which is why {@link #assemble} cannot. */
    public float rollLength(RandomSource random) {
        return resultLengthMin + random.nextFloat() * (resultLengthMax - resultLengthMin);
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
    public boolean isSpecial() {
        // Bench-resolved, no placeable ingredients — keeps RecipeManager from warning
        // "can't be placed due to empty ingredients" at every load. The recipe stays in
        // RecipeMap either way; only recipe-book placement is skipped.
        return true;
    }

    @Override
    public @Nullable RecipeBookCategory recipeBookCategory() {
        return null;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(List.of());
    }
}
