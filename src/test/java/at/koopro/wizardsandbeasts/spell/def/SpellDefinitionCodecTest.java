package at.koopro.wizardsandbeasts.spell.def;

import at.koopro.wizardsandbeasts.spell.core.CastType;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON round-trip tests for {@link SpellDefinition}. Pure codec exercise — no
 * Minecraft bootstrap needed because we don't resolve registries here.
 *
 * <p>The real-world reload flow is covered by {@link SpellReloadListener},
 * which uses the same codec; once those tests run in a bootstrapped environment
 * the resource-resolution path can be tested end-to-end.
 */
class SpellDefinitionCodecTest {

    private static final Gson GSON = new Gson();

    @Test
    void minimalJson_loadsWithDefaults() {
        String json = """
                {
                  "displayName": "Test",
                  "category": "utility",
                  "cooldownTicks": 20,
                  "color": 16777215,
                  "castType": "self"
                }
                """;
        SpellDefinition def = parse(json);

        assertEquals("Test", def.displayName());
        assertEquals(SpellCategory.UTILITY, def.category());
        assertEquals(20, def.cooldownTicks());
        assertEquals(0.0f, def.baseDamage());
        assertEquals(16777215, def.color());
        assertEquals(CastType.SELF, def.castType());
        assertEquals(0.0f, def.range());
        assertFalse(def.disarms());
        assertEquals(List.of(), def.selfEffects());
        assertEquals(List.of(), def.targetEffects());
        assertEquals(SpellDefinition.SpellRequirementDef.Type.NONE, def.requirement().type());
    }

    @Test
    void allFields_populated_roundTrip() {
        String json = """
                {
                  "displayName": "Maxima",
                  "category": "combat",
                  "cooldownTicks": 80,
                  "baseDamage": 6.5,
                  "color": -65536,
                  "castType": "targeted",
                  "range": 12.0,
                  "knockback": 1.5,
                  "igniteSeconds": 4,
                  "explode": { "power": 2.5, "breaksBlocks": false },
                  "disarms": true,
                  "selfEffects": [
                    { "id": "minecraft:speed", "duration": 100, "amplifier": 0 }
                  ],
                  "targetEffects": [
                    { "id": "minecraft:slowness", "duration": 80, "amplifier": 1 }
                  ],
                  "sound": { "id": "minecraft:entity.blaze.shoot", "volume": 1.0, "pitch": 1.2 },
                  "requirement": {
                    "type": "proficiency",
                    "prerequisiteId": "stupefy",
                    "minProficiency": "proficient"
                  }
                }
                """;
        SpellDefinition def = parse(json);

        assertEquals(SpellCategory.COMBAT, def.category());
        assertEquals(CastType.TARGETED, def.castType());
        assertEquals(12.0f, def.range());
        assertEquals(1.5f, def.knockback());
        assertEquals(Optional.of(4), def.igniteSeconds());
        assertTrue(def.explode().isPresent());
        assertEquals(2.5f, def.explode().get().power());
        assertFalse(def.explode().get().breaksBlocks());
        assertTrue(def.disarms());
        assertEquals(1, def.selfEffects().size());
        assertEquals("minecraft", def.selfEffects().get(0).id().getNamespace());
        assertEquals("speed", def.selfEffects().get(0).id().getPath());
        assertEquals(100, def.selfEffects().get(0).duration());
        assertEquals(1, def.targetEffects().get(0).amplifier());
        assertTrue(def.sound().isPresent());
        assertEquals("minecraft:entity.blaze.shoot", def.sound().get().id().toString());

        var req = def.requirement();
        assertEquals(SpellDefinition.SpellRequirementDef.Type.PROFICIENCY, req.type());
        assertEquals(Optional.of("stupefy"), req.prerequisiteId());
        assertTrue(req.minProficiency().isPresent());
        assertEquals("PROFICIENT", req.minProficiency().get().name());

        // Encode-decode round trip should preserve all fields.
        JsonElement encoded = SpellDefinition.CODEC.encodeStart(JsonOps.INSTANCE, def).result().orElseThrow();
        SpellDefinition reparsed = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
        assertEquals(def, reparsed,
                "encode -> decode round trip must produce an equal SpellDefinition.");
    }

