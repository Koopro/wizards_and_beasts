package at.koopro.wizardsandbeasts.creature.ecology;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static at.koopro.wizardsandbeasts.creature.ecology.EcologyCapability.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Archetype resolution and the new profile fields, on hand-written JSON. */
class EcologyProfileFilesTest {

    private static Map<String, DataResult<EcologyProfile>> resolve(String... pairs) {
        Map<String, JsonElement> files = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            files.put(pairs[i], JsonParser.parseString(pairs[i + 1]));
        }
        return EcologyProfileFiles.resolve(files);
    }

    private static EcologyProfile ok(DataResult<EcologyProfile> result) {
        return result.getOrThrow(AssertionError::new);
    }

    @Test
    void aFileExtendsItsArchetypeAndItsOwnFieldsWin() {
        var out = resolve(
                "_archetypes/grazer_herd", """
                        {"capabilities":["prey","herd","grazer","diurnal"],"needs":{"hunger":24000,"thirst":12000},
                         "herd_radius":10,"personality":{"boldness":[0.1,0.3]}}""",
                "mooncalf", """
                        {"extends":"grazer_herd","without":["diurnal"],"capabilities":["nocturnal","human-avoidant"],
                         "needs":{"thirst":6000},"herd_radius":8}""");
        assertFalse(out.containsKey("_archetypes/grazer_herd"), "an archetype is not a creature");
        EcologyProfile mooncalf = ok(out.get("mooncalf"));
        assertTrue(mooncalf.has(PREY) && mooncalf.has(HERD) && mooncalf.has(GRAZER) && mooncalf.has(NOCTURNAL));
        assertTrue(mooncalf.has(HUMAN_AVOIDANT));
        assertFalse(mooncalf.has(DIURNAL), "removed with without");
        assertEquals(8.0, mooncalf.herdRadius());
        assertEquals(Map.of(EcologyNeed.HUNGER, 24000, EcologyNeed.THIRST, 6000), mooncalf.needs(), "merged key by key");
        assertEquals(0.3f, mooncalf.personalityRanges().get(EcologyPersonality.Axis.BOLDNESS).max(), 1e-6);
    }

    @Test
    void archetypesChainAndBrokenChainsAreReported() {
        var out = resolve(
                "_archetypes/animal", "{\"capabilities\":[\"grooming\"]}",
                "_archetypes/grazer", "{\"extends\":\"animal\",\"capabilities\":[\"grazer\"],\"needs\":{\"hunger\":20000}}",
                "moke", "{\"extends\":\"grazer\",\"capabilities\":[\"prey\"]}",
                "orphan", "{\"extends\":\"nothing\",\"capabilities\":[\"prey\"]}",
                "_archetypes/loop", "{\"extends\":\"loop\",\"capabilities\":[\"prey\"]}",
                "looper", "{\"extends\":\"loop\",\"capabilities\":[\"prey\"]}");
        EcologyProfile moke = ok(out.get("moke"));
        assertTrue(moke.has(GROOMING) && moke.has(GRAZER) && moke.has(PREY));
        assertTrue(out.get("orphan").isError());
        assertTrue(out.get("looper").isError());
    }

    @Test
    void newFieldsParseAndContradictionsAreRefused() {
        EcologyProfile cat = ok(EcologyProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"capabilities":["predator","solitary","crepuscular"],"hunt_style":"stalk","personal_space":20,
                 "needs":{"hunger":30000},"food_items":"#minecraft:meat"}""")));
        assertEquals(EcologyProfile.HuntStyle.STALK, cat.huntStyle());
        assertEquals(20.0, cat.personalSpace());
        assertTrue(cat.foodItems().isPresent());

        String[] refused = {
                "{\"capabilities\":[\"nocturnal\",\"diurnal\"]}",
                "{\"capabilities\":[\"solitary\",\"herd\"]}",
                "{\"capabilities\":[\"cold-adapted\",\"warmth-loving\"]}",
                "{\"capabilities\":[\"aquatic\"],\"needs\":{\"thirst\":12000}}",
                "{\"capabilities\":[\"prey\"],\"needs\":{\"hunger\":12000}}",
                "{\"capabilities\":[\"grazer\"]}",
                "{\"capabilities\":[\"prey\"],\"needs\":{\"social\":6000}}",
                "{\"capabilities\":[\"prey\"],\"needs\":{\"curiosity\":6000}}",
                "{\"capabilities\":[\"prey\"],\"needs\":{\"magic\":6000}}",
                "{\"capabilities\":[\"prey\"],\"personality\":{\"boldness\":[0.8,0.2]}}",
        };
        for (String json : refused) {
            assertTrue(EcologyProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isError(),
                    "should be refused: " + json);
        }
    }
}
