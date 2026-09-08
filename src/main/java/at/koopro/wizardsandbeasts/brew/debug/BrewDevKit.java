package at.koopro.wizardsandbeasts.brew.debug;

import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.brew.BrewingRecipes;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.command.debug.dev.DevLog;
import at.koopro.wizardsandbeasts.command.debug.dev.FeatureDevKit;
import at.koopro.wizardsandbeasts.item.brew.BrewItem;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A pot of every tier, bottles to decant into, and the ingredients the loaded recipes actually ask
 * for.
 *
 * <h2>The ingredients are read out of the recipes</h2>
 *
 * <p>Not a hand-written list. Brewing content is datapack-driven, so a hard-coded kit would go stale
 * the first time somebody edited a recipe and would then hand you the wrong ingredients — which
 * looks exactly like a cauldron refusing them. Walking {@link BrewingRecipes} means the kit is
 * correct by construction for whatever is loaded right now.
 *
 * <p>Finished bottles come too. Testing what a brew <em>does</em> and testing whether a cauldron can
 * make it are different jobs, and only one of them should require twenty minutes at a fire.
 */
@NullMarked
public final class BrewDevKit implements FeatureDevKit {

    /** Enough of an ingredient for several attempts, including the ones you get wrong. */
    private static final int INGREDIENT_COUNT = 16;
    private static final int BOTTLES = 32;
    /** Ingredient kinds handed over before the kit stops, so a big datapack cannot fill an inventory. */
    private static final int MAX_INGREDIENT_KINDS = 27;

    @Override
    public String id() {
        return "brewing";
    }

    @Override
    public String title() {
        return "Brewing";
    }

    @Override
    public String summary() {
        return "All three cauldrons, glass bottles, every loaded brew bottled, and the ingredients "
                + "the loaded recipes ask for.";
    }

    @Override
    public void kit(ServerPlayer target, DevLog log) {
        target.getInventory().add(new ItemStack(
                ModBlocks.PEWTER_CAULDRON_ITEM.get()));
        target.getInventory().add(new ItemStack(
                ModBlocks.BRASS_CAULDRON_ITEM.get()));
        target.getInventory().add(new ItemStack(
                ModBlocks.WIZARDING_COPPER_CAULDRON_ITEM.get()));
        log.changed("cauldrons", "pewter, brass and wizarding copper");

        target.getInventory().add(new ItemStack(Items.GLASS_BOTTLE, BOTTLES));
        target.getInventory().add(new ItemStack(Items.WATER_BUCKET));
        log.changed("bottles and water", BOTTLES + " bottles, 1 bucket");

        giveIngredients(target, log);
        giveFinishedBrews(target, log);
    }

    /** Every distinct ingredient named by a loaded recipe. See the class note. */
    private static void giveIngredients(ServerPlayer target, DevLog log) {
        if (BrewingRecipes.all().isEmpty()) {
            log.warn("no brewing recipes loaded - the datapack listener has not run");
            return;
        }
        Set<net.minecraft.world.item.Item> wanted = new LinkedHashSet<>();
        for (BrewingRecipe recipe : BrewingRecipes.all()) {
            recipe.ingredients().forEach(ingredient -> wanted.add(ingredient.item()));
            recipe.catalyst().ifPresent(catalyst -> wanted.add(catalyst.item()));
        }
        int given = 0;
        for (net.minecraft.world.item.Item item : wanted) {
            if (given >= MAX_INGREDIENT_KINDS) {
                log.skip("stopped at " + MAX_INGREDIENT_KINDS + " ingredient kinds ("
                        + (wanted.size() - MAX_INGREDIENT_KINDS) + " more exist)");
                break;
            }
            target.getInventory().add(new ItemStack(item, INGREDIENT_COUNT));
            given++;
        }
        log.changed("ingredients", given + " kinds x" + INGREDIENT_COUNT
                + ", read out of " + BrewingRecipes.all().size() + " recipes");
    }

    private static void giveFinishedBrews(ServerPlayer target, DevLog log) {
        if (Brews.all().isEmpty()) {
            log.warn("no brews loaded - the datapack listener has not run");
            return;
        }
        for (Brew brew : Brews.all()) {
            target.getInventory().add(BrewItem.of(brew));
        }
        log.changed("bottled brews", Brews.all().size() + " - one of each, already made");
    }
}