    @Test
    void unknownField_isAccepted() {
        // Forward-compat: extra JSON fields shouldn't break parsing.
        String json = """
                {
                  "displayName": "Test",
                  "category": "utility",
                  "cooldownTicks": 20,
                  "color": 0,
                  "castType": "self",
                  "futureField": "ignored"
                }
                """;
        SpellDefinition def = parse(json);
        assertEquals("Test", def.displayName());
    }

    @Test
    void requirementListsAreNonNullDefaults() {
        SpellDefinition def = parse("""
                {
                  "displayName": "X",
                  "category": "utility",
                  "cooldownTicks": 1,
                  "color": 0,
                  "castType": "self"
                }
                """);
        assertEquals(List.of(), def.selfEffects());
        assertEquals(List.of(), def.targetEffects());
    }

    @Test
    void loreApproximationSpells_parseFromDatapackFiles() throws IOException {
        SpellDefinition episkey = parseFile("src/main/resources/data/wizards_and_beasts/spells/episkey.json");
        SpellDefinition frigora = parseFile("src/main/resources/data/wizards_and_beasts/spells/frigora.json");
        SpellDefinition levicorpus = parseFile("src/main/resources/data/wizards_and_beasts/spells/levicorpus.json");
        SpellDefinition finiteIncantatem = parseFile(
                "src/main/resources/data/wizards_and_beasts/spells/finite_incantatem.json");

        assertEquals(CastType.SELF, episkey.castType());
        assertEquals(0, episkey.selfEffects().size());
        assertTrue(episkey.sound().isPresent());

        assertEquals(CastType.SELF, frigora.castType());
        assertEquals(0, frigora.selfEffects().size());
        assertTrue(frigora.sound().isPresent());

        assertEquals(CastType.TARGETED, levicorpus.castType());
        assertEquals(0, levicorpus.targetEffects().size());
        assertTrue(levicorpus.sound().isPresent());

        assertEquals(CastType.TARGETED, finiteIncantatem.castType());
        assertEquals(40, finiteIncantatem.cooldownTicks());
        assertEquals(SpellCategory.UTILITY, finiteIncantatem.category());
        assertEquals(Optional.of(SpellFamily.LIGHT), finiteIncantatem.spellFamily());
        assertEquals(0, finiteIncantatem.targetEffects().size());
        assertTrue(finiteIncantatem.sound().isPresent());
    }

    @Test
    void allDatapackSpells_parseAndMigratedHaveComponents() throws IOException {
        Path dir = Path.of("src/main/resources/data/wizards_and_beasts/spells");
        try (var stream = Files.list(dir)) {
            for (Path f : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                SpellDefinition def = parse(Files.readString(f)); // throws on any codec error
                assertFalse(def.displayName().isBlank(), "blank displayName in " + f);
            }
        }
        // Spot-check the Step-4 component migrations decode their effect lists.
        assertEquals(1, parseFile("src/main/resources/data/wizards_and_beasts/spells/nox.json")
                .effectComponents().size(), "nox should carry one dispel component");
        assertEquals(2, parseFile("src/main/resources/data/wizards_and_beasts/spells/episkey.json")
                .effectComponents().size(), "episkey should carry heal + dispel components");
        assertEquals(3, parseFile("src/main/resources/data/wizards_and_beasts/spells/frigora.json")
                .effectComponents().size(), "frigora should carry clear_fire + 2 apply_effect components");
        assertEquals(Optional.of(5), parseFile("src/main/resources/data/wizards_and_beasts/spells/incendio.json")
                .igniteSeconds(), "incendio keeps igniteSeconds in props");
    }

    private static SpellDefinition parse(String json) {
        JsonElement element = GSON.fromJson(json, JsonElement.class);
        var result = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, element);
        if (result.error().isPresent()) {
            throw new AssertionError("Codec parse failed: " + result.error().get().message());
        }
        return result.result().orElseThrow();
    }

    private static SpellDefinition parseFile(String relativePath) throws IOException {
        String json = Files.readString(Path.of(relativePath));
        return parse(json);
    }
}
