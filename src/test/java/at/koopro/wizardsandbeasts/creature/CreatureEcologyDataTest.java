package at.koopro.wizardsandbeasts.creature;

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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world should say "these creatures live here", not "these creatures have biome tags".
 *
 * <p>Three things had drifted apart: what the Bestiary teaches, where creatures actually spawn, and
 * what the lore says about the habitat. The Runespoor, native to Burkina Faso in canon, spawned in
 * every temperate forest in the world; the Matagot, which haunts wizarding buildings, spawned in the
 * one habitat the lore rules out; and the near-extinct Golden Snidget was as common as anything else
 * with wings. These checks are the guard rails for the repair, and they read the shipped data rather
 * than a copy of it.
 */
class CreatureEcologyDataTest {

    private static final Path DATA =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts");
    private static final Path BESTIARY = DATA.resolve("bestiary/entries");
    private static final Path BIOME_MODIFIERS = DATA.resolve("neoforge/biome_modifier");

    /**
     * Spawning things the Bestiary is right not to carry.
     *
     * <p>The Bestiary is a book about beasts. A Gringotts teller is a goblin, and goblins are a people
     * in this mod with a heritage, a skill web and a bank — filing one under magical fauna would be
     * the same category error the heritage layer exists to avoid. It keeps its cave spawn because the
     * mod generates no Gringotts and that spawn is currently the only way to meet one.
     */
    private static final Set<String> NOT_FAUNA = Set.of("goblin_teller");

