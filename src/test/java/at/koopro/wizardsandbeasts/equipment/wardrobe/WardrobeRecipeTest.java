package at.koopro.wizardsandbeasts.equipment.wardrobe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the wardrobe is obtained in survival (WARDROBE_RENAISSANCE Phase E4): recipes for the cuts a
 * player makes, dye recipes for liveries, career ranks for the uniforms a player is issued.
 *
 * <p>A recipe that names a livery its cut does not admit hands out a robe the stamp door would have
 * refused, and two recipes with the same inputs make one of them unreachable: the clothing would
 * simply never appear. Neither fails anywhere else, so the shipped files are read here.
 */
class WardrobeRecipeTest {

    private static final String MOD = "wizards_and_beasts";
    private static final Path DATA = Path.of("src", "main", "resources", "data", MOD);
    private static final Path RECIPES = DATA.resolve("recipe/wardrobe");
    private static final Path LIVERIES = DATA.resolve(MOD + "/livery");
    private static final Path ITEM_DEFINITIONS =
            Path.of("src", "generated", "resources", "assets", MOD, "items");

    /** Issued by a career rank, never made: no base recipe on purpose. */
    private static final Set<String> ISSUED = Set.of("auror_robe_chest", "duty_belt", "master_mantle_chest");

    /** Vanilla's armour shapes; a single-material wardrobe recipe in one of these would collide with it. */
    private static final List<List<String>> VANILLA_SHAPES = List.of(
            List.of("X X", "X X"), List.of("XXX", "X X"), List.of("X X", "XXX", "XXX"), List.of("XXX", "X X", "X X"));

    @Test
    void everyResultIsARealItemAndEveryStitchedResultIsAdmitted() throws IOException {
        for (Map.Entry<String, JsonObject> recipe : recipes().entrySet()) {
            assertResult(recipe.getValue().getAsJsonObject("result"), recipe.getKey());
        }
    }

    @Test
    void careerUniformsAreRealItemsInLiveriesThatAdmitThem() throws IOException {
        int uniforms = 0;
        try (Stream<Path> files = Files.list(DATA.resolve("career_tracks"))) {
            for (Path file : files.toList()) {
                for (JsonElement rank : read(file).getAsJsonObject().getAsJsonArray("ranks")) {
                    JsonObject r = rank.getAsJsonObject();
                    if (!r.has("rewards")) {
                        continue;
                    }
                    for (JsonElement reward : r.getAsJsonArray("rewards")) {
                        JsonObject stack = reward.getAsJsonObject();
                        if (stack.has("components") && stack.getAsJsonObject("components").has(MOD + ":outfit")) {
                            uniforms++;
                        }
                        assertResult(stack, file.getFileName() + " " + r.get("id").getAsString());
                    }
                }
            }
        }
        assertTrue(uniforms >= 5, "career ranks issue the stitched uniforms (Auror ranks, field naturalist, master)");
    }

    @Test
    void everyMadeCutHasARecipeAndNoIssuedCutHasOne() throws IOException {
        Set<String> made = new HashSet<>();
        for (JsonObject recipe : recipes().values()) {
            if (recipe.get("type").getAsString().equals("minecraft:crafting_shaped")) {
                made.add(path(recipe.getAsJsonObject("result").get("id").getAsString()));
            }
        }
        for (String cut : List.of("student_robe_chest", "death_eater_robe_chest", "wizard_robe_chest",
                "scholar_gown_chest", "travel_coat_chest", "healer_coat_chest", "ministry_robe_chest",
                "school_cape", "travel_cloak", "leather_belt", "scarf", "gloves", "professor_hat", "travel_hat")) {
            assertTrue(made.contains(cut), cut + " has no crafting recipe");
        }
        for (String issued : ISSUED) {
            assertFalse(made.contains(issued), issued + " is issued by a career rank, not made");
        }
    }

    @Test
    void noTwoRecipesTakeTheSameInputsAndNoneTakesAVanillaArmourShape() throws IOException {
        Map<String, String> seen = new HashMap<>();
        for (Map.Entry<String, JsonObject> entry : recipes().entrySet()) {
            JsonObject recipe = entry.getValue();
            String signature;
            if (recipe.get("type").getAsString().equals("minecraft:crafting_shaped")) {
                List<String> pattern = new ArrayList<>();
                recipe.getAsJsonArray("pattern").forEach(e -> pattern.add(e.getAsString()));
                Map<String, String> key = new TreeMap<>();
                recipe.getAsJsonObject("key").entrySet().forEach(e -> key.put(e.getKey(), e.getValue().getAsString()));
                StringBuilder grid = new StringBuilder();
                for (String row : pattern) {
                    for (char c : row.toCharArray()) {
                        grid.append(c == ' ' ? "_" : key.get(String.valueOf(c))).append(',');
                    }
                    grid.append('/');
                }
                signature = "shaped:" + grid;
                if (key.size() == 1) {
                    List<String> shape = pattern.stream().map(r -> r.replaceAll("[^ ]", "X")).toList();
                    assertFalse(VANILLA_SHAPES.contains(shape), entry.getKey() + " takes a vanilla armour shape");
                }
            } else {
                List<String> ingredients = new ArrayList<>();
                recipe.getAsJsonArray("ingredients").forEach(e -> ingredients.add(e.getAsString()));
                ingredients.sort(String::compareTo);
                signature = "shapeless:" + ingredients;
            }
            String before = seen.put(signature, entry.getKey());
            assertTrue(before == null, entry.getKey() + " takes the same inputs as " + before);
        }
    }

    // ------------------------------------------------------------------------------------------

    private static void assertResult(JsonObject stack, String where) throws IOException {
        String id = stack.get("id").getAsString();
        if (!id.startsWith(MOD + ":")) {
            return;
        }
        assertTrue(Files.exists(ITEM_DEFINITIONS.resolve(path(id) + ".json")), where + ": no item " + id);
        if (!stack.has("components") || !stack.getAsJsonObject("components").has(MOD + ":outfit")) {
            return;
        }
        JsonObject outfit = stack.getAsJsonObject("components").getAsJsonObject(MOD + ":outfit");
        String livery = path(outfit.get("livery").getAsString());
        Path file = LIVERIES.resolve(livery + ".json");
        assertTrue(Files.exists(file), where + ": no livery " + livery);
        JsonArray cuts = read(file).getAsJsonObject().getAsJsonArray("cuts");
        assertTrue(cuts.toString().contains("\"" + id + "\""), where + ": " + livery + " does not admit " + id);
        int rank = outfit.has("rank") ? outfit.get("rank").getAsInt() : 0;
        assertTrue(rank >= 0 && rank <= Outfit.MAX_RANK, where + ": rank " + rank);
    }

    private static Map<String, JsonObject> recipes() throws IOException {
        Map<String, JsonObject> out = new TreeMap<>();
        try (Stream<Path> files = Files.list(RECIPES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                out.put(file.getFileName().toString(), read(file).getAsJsonObject());
            }
        }
        assertTrue(out.size() > 20, "the wardrobe recipes moved");
        return out;
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    private static JsonElement read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path));
    }
}
