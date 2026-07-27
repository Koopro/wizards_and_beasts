package at.koopro.wizardsandbeasts.wand.resonance;

import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every wizard must be able to walk out of Ollivander's with a wand on their first visit.
 *
 * <p>The trial offers three wands drawn from entries the wizard's level allows, and
 * {@code OllivanderTrialMenu.pickTrials} guarantees the best-scoring one is among them. That guarantee
 * is only worth anything if some starter entry actually favours the wizard's wood: an unfavoured wood
 * scores {@link WandResonanceSystem#UNFAVOURED_WOOD_AFFINITY}, which leaves the weighted total hovering
 * either side of the match threshold. So the pool must cover every heritage variant.
 */
class OllivanderPoolCoverageTest {

    private static final Path DATA = Path.of("src", "main", "resources", "data", "wizards_and_beasts");
    private static final Path POOL = DATA.resolve(Path.of("ollivander_pool", "standard_pool.json"));
    private static final Path WOODS = DATA.resolve(Path.of("wizards_and_beasts", "wand_woods"));

    /** Wood id → the personality words it favours. */
    private static Map<String, Set<String>> woodAffinities() throws IOException {
        Map<String, Set<String>> affinities = new HashMap<>();
        try (var files = Files.list(WOODS)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                Set<String> traits = new TreeSet<>();
                root.getAsJsonArray("personality_affinity").forEach(el -> traits.add(el.getAsString()));
                String id = path.getFileName().toString().replace(".json", "");
                affinities.put(id, traits);
            }
        }
        return affinities;
    }

    /** Wood ids offered to a wizard who has not levelled up yet. */
    private static List<String> starterWoods() throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(POOL)).getAsJsonObject();
        List<String> woods = new ArrayList<>();
        for (var element : root.getAsJsonArray("entries")) {
            JsonObject entry = element.getAsJsonObject();
            int minLevel = entry.has("minimum_player_level") ? entry.get("minimum_player_level").getAsInt() : 0;
            if (minLevel == 0) {
                woods.add(entry.get("wood_key").getAsString().split(":", 2)[1]);
            }
        }
        return woods;
    }

    @Test
    void everyWoodInThePoolIsDefined() throws IOException {
        Map<String, Set<String>> woods = woodAffinities();
        JsonObject root = JsonParser.parseString(Files.readString(POOL)).getAsJsonObject();
        List<String> undefined = new ArrayList<>();
        for (var element : root.getAsJsonArray("entries")) {
            String wood = element.getAsJsonObject().get("wood_key").getAsString().split(":", 2)[1];
            if (!woods.containsKey(wood)) {
                undefined.add(wood);
            }
        }
        assertTrue(undefined.isEmpty(), "pool offers woods with no definition (they score 0): " + undefined);
    }

    @Test
    void everyHeritageVariantHasAFavouredStarterWand() throws IOException {
        Map<String, Set<String>> woods = woodAffinities();
        List<String> starters = starterWoods();
        assertFalse(starters.isEmpty(), "no starter entries in the Ollivander pool");

        List<String> unserved = new ArrayList<>();
        for (HeritageVariant variant : HeritageVariant.values()) {
            Set<String> traits = WandResonanceSystem.traitsOf(variant);
            boolean served = starters.stream().anyMatch(wood ->
                    woods.getOrDefault(wood, Set.of()).stream().anyMatch(traits::contains));
            if (!served) {
                unserved.add(variant.name() + " " + WizardPersonality.of(variant));
            }
        }
        assertTrue(unserved.isEmpty(),
                "no level-0 Ollivander entry favours these wizards, so their first visit may leave them "
                        + "wandless:\n" + String.join("\n", unserved));
    }
}
