package at.koopro.wizardsandbeasts.creature.ecology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped ecology data, joined against the creatures it describes. A profile that names a creature nobody
 * registers, or gives a body a capability it does not have, parses fine and does nothing — the class of silent
 * failure this repository has met before (tasks/lessons.md, "Two data files with a shared vocabulary").
 */
class EcologyProfileDataTest {

    private static final Path ECOLOGY = Path.of("src/main/resources/data/wizards_and_beasts/creature_ecology");
    private static final Path CREATURES = Path.of("src/main/resources/data/wizards_and_beasts/creatures");
    /** Bespoke Java creatures: no creatures/*.json, registered in code. */
    private static final Set<String> BESPOKE = Set.of(
            "niffler", "bowtruckle", "phoenix", "mooncalf", "thestral", "dementor", "cornish_pixie",
            "runespoor", "hidebehind", "augurey", "streeler");
    /** The high-priority archetypes of the 2026-10-06 ecology pass; each must declare an ecology. */
    private static final List<String> PRIORITY = List.of(
            "niffler", "bowtruckle", "phoenix", "unicorn", "demiguise", "mooncalf", "hippogriff", "thestral",
            "dementor", "basilisk", "acromantula", "centaur", "merperson", "grindylow", "cornish_pixie", "zouwu",
            "occamy", "hungarian_horntail");

    @Test
    void everyProfileParsesAndNamesARealCreature() throws IOException {
        Map<String, EcologyProfile> profiles = load();
        assertFalse(profiles.isEmpty());
        for (String id : profiles.keySet()) {
            assertTrue(BESPOKE.contains(id) || Files.exists(CREATURES.resolve(id + ".json")),
                    "creature_ecology/" + id + ".json describes a creature that does not exist");
        }
        for (String id : PRIORITY) {
            assertTrue(profiles.containsKey(id), "priority creature " + id + " has no ecology");
        }
    }

    @Test
    void aBodyDeclaresOnlyTheMovementItHasAndAPredatorCanBite() throws IOException {
        for (Map.Entry<String, EcologyProfile> e : load().entrySet()) {
            Path definition = CREATURES.resolve(e.getKey() + ".json");
            if (!Files.exists(definition)) {
                continue; // bespoke: its class owns movement and attack
            }
            JsonObject def = JsonParser.parseString(Files.readString(definition)).getAsJsonObject();
            String locomotion = def.get("locomotion").getAsString();
            EcologyProfile profile = e.getValue();
            assertEquals(locomotion.equals("AQUATIC"), profile.has(EcologyCapability.AQUATIC),
                    e.getKey() + ": aquatic must match locomotion " + locomotion);
            assertEquals(locomotion.equals("FLYING"), profile.has(EcologyCapability.FLYING),
                    e.getKey() + ": flying must match locomotion " + locomotion);
            double damage = def.has("attackDamage") ? def.get("attackDamage").getAsDouble() : 0.0;
            if (profile.has(EcologyCapability.PREDATOR) || profile.has(EcologyCapability.AGGRESSIVE)) {
                assertTrue(damage > 0, e.getKey() + " hunts or attacks but has no attackDamage to do it with");
            }
        }
    }

    @Test
    void contradictionsAreRefusedAtLoad() {
        String json = "{\"capabilities\":[\"human-avoidant\",\"human-friendly\"]}";
        assertTrue(EcologyProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isError());
        String lonelyAggression = "{\"capabilities\":[\"aggressive\"]}";
        assertTrue(EcologyProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(lonelyAggression)).isError());
    }

    /** BR-2's exit: every creature on the roster has an ecology, so none of them is left out of daily life. */
    @Test
    void everyCreatureOnTheRosterHasAnEcology() throws IOException {
        Map<String, EcologyProfile> profiles = load();
        List<String> missing = new java.util.ArrayList<>();
        try (var files = Files.list(CREATURES)) {
            files.map(f -> f.getFileName().toString().replace(".json", "")).filter(id -> !profiles.containsKey(id))
                    .forEach(missing::add);
        }
        BESPOKE.stream().filter(id -> !profiles.containsKey(id)).forEach(missing::add);
        assertTrue(missing.isEmpty(), "creatures with no creature_ecology profile: " + missing);
    }

    /** An archetype nobody extends is dead data. */
    @Test
    void everyArchetypeIsUsed() throws IOException {
        Set<String> used = new java.util.HashSet<>();
        Set<String> archetypes = new java.util.HashSet<>();
        try (var files = Files.walk(ECOLOGY)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (json.has("extends")) {
                    used.add(json.get("extends").getAsString());
                }
                if (file.getParent().getFileName().toString().equals("_archetypes")) {
                    archetypes.add(file.getFileName().toString().replace(".json", ""));
                }
            }
        }
        archetypes.removeAll(used);
        assertTrue(archetypes.isEmpty(), "archetypes nothing extends: " + archetypes);
    }

    /** A diet is shown through hunger, and every hungry creature has something it can eat. */
    @Test
    void everyHungerCanBeAnswered() throws IOException {
        for (Map.Entry<String, EcologyProfile> e : load().entrySet()) {
            EcologyProfile p = e.getValue();
            if (p.needs(EcologyNeed.HUNGER)) {
                assertTrue(p.foragesBlocks() || p.has(EcologyCapability.PREDATOR) || p.has(EcologyCapability.SCAVENGER)
                        || p.foodItems().isPresent(), e.getKey() + " gets hungry with nothing to eat");
            }
        }
    }

    @Test
    void theFoodWebHasBothEnds() throws IOException {
        Map<String, EcologyProfile> profiles = load();
        long predators = profiles.values().stream().filter(p -> p.has(EcologyCapability.PREDATOR)).count();
        long prey = profiles.values().stream().filter(p -> p.has(EcologyCapability.PREY)).count();
        assertTrue(predators > 0 && prey > 0, "a food web needs hunters and hunted: " + predators + "/" + prey);
    }

    /** Every profile, resolved exactly as the reload listener does (archetypes included). */
    static Map<String, EcologyProfile> load() throws IOException {
        Map<String, com.google.gson.JsonElement> raw = new HashMap<>();
        try (var files = Files.walk(ECOLOGY)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String id = ECOLOGY.relativize(file).toString().replace('\\', '/').replace(".json", "");
                raw.put(id, JsonParser.parseString(Files.readString(file)));
            }
        }
        Map<String, EcologyProfile> out = new HashMap<>();
        EcologyProfileFiles.resolve(raw).forEach((id, result) ->
                out.put(id, result.getOrThrow(msg -> new AssertionError(id + ": " + msg))));
        return out;
    }
}
