package at.koopro.wizardsandbeasts.brew;

import at.koopro.wizardsandbeasts.brew.def.BrewDefinition;
import at.koopro.wizardsandbeasts.brew.def.BrewingRecipeDefinition;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffect;
import at.koopro.wizardsandbeasts.brew.effect.BrewEffectEntry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped brews and recipes, checked for the two ways brewing fails silently.
 *
 * <p>Both are invisible in review and in play until somebody tries the potion:
 *
 * <ul>
 *   <li><b>An effect id that resolves to nothing.</b> {@code ApplyEffects.apply} looks each id up in
 *       the live registry and, on a miss, logs a warning and <em>skips that effect</em>. So a typo in
 *       a modded id does not crash, does not fail the reload, and just quietly removes part of a
 *       potion. This is the same failure shape {@code ShippedWandDefinitionJsonTest} guards for
 *       wands.</li>
 *   <li><b>An ingredient nothing drops.</b> A recipe naming an item with no loot table, no crafting
 *       recipe and no other source is a recipe no player can ever complete. There is no error: the
 *       cauldron simply never matches. {@code essence_of_dittany} is a live example — it is a
 *       registered canon item with no source at all, which is why Skele-Gro asks for {@code dittany}
 *       instead.</li>
 * </ul>
 */
class ShippedBrewDataTest {

    private static final Path DATA = Path.of("src", "main", "resources", "data", "wizards_and_beasts");
    private static final Path BREWS = DATA.resolve("brews");
    private static final Path RECIPES = DATA.resolve("brewing_recipes");
    private static final Path MOD_EFFECTS = Path.of("src", "main", "java", "at", "koopro",
            "wizardsandbeasts", "effect", "ModEffects.java");

    /** Where an item can come from. Loot tables cover blocks, mobs and chests alike. */
    private static final List<Path> SOURCE_ROOTS = List.of(
            Path.of("src", "generated", "resources", "data", "wizards_and_beasts", "loot_table"),
            DATA.resolve("loot_table"),
            Path.of("src", "generated", "resources", "data", "wizards_and_beasts", "recipe"),
            DATA.resolve("recipe"));

    /**
     * The signature potions this pass made real, and the component or modded effect that makes each
     * one more than a list of vanilla effects.
     *
     * <p>Pinned because the regression is a quiet one. Every one of these was once authored as
     * {@code apply_effects} over vanilla ids wearing a canon name — Veritaserum was Glowing plus
     * Weakness — and a potion in that state brews, bottles, drinks and looks entirely correct.
     */
    private static final Map<String, String> SIGNATURE_MARKERS = Map.of(
            "veritaserum", "truth_serum",
            "polyjuice_potion", "polyjuice_disguise",
            "felix_felicis", "felix_felicis",
            "wolfsbane_potion", "wizards_and_beasts:wolfsbane",
            "draught_of_living_death", "wizards_and_beasts:living_death",
            "amortentia", "wizards_and_beasts:infatuation");

    // ── effect ids ─────────────────────────────────────────────────────────

    /**
     * Every modded effect id a brew names is actually registered.
     *
     * <p>Read out of {@code ModEffects} rather than a live registry, because a unit test has no
     * registry: the source is where the names are declared, and a name that is not there cannot be
     * resolved at runtime either.
     */
    @Test
    void everyModdedEffectIdNamedByABrewIsRegistered() throws IOException {
        Set<String> registered = registeredModEffectNames();
        assertFalse(registered.isEmpty(), "found no MOB_EFFECTS.register calls in " + MOD_EFFECTS);

        List<String> unknown = new ArrayList<>();
        for (Map.Entry<String, BrewDefinition> entry : decodeBrews().entrySet()) {
            for (String id : moddedEffectIds(entry.getValue())) {
                if (!registered.contains(id.substring("wizards_and_beasts:".length()))) {
                    unknown.add(entry.getKey() + " -> " + id);
                }
            }
        }
        assertEquals(List.of(), unknown,
                "these brews name a wizards_and_beasts effect that is not registered; at runtime the "
                        + "id fails to resolve and the effect is silently dropped from the potion: "
                        + unknown);
    }

    // ── ingredients ────────────────────────────────────────────────────────

    /** Every modded ingredient a recipe asks for has some source in the shipped data. */
    @Test
    void everyModdedRecipeIngredientIsObtainable() throws IOException {
        String sources = readAllSources();
        assertFalse(sources.isBlank(), "found no loot tables or recipes to check against");

        List<String> unobtainable = new ArrayList<>();
        for (Map.Entry<String, BrewingRecipeDefinition> entry : decodeRecipes().entrySet()) {
            for (BrewingRecipeDefinition.IngredientEntry ingredient : entry.getValue().ingredients()) {
                String item = ingredient.item().toString();
                if (item.startsWith("wizards_and_beasts:") && !sources.contains('"' + item + '"')) {
                    unobtainable.add(entry.getKey() + " -> " + item);
                }
            }
        }
        assertEquals(List.of(), unobtainable,
                "these recipes ask for a mod item nothing drops or crafts, so the cauldron can never "
                        + "match them and the potion is unreachable: " + unobtainable);
    }