    private record Spawn(String creature, List<String> biomes, int weight, int maxCount, String file) {}

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static Map<String, JsonObject> bestiary() throws IOException {
        Map<String, JsonObject> entries = new TreeMap<>();
        try (Stream<Path> files = Files.walk(BESTIARY)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString();
                entries.put(name.substring(0, name.length() - ".json".length()), read(file));
            }
        }
        return entries;
    }

    private static List<Spawn> spawns() throws IOException {
        List<Spawn> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(BIOME_MODIFIERS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject modifier = read(file);
                if (!"neoforge:add_spawns".equals(
                        modifier.has("type") ? modifier.get("type").getAsString() : "")) {
                    continue;
                }
                List<String> biomes = new ArrayList<>();
                JsonElement biomeField = modifier.get("biomes");
                if (biomeField != null && biomeField.isJsonArray()) {
                    for (JsonElement biome : biomeField.getAsJsonArray()) {
                        biomes.add(biome.getAsString());
                    }
                } else if (biomeField != null) {
                    biomes.add(biomeField.getAsString());
                }

                JsonElement spawners = modifier.get("spawners");
                List<JsonObject> each = new ArrayList<>();
                if (spawners != null && spawners.isJsonArray()) {
                    for (JsonElement entry : spawners.getAsJsonArray()) {
                        each.add(entry.getAsJsonObject());
                    }
                } else if (spawners != null) {
                    each.add(spawners.getAsJsonObject());
                }

                for (JsonObject spawner : each) {
                    String type = spawner.get("type").getAsString();
                    out.add(new Spawn(
                            type.substring(type.indexOf(':') + 1),
                            biomes,
                            spawner.get("weight").getAsInt(),
                            spawner.has("maxCount") ? spawner.get("maxCount").getAsInt() : 1,
                            file.getFileName().toString()));
                }
            }
        }
        return out;
    }

    // ── the five checks the ecology brief asks for ──────────────────────────────────────────────

    @Test
    void everySpawnableCreatureIsInTheBestiary() throws IOException {
        Set<String> documented = bestiary().keySet();
        List<String> undocumented = new ArrayList<>();
        for (Spawn spawn : spawns()) {
            if (!documented.contains(spawn.creature()) && !NOT_FAUNA.contains(spawn.creature())) {
                undocumented.add(spawn.creature() + " (" + spawn.file() + ")");
            }
        }
        assertTrue(undocumented.isEmpty(),
                "a player can meet these and the Bestiary has never heard of them: " + undocumented);
    }

    /**
     * The converse of the exemption above, so it cannot quietly become a dumping ground: anything
     * named there must actually still be spawning, or the exemption is stale.
     */
    @Test
    void theNonFaunaExemptionIsStillEarned() throws IOException {
        Set<String> spawning = new HashSet<>();
        for (Spawn spawn : spawns()) {
            spawning.add(spawn.creature());
        }
        List<String> stale = new ArrayList<>();
        for (String exempt : NOT_FAUNA) {
            if (!spawning.contains(exempt)) {
                stale.add(exempt);
            }
        }
        assertTrue(stale.isEmpty(), "these no longer spawn and need no exemption: " + stale);
    }

    @Test
    void everyBestiaryEntryNamesItsOwnCreature() throws IOException {
        List<String> broken = new ArrayList<>();
        bestiary().forEach((name, entry) -> {
            if (!entry.has("entityType")) {
                broken.add(name + ": no entityType");
                return;
            }
            String entityType = entry.get("entityType").getAsString();
            if (!entityType.startsWith("wizards_and_beasts:")) {
                broken.add(name + ": entityType is not this mod's -> " + entityType);
            } else if (!entityType.substring(entityType.indexOf(':') + 1).equals(name)) {
                broken.add(name + ": entityType points elsewhere -> " + entityType);
            }
        });
        assertTrue(broken.isEmpty(), "Bestiary entries with an invalid creature id: " + broken);
    }

    /**
     * Biome references must be well formed, and any tag must be one somebody ships. A typo here does
     * not fail a build — it silently produces a creature that never spawns anywhere.
     */
    @Test
    void everyBiomeReferenceIsWellFormed() throws IOException {
        List<String> malformed = new ArrayList<>();
        for (Spawn spawn : spawns()) {
            if (spawn.biomes().isEmpty()) {
                malformed.add(spawn.file() + ": no biomes at all");
            }
            for (String biome : spawn.biomes()) {
                String bare = biome.startsWith("#") ? biome.substring(1) : biome;
                if (!bare.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                    malformed.add(spawn.file() + ": '" + biome + "' is not a namespaced id");
                }
            }
        }
        assertTrue(malformed.isEmpty(), "impossible biome references: " + malformed);
    }

    @Test
    void noCreatureHasTwoSpawnRules() throws IOException {
        Map<String, List<String>> byCreature = new TreeMap<>();
        for (Spawn spawn : spawns()) {
            byCreature.computeIfAbsent(spawn.creature(), c -> new ArrayList<>()).add(spawn.file());
        }
        List<String> duplicated = new ArrayList<>();
        byCreature.forEach((creature, files) -> {
            if (files.size() > 1) {
                duplicated.add(creature + " -> " + files);
            }
        });
        assertTrue(duplicated.isEmpty(),
                "two rules for one creature stack rather than replace, so the rarer one is a lie: "
                        + duplicated);
    }

    /**
     * Rarity has to agree with itself. A creature the Ministry rates XXXX or XXXXX is dangerous or
     * rare enough that meeting a herd of them is a contradiction, and this is the check the audit
     * wanted: rarity expressed as habitat and headcount, not only as a small weight.
     */
    @Test
    void dangerRatingAgreesWithHowOftenYouMeetOne() throws IOException {
        Map<String, JsonObject> entries = bestiary();
        List<String> contradictions = new ArrayList<>();
        for (Spawn spawn : spawns()) {
            JsonObject entry = entries.get(spawn.creature());
            if (entry == null || !entry.has("mmRating")) {
                continue;
            }
            int rating = entry.get("mmRating").getAsInt();
            if (rating >= 4 && spawn.weight() > 4) {
                contradictions.add(spawn.creature() + ": rated " + rating
                        + " but spawn weight " + spawn.weight());
            }
            if (rating >= 4 && spawn.maxCount() > 2) {
                contradictions.add(spawn.creature() + ": rated " + rating
                        + " but arrives in groups of " + spawn.maxCount());
            }
        }
        assertTrue(contradictions.isEmpty(),
                "danger rating and spawn frequency disagree: " + contradictions);
    }

    /**
     * The three habitats this pass corrected, pinned to the reasoning rather than to a number.
     *
     * <p>Each was wrong in the same way — a broad vanilla tag standing in for a habitat the lore is
     * specific about — and each would revert just as silently as it drifted.
     */
    @Test
    void theCorrectedHabitatsStayCorrected() throws IOException {
        Map<String, List<String>> biomes = new HashMap<>();
        for (Spawn spawn : spawns()) {
            biomes.put(spawn.creature(), spawn.biomes());
        }

        assertTrue(!biomes.getOrDefault("runespoor", List.of()).contains("#minecraft:is_forest"),
                "the Runespoor is native to Burkina Faso, not to every temperate wood");
        assertTrue(!biomes.getOrDefault("matagot", List.of()).contains("#minecraft:is_forest"),
                "the Matagot haunts wizarding buildings; woodland is the habitat canon rules out."
                        + " Its real gate is BeastSpawnHandler's stonework check, so the biome here is"
                        + " deliberately wide — but it must not be filed as a forest creature");
        assertTrue(!biomes.getOrDefault("golden_snidget", List.of()).contains("#minecraft:is_forest"),
                "the Golden Snidget was hunted to the edge of extinction and is a protected species;"
                        + " it should not be in every forest on the map");
    }
}
