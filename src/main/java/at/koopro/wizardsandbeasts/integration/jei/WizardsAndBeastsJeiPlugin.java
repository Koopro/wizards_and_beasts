package at.koopro.wizardsandbeasts.integration.jei;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.client.brew.ClientBrewData;
import at.koopro.wizardsandbeasts.client.module.ClientModuleState;
import at.koopro.wizardsandbeasts.client.recipe.ClientSyncedRecipes;
import at.koopro.wizardsandbeasts.integration.viewer.ViewerContentFilter;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.wand.recipe.WandmakingRecipe;
import at.koopro.wizardsandbeasts.wand.recipe.WandmakingRecipeType;
import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;

/**
 * Exposes this mod's two bespoke crafting pipelines to JEI, and keeps JEI's view in step with module state.
 *
 * <p>Both pipelines were fully implemented and completely undiscoverable: nothing told a player that the
 * Wandmaker's Bench or cauldron brewing existed, let alone what went into them. That is the reason this
 * integration exists; the hiding below is the second reason.
 *
 * <p>This class is only ever loaded when JEI is present — JEI finds it by annotation — so referencing JEI
 * types here is safe. Nothing outside this package refers to it, which is what keeps the mod loading with
 * JEI absent. The decision about <em>what</em> to hide lives in {@link ViewerContentFilter}, which has no
 * JEI imports; this class only performs it.
 */
@NullMarked
@JeiPlugin
public class WizardsAndBeastsJeiPlugin implements IModPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "jei_plugin");

    private @Nullable IJeiRuntime runtime;

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new WandmakingCategory(guiHelper),
                new CauldronBrewingCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(WandmakingCategory.TYPE, wandmakingRecipes());
        registration.addRecipes(CauldronBrewingCategory.TYPE, ClientBrewData.recipes());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(WandmakingCategory.benchItem()),
                WandmakingCategory.TYPE);
        // All three cauldrons brew; which one a given recipe needs is drawn on the recipe itself.
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.BRASS_CAULDRON_ITEM.get()),
                CauldronBrewingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.WIZARDING_COPPER_CAULDRON_ITEM.get()),
                CauldronBrewingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.PEWTER_CAULDRON_ITEM.get()),
                CauldronBrewingCategory.TYPE);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        this.runtime = jeiRuntime;
        // Both triggers matter. A module flip is the case this exists for; a brew sync changes which
        // recipes exist at all, and arrives on join and on every /reload.
        ClientModuleState.whenApplied(this::applyHiding);
        ClientBrewData.whenApplied(this::applyHiding);
        applyHiding();
    }

    @Override
    public void onRuntimeUnavailable() {
        this.runtime = null;
    }

    /**
     * Brings JEI's ingredient list, its search index and its recipes back in line with module state.
     *
     * <p>Two calls are needed, not one. Removing an ingredient takes it out of the list and out of search,
     * but JEI's visibility check is purely ingredient-level: recipes mentioning a hidden ingredient stay
     * visible until hidden separately. Hiding only the ingredient would leave a player able to look up a
     * recipe for an item the game says does not exist.
     *
     * <p>Both directions are handled every time rather than tracking what was hidden last: re-adding an
     * already-visible ingredient is a no-op, and deriving the full answer from current state cannot drift
     * the way an incremental record can.
     */
    private void applyHiding() {
        IJeiRuntime jei = this.runtime;
        if (jei == null) {
            return;
        }
        // JEI's runtime is client-side and not thread-safe; module syncs and brew syncs already arrive
        // through enqueueWork, but a stray caller would corrupt the ingredient list rather than throw.
        if (!Minecraft.getInstance().isSameThread()) {
            Minecraft.getInstance().execute(this::applyHiding);
            return;
        }

        var ingredients = jei.getIngredientManager();
        List<ItemStack> hidden = ViewerContentFilter.hiddenItems().stream().map(ItemStack::new).toList();
        List<ItemStack> visible = ViewerContentFilter.visibleItems().stream().map(ItemStack::new).toList();
        ingredients.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, visible);
        ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);

        hideRecipes(jei);
        LOGGER.debug("JEI hiding applied: {} items hidden, {} visible", hidden.size(), visible.size());
    }

    private void hideRecipes(IJeiRuntime jei) {
        var recipes = jei.getRecipeManager();

        // includeHidden() is essential: without it, a recipe hidden by a previous pass is not returned and
        // so can never be unhidden when its module comes back.
        List<WandmakingRecipe> allWandmaking = recipes.createRecipeLookup(WandmakingCategory.TYPE)
                .includeHidden().get().toList();
        recipes.unhideRecipes(WandmakingCategory.TYPE, allWandmaking);
        recipes.hideRecipes(WandmakingCategory.TYPE, allWandmaking.stream()
                .filter(r -> ViewerContentFilter.hidesRecipe(WandmakingCategory.displayedStacks(r)))
                .toList());

        List<BrewingRecipe> allBrewing = recipes.createRecipeLookup(CauldronBrewingCategory.TYPE)
                .includeHidden().get().toList();
        recipes.unhideRecipes(CauldronBrewingCategory.TYPE, allBrewing);
        recipes.hideRecipes(CauldronBrewingCategory.TYPE, allBrewing.stream()
                .filter(r -> ViewerContentFilter.hidesRecipe(CauldronBrewingCategory.displayedStacks(r)))
                .toList());
    }

    /**
     * The wandmaking recipes the client actually has.
     *
     * <p>They arrive only because {@code RecipeDataSyncEvents} asks NeoForge to send that type; vanilla
     * has not synced full recipes since 1.21.2. An empty list here means that request did not happen, not
     * that no recipes are defined.
     */
    private static List<WandmakingRecipe> wandmakingRecipes() {
        return ClientSyncedRecipes.byType(WandmakingRecipeType.INSTANCE.get());
    }
}
