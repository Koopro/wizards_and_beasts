package at.koopro.wizardsandbeasts.integration.jei;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.wand.WandCoreMaterialItem;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.recipe.WandmakingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Shows what the Wandmaker's Bench turns a blank and a core into.
 *
 * <p>{@code WandmakingRecipe} cannot assemble its own result — {@code matches} returns false and
 * {@code assemble} returns an empty stack, because the bench menu resolves the wand itself from the
 * player's bench tier and chosen flexibility. So this category builds the display stacks the same way the
 * menu does rather than calling the recipe. Length is shown at the low end of the recipe's range, since
 * the real value is rolled per craft and a viewer that implied a fixed length would be lying.
 */
@NullMarked
public class WandmakingCategory implements IRecipeCategory<WandmakingRecipe> {

    public static final IRecipeType<WandmakingRecipe> TYPE = IRecipeType.create(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wandmaking"),
            WandmakingRecipe.class);

    private final IDrawable icon;

    public WandmakingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.WANDMAKERS_BENCH_ITEM.get()));
    }

    @Override
    public IRecipeType<WandmakingRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        // The bench's own name, already translated. A viewer category is not a place to coin a second
        // name for a thing the game has already named.
        return Component.translatable("block.wizards_and_beasts.wandmakers_bench");
    }

    @Override
    public int getWidth() {
        return 150;
    }

    @Override
    public int getHeight() {
        return 40;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, WandmakingRecipe recipe, IFocusGroup focuses) {
        // Slot positions mirror WandmakersBenchMenu: blank, core, output.
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 12)
                .addItemStack(blankFor(recipe.woodKey()));
        builder.addSlot(RecipeIngredientRole.INPUT, 37, 12)
                .addItemStacks(coresFor(recipe.coreKey()));
        builder.addSlot(RecipeIngredientRole.OUTPUT, 91, 12)
                .addItemStack(resultFor(recipe));
    }

    private static ItemStack blankFor(Identifier woodKey) {
        ItemStack blank = new ItemStack(WandItemRegistry.WAND_BLANK.get());
        blank.set(WandComponents.WAND_WOOD.get(), woodKey);
        return blank;
    }

    /**
     * Every core item declaring this core key. A list rather than one stack because the key is the
     * wandlore identity, not the item — if two items ever carry the same core, both are valid input and
     * the viewer should cycle them rather than silently pick one.
     */
    private static List<ItemStack> coresFor(Identifier coreKey) {
        return BuiltInRegistries.ITEM.stream()
                .filter(item -> item instanceof WandCoreMaterialItem core && coreKey.equals(core.getCoreKey()))
                .map(ItemStack::new)
                .toList();
    }

    /**
     * No flexibility, and the low end of the length range: both are chosen or rolled at the bench, and a
     * viewer that implied a fixed value would be lying. The stack itself comes from the recipe now — this
     * category used to build its own and had drifted, missing the elder-wand marker.
     */
    private static ItemStack resultFor(WandmakingRecipe recipe) {
        return recipe.createWand(null, recipe.resultLengthMin());
    }

    /** The stacks this recipe names, for the hiding pass. */
    static List<ItemStack> displayedStacks(WandmakingRecipe recipe) {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(blankFor(recipe.woodKey()), resultFor(recipe)),
                        coresFor(recipe.coreKey()).stream())
                .toList();
    }

    /** Convenience for the catalyst registration. */
    static Item benchItem() {
        return ModBlocks.WANDMAKERS_BENCH_ITEM.get();
    }
}
