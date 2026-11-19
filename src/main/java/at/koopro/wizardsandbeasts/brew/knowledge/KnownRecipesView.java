package at.koopro.wizardsandbeasts.brew.knowledge;

import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The client's copy of what this player knows and what each textbook teaches ({@link BrewKnowledgeS2CPayload}).
 * Plain data, no client classes, so item tooltips -- which run on both sides -- may read it safely; on a server it
 * stays empty and the tooltip shows only names.
 */
@NullMarked
public final class KnownRecipesView {

    private static volatile Set<String> known = Set.of();
    private static volatile Map<String, List<String>> books = Map.of();
    private static volatile Map<String, BrewKnowledgeS2CPayload.Line> lines = Map.of();

    private KnownRecipesView() {}

    public static boolean knows(String recipeId) {
        return known.contains(recipeId);
    }

    /** What the textbook with this item path teaches, or empty. */
    public static List<String> book(String itemPath) {
        return books.getOrDefault(itemPath, List.of());
    }

    /** How a recipe is made, for a tooltip; null when this client was never told. */
    public static BrewKnowledgeS2CPayload.@org.jspecify.annotations.Nullable Line line(String recipeId) {
        return lines.get(recipeId);
    }

    /** Every recipe this client was told about, known or not, in the server's order. */
    public static java.util.Collection<BrewKnowledgeS2CPayload.Line> lines() {
        return lines.values();
    }

    /** Item paths of the textbooks that teach {@code recipeId}; empty when only a lesson or an experiment does. */
    public static List<String> taughtIn(String recipeId) {
        List<String> out = new java.util.ArrayList<>();
        books.forEach((book, recipes) -> {
            if (recipes.contains(recipeId)) {
                out.add(book);
            }
        });
        java.util.Collections.sort(out);
        return out;
    }

    static void accept(List<String> knownIds, Map<String, List<String>> bookLists, List<BrewKnowledgeS2CPayload.Line> recipeLines) {
        known = Set.copyOf(knownIds);
        books = Map.copyOf(bookLists);
        Map<String, BrewKnowledgeS2CPayload.Line> byId = new java.util.HashMap<>();
        recipeLines.forEach(l -> byId.put(l.recipe(), l));
        lines = Map.copyOf(byId);
    }
}
