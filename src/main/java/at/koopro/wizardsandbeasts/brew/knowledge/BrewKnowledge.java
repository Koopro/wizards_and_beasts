package at.koopro.wizardsandbeasts.brew.knowledge;

import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.brew.BrewingRecipes;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Which brewing recipes a player knows, and learning more. User decision (2026-10-22): recipes are known, not given
 * -- everyone starts with the basics; textbooks, loose pages and the apothecary's lessons teach the rest; and a
 * correct combination nobody taught you can still be brewed as an experiment, riskier, and learned if it works.
 */
@NullMarked
public final class BrewKnowledge {

    /** Added to the failure chance of a brew whose recipe the brewer does not know. */
    public static final float EXPERIMENT_PENALTY = 0.30f;

    private BrewKnowledge() {}

    /** Pure: whether a recipe is known, given the player's book and the basics. */
    public static boolean knows(BrewRecipeBook book, Collection<String> basics, String recipeId) {
        return basics.contains(recipeId) || book.has(recipeId);
    }

    public static boolean knows(ServerPlayer player, String recipeId) {
        return knows(book(player), RecipeSources.recipes(RecipeSources.BASIC), recipeId);
    }

    public static BrewRecipeBook book(ServerPlayer player) {
        return player.getData(ModAttachments.BREW_RECIPE_BOOK.get());
    }

    /**
     * Teaches the player every recipe in {@code recipeIds} that exists and they do not know yet. Returns what was
     * new, in the given order.
     */
    public static List<String> learn(ServerPlayer player, Collection<String> recipeIds) {
        List<String> fresh = new ArrayList<>();
        for (String id : recipeIds) {
            if (BrewingRecipes.byId(id) != null && !knows(player, id) && !fresh.contains(id)) {
                fresh.add(id);
            }
        }
        if (!fresh.isEmpty()) {
            player.setData(ModAttachments.BREW_RECIPE_BOOK.get(), book(player).with(fresh));
            BrewKnowledgeS2CPayload.sendTo(player);
        }
        return fresh;
    }

    /** The next recipe an apothecary lesson teaches this player, or null when they know them all. */
    public static @Nullable String nextLesson(ServerPlayer player) {
        for (String id : RecipeSources.recipes(RecipeSources.LESSONS)) {
            if (BrewingRecipes.byId(id) != null && !knows(player, id)) {
                return id;
            }
        }
        return null;
    }

    /** "Pepperup Potion", for a recipe id; the id itself when the brew is gone. */
    public static Component name(String recipeId) {
        BrewingRecipe recipe = BrewingRecipes.byId(recipeId);
        Brew brew = recipe == null ? null : Brews.byId(recipe.outputBrewId());
        return brew == null ? Component.literal(recipeId) : Component.translatable(brew.displayName());
    }
}
