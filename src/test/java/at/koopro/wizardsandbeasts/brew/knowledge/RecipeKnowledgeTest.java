package at.koopro.wizardsandbeasts.brew.knowledge;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recipe knowledge: the pure rule, the saved book, and the shipped {@code brew_recipe_sources} data -- the way it
 * fails silently is a source naming a recipe that does not exist (it teaches nothing) or a recipe no route teaches
 * (it can only ever be an experiment).
 */
class RecipeKnowledgeTest {

    private static final Path DATA = Path.of("src", "main", "resources", "data", "wizards_and_beasts");
    private static final Path SOURCES = DATA.resolve("brew_recipe_sources");
    private static final Path RECIPES = DATA.resolve("brewing_recipes");
    private static final Path CANON_ITEMS = Path.of("src", "main", "java", "at", "koopro", "wizardsandbeasts",
            "registry", "CanonItemRegistry.java");
    private static final Set<String> NOT_BOOKS = Set.of("basic", "apothecary_lessons", "recipe_pages");

    @Test
    void basicsAreKnownWithoutBeingWritten() {
        assertTrue(BrewKnowledge.knows(BrewRecipeBook.EMPTY, List.of("a:pepperup"), "a:pepperup"));
        assertFalse(BrewKnowledge.knows(BrewRecipeBook.EMPTY, List.of("a:pepperup"), "a:polyjuice"));
        assertTrue(BrewKnowledge.knows(BrewRecipeBook.EMPTY.with(List.of("a:polyjuice")), List.of(), "a:polyjuice"));
    }

    @Test
    void bookRoundTripsAndOldSavesReadEmpty() {
        BrewRecipeBook book = BrewRecipeBook.EMPTY.with(List.of("a:x", "a:y"));
        JsonElement json = BrewRecipeBook.CODEC.encodeStart(JsonOps.INSTANCE, book).getOrThrow();
        assertEquals(book, BrewRecipeBook.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(BrewRecipeBook.EMPTY,
                BrewRecipeBook.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow());
    }

    @Test
    void everySourceNamesARealRecipe() throws IOException {
        Set<String> recipes = shippedRecipes();
        sources().forEach((source, ids) -> ids.forEach(id ->
                assertTrue(recipes.contains(id), source + " teaches " + id + ", which is no brewing recipe")));
    }

    @Test
    void everyRecipeIsTaughtSomewhereBesidesAnExperiment() throws IOException {
        Set<String> taught = new HashSet<>();
        sources().values().forEach(taught::addAll);
        for (String recipe : shippedRecipes()) {
            assertTrue(taught.contains(recipe), recipe + " is in no recipe source");
        }
    }

    @Test
    void basicsAreNeverFoundAsPages() throws IOException {
        Map<String, List<String>> sources = sources();
        for (String basic : sources.get("basic")) {
            assertFalse(sources.get("recipe_pages").contains(basic), basic + " is known by all, a page of it is waste");
        }
    }

    @Test
    void everyBookSourceIsAReadableTextbook() throws IOException {
        String registry = Files.readString(CANON_ITEMS);
        for (String source : sources().keySet()) {
            if (NOT_BOOKS.contains(source)) {
                continue;
            }
            assertTrue(registry.contains("registerItem(\"" + source + "\", props -> new at.koopro.wizardsandbeasts"
                    + ".brew.knowledge.PotionRecipeItem("), source + " teaches recipes but is no PotionRecipeItem");
        }
    }

    private static Set<String> shippedRecipes() throws IOException {
        Set<String> ids = new HashSet<>();
        try (Stream<Path> files = Files.list(RECIPES)) {
            files.filter(p -> p.toString().endsWith(".json")).forEach(p -> ids.add(
                    "wizards_and_beasts:" + p.getFileName().toString().replace(".json", "")));
        }
        return ids;
    }

    private static Map<String, List<String>> sources() throws IOException {
        Map<String, List<String>> out = new HashMap<>();
        try (Stream<Path> files = Files.list(SOURCES)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                RecipeSources.Source source = RecipeSources.Source.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString(Files.readString(p))).getOrThrow();
                out.put(p.getFileName().toString().replace(".json", ""), source.recipes());
            }
        }
        return out;
    }
}
