package at.koopro.wizardsandbeasts.integration.jei;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.brew.CauldronTier;
import at.koopro.wizardsandbeasts.client.brew.ClientBrewData;
import at.koopro.wizardsandbeasts.item.brew.BrewItem;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows what goes into a cauldron and which brew comes out.
 *
 * <p>Brewing has no GUI of its own — it is a right-click on a cauldron block plus a server tick — so
 * there is no screen texture to derive a background from and the layout is built from slots alone.
 *
 * <p>The recipe names its output as a brew id rather than a stack, because brews reload independently of
 * recipes and resolving eagerly would bake in whichever load happened to run first. The output stack is
 * therefore built here from the id, the same way consumption resolves it.
 */
@NullMarked
public class CauldronBrewingCategory implements IRecipeCategory<BrewingRecipe> {

    public static final IRecipeType<BrewingRecipe> TYPE = IRecipeType.create(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "cauldron_brewing"),
            BrewingRecipe.class);

    private static final int SLOTS_PER_ROW = 4;

    private final IDrawable icon;

    public CauldronBrewingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.BRASS_CAULDRON_ITEM.get()));
    }

    @Override
    public IRecipeType<BrewingRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.wizards_and_beasts.jei.cauldron_brewing");
    }

    @Override
    public int getWidth() {
        return 150;
    }

    @Override
    public int getHeight() {
        return 60;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BrewingRecipe recipe, IFocusGroup focuses) {
        List<BrewingRecipe.Ingredient> ingredients = recipe.ingredients();
        for (int i = 0; i < ingredients.size(); i++) {
            BrewingRecipe.Ingredient ingredient = ingredients.get(i);
            int x = 1 + (i % SLOTS_PER_ROW) * 18;
            int y = 1 + (i / SLOTS_PER_ROW) * 18;
            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                    .addItemStack(new ItemStack(ingredient.item(), ingredient.count()));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 110, 10).addItemStack(outputFor(recipe));
    }

    @Override
    public void draw(BrewingRecipe recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        // Which cauldron you need is the one thing about this recipe a player cannot read off the slots.
        Component tier = cauldronName(recipe.cauldronTier());
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                Component.translatable("gui.wizards_and_beasts.jei.cauldron_brewing.tier", tier),
                1, getHeight() - 10, 0xFF404040, false);
    }

    private static Component cauldronName(CauldronTier tier) {
        return switch (tier) {
            case BRASS -> Component.translatable("block.wizards_and_beasts.brass_cauldron");
            case COPPER -> Component.translatable("block.wizards_and_beasts.wizarding_copper_cauldron");
            case PEWTER -> Component.translatable("block.wizards_and_beasts.pewter_cauldron");
        };
    }

    /**
     * Resolves the output through the client's synced brew table, not the server-side {@code Brews}
     * registry — on a remote client that one is empty. An unresolved id yields an empty stack rather than
     * a crash: a datapack can name a brew it never defines, and a viewer is the wrong place to enforce it.
     */
    private static ItemStack outputFor(BrewingRecipe recipe) {
        Brew brew = ClientBrewData.brew(recipe.outputBrewId());
        return brew == null ? ItemStack.EMPTY : BrewItem.of(brew);
    }

    /** The stacks this recipe names, for the hiding pass. */
    static List<ItemStack> displayedStacks(BrewingRecipe recipe) {
        List<ItemStack> stacks = new ArrayList<>();
        recipe.ingredients().forEach(i -> stacks.add(new ItemStack(i.item(), i.count())));
        stacks.add(outputFor(recipe));
        return stacks;
    }
}