    /**
     * Brewing is what the mod's own flora is for.
     *
     * <p>Every recipe used to be built entirely out of vanilla items — rose bushes, poppies, phantom
     * membrane — while Herbology grew mandrake, mallowsweet, devil's snare, gillyweed and dittany
     * that no brew consumed. Asserted as a floor rather than per-recipe so ingredients stay tunable:
     * what must not come back is a roster where the plants and the potions are unrelated.
     */
    @Test
    void mostRecipesAreBuiltOnTheModsOwnIngredients() throws IOException {
        Map<String, BrewingRecipeDefinition> recipes = decodeRecipes();
        long withModIngredient = recipes.values().stream()
                .filter(r -> r.ingredients().stream()
                        .anyMatch(i -> i.item().toString().startsWith("wizards_and_beasts:")))
                .count();
        assertTrue(withModIngredient * 2 >= recipes.size(),
                "only " + withModIngredient + " of " + recipes.size() + " brewing recipes use any "
                        + "mod ingredient; Herbology's crops exist to be brewed with");
    }

    // ── the signature potions ──────────────────────────────────────────────

    /** None of the famous potions is a list of vanilla effects wearing a canon name. */
    @Test
    void everySignaturePotionIsMoreThanVanillaEffects() throws IOException {
        Map<String, BrewDefinition> brews = decodeBrews();
        List<String> fake = new ArrayList<>();
        for (Map.Entry<String, String> expected : SIGNATURE_MARKERS.entrySet()) {
            BrewDefinition def = brews.get(expected.getKey());
            assertTrue(def != null, "missing brew: " + expected.getKey());

            boolean hasComponent = def.components().stream()
                    .anyMatch(e -> e.component().type().getSerializedName().equals(expected.getValue()));
            boolean hasModdedEffect = moddedEffectIds(def).contains(expected.getValue());
            if (!hasComponent && !hasModdedEffect) {
                fake.add(expected.getKey() + " (wanted " + expected.getValue() + ")");
            }
        }
        assertEquals(List.of(), fake,
                "these signature potions are back to being plain vanilla effect lists: " + fake);
    }

    /** Wiggenweld and Mandrake Restoration predate components and must keep working untouched. */
    @Test
    void theTwoOriginalBrewsStillBakeFromTheirLegacyEffectsList() throws IOException {
        Map<String, BrewDefinition> brews = decodeBrews();
        for (String id : List.of("wiggenweld_potion", "mandrake_restoration_draught")) {
            BrewDefinition def = brews.get(id);
            assertTrue(def != null, "missing brew: " + id);
            assertTrue(def.components().isEmpty(),
                    id + " gained a components list; that is fine, but it was the proof that an "
                            + "un-migrated brew still works — migrate the proof to another one first");
            assertFalse(def.effects().isEmpty(), id + " has no legacy effects list left");

            Brew baked = def.toBrew("wizards_and_beasts:" + id);
            assertTrue(baked != null, id + " no longer bakes into a Brew");
            assertTrue(BrewEffectEntry.hasPhase(baked.components(),
                            at.koopro.wizardsandbeasts.brew.effect.BrewEffectPhase.ON_DRINK),
                    id + "'s legacy list did not become an on_drink component");
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /** Every {@code wizards_and_beasts:} effect id named by any apply_effects component of a brew. */
    private static List<String> moddedEffectIds(BrewDefinition def) {
        List<String> ids = new ArrayList<>();
        def.effects().stream()
                .map(e -> e.id().toString())
                .filter(id -> id.startsWith("wizards_and_beasts:"))
                .forEach(ids::add);
        for (BrewEffectEntry entry : def.components()) {
            if (entry.component() instanceof BrewEffect.ApplyEffects applyEffects) {
                applyEffects.effects().stream()
                        .map(spec -> spec.id().toString())
                        .filter(id -> id.startsWith("wizards_and_beasts:"))
                        .forEach(ids::add);
            }
        }
        return ids;
    }

    private static Set<String> registeredModEffectNames() throws IOException {
        Matcher m = Pattern.compile("MOB_EFFECTS\\.register\\(\\s*\"([a-z0-9_]+)\"")
                .matcher(Files.readString(MOD_EFFECTS));
        Set<String> names = new java.util.HashSet<>();
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private static String readAllSources() throws IOException {
        StringBuilder all = new StringBuilder();
        for (Path root : SOURCE_ROOTS) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                    all.append(Files.readString(file)).append('\n');
                }
            }
        }
        return all.toString();
    }

    private static Map<String, BrewDefinition> decodeBrews() throws IOException {
        return decode(BREWS, json -> BrewDefinition.CODEC.parse(JsonOps.INSTANCE, json));
    }

    private static Map<String, BrewingRecipeDefinition> decodeRecipes() throws IOException {
        return decode(RECIPES, json -> BrewingRecipeDefinition.CODEC.parse(JsonOps.INSTANCE, json));
    }

    private static <T> Map<String, T> decode(
            Path dir,
            java.util.function.Function<JsonElement, com.mojang.serialization.DataResult<T>> parse)
            throws IOException {
        assertTrue(Files.isDirectory(dir), "missing definition directory: " + dir);
        Map<String, T> out = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                out.put(path.getFileName().toString().replace(".json", ""),
                        parse.apply(json).getOrThrow(msg -> new AssertionError(path + ": " + msg)));
            }
        }
        assertFalse(out.isEmpty(), "no definitions found under " + dir);
        return out;
    }
}
