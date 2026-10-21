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
            "niffler", "bowtruckle", "phoenix", "mooncalf", "thestral", "dementor", "cornish_pixie");
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

    @Test
    void theFoodWebHasBothEnds() throws IOException {
        Map<String, EcologyProfile> profiles = load();
        long predators = profiles.values().stream().filter(p -> p.has(EcologyCapability.PREDATOR)).count();
        long prey = profiles.values().stream().filter(p -> p.has(EcologyCapability.PREY)).count();
        assertTrue(predators > 0 && prey > 0, "a food web needs hunters and hunted: " + predators + "/" + prey);
    }

    private static Map<String, EcologyProfile> load() throws IOException {
        Map<String, EcologyProfile> out = new HashMap<>();
        try (var files = Files.list(ECOLOGY)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String id = file.getFileName().toString().replace(".json", "");
                out.put(id, EcologyProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
                        .getOrThrow(msg -> new AssertionError(id + ": " + msg)));
            }
        }
        return out;
    }
}
