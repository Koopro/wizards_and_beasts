package at.koopro.wizardsandbeasts.creature;

import at.koopro.wizardsandbeasts.creature.ability.CreatureAbility;
import at.koopro.wizardsandbeasts.creature.ability.FireAffinity;
import at.koopro.wizardsandbeasts.creature.ability.OccamyChoranaptyxis;
import at.koopro.wizardsandbeasts.creature.ability.Tint;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON back-compat + ability-layer tests for {@link CreatureDefinition.CODEC}. Pure codec parsing — no
 * Minecraft bootstrap (the codec only touches {@code Identifier}/{@code StringRepresentable}). Proves the
 * verification gate: every shipped creature JSON still parses after the {@code abilities} field was added,
 * and {@link FireAffinity} decodes with the expected per-creature flags.
 */
class CreatureDefinitionCodecTest {

    private static final Gson GSON = new Gson();
    private static final Path CREATURES_DIR =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "creatures");

    @Test
    void everyShippedCreatureJson_stillParses() throws IOException {
        try (Stream<Path> files = Files.list(CREATURES_DIR)) {
            List<Path> jsons = files.filter(p -> p.toString().endsWith(".json")).toList();
            assertFalse(jsons.isEmpty(), "expected creature JSONs on disk");
            for (Path json : jsons) {
                CreatureDefinition def = parse(Files.readString(json), json.getFileName().toString());
                assertNotNull(def.abilities(), "abilities is never null (defaults to empty list)");
            }
        }
    }

    @Test
    void legacyJson_withoutAbilities_parsesWithEmptyList() {
        CreatureDefinition def = parse("""
                {
                  "id": "wizards_and_beasts:unicorn",
                  "bodyPlan": "QUADRUPED",
                  "locomotion": "GROUND",
                  "width": 1.0, "height": 1.6,
                  "maxHealth": 20.0, "movementSpeed": 0.3,
                  "model": "wizards_and_beasts:entity/unicorn",
                  "texture": "wizards_and_beasts:textures/entity/unicorn.png",
                  "animation": "wizards_and_beasts:geckolib/animations/entity/unicorn.animation.json"
                }
                """, "legacy");
        assertTrue(def.abilities().isEmpty(), "absent abilities -> empty list (back-compat)");
        assertTrue(def.dragon().isEmpty());
    }

    @Test
    void occamy_decodesTheSizeRangeItActuallyShips() {
        // The range is not decoration: it is the hitbox, so a datapack typo here changes what the
        // creature collides with. `calm_scale` is also the newest field on the record, and an
        // optional field that silently falls back to its default is the kind of thing that only
        // shows up in play.
        OccamyChoranaptyxis size = parse(read("occamy.json"), "occamy").abilities().stream()
                .filter(OccamyChoranaptyxis.class::isInstance)
                .map(OccamyChoranaptyxis.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the Occamy ships no choranaptyxis ability"));

        assertEquals(0.35f, size.minScale(), 1.0e-4f);
        assertEquals(2.2f, size.maxScale(), 1.0e-4f);
        assertEquals(1.0f, size.calmScale(), 1.0e-4f);
        assertTrue(size.rate() > 0, "a rate of zero would freeze it at whatever size it loaded at");
    }

    @Test
    void choranaptyxis_omittingCalmScaleFallsBackToTheNaturalBody() {
        CreatureDefinition def = parse("""
                {
                  "id": "wizards_and_beasts:occamy",
                  "bodyPlan": "SERPENTINE",
                  "locomotion": "GROUND",
                  "width": 1.7, "height": 1.9,
                  "maxHealth": 47.0, "movementSpeed": 0.23,
                  "model": "wizards_and_beasts:entity/occamy",
                  "texture": "wizards_and_beasts:textures/entity/occamy.png",
                  "animation": "wizards_and_beasts:geckolib/animations/entity/occamy.animation.json",
                  "abilities": [{ "type": "occamy_choranaptyxis" }]
                }
                """, "occamy-defaults");
        OccamyChoranaptyxis size = (OccamyChoranaptyxis) def.abilities().getFirst();
        assertEquals(1.0f, size.calmScale(), 1.0e-4f, "a creature with no stated calm size is its declared body");
    }

    @Test
    void salamander_decodesFireAffinityFlags() {
        FireAffinity fire = onlyFireAffinity(parse(read("salamander.json"), "salamander"));
        assertTrue(fire.fireImmune());
        assertTrue(fire.requiresFire());
        assertEquals(200, fire.dryGraceTicks());
        assertTrue(fire.regenInFire() > 0);
        assertTrue(fire.seekFireWhenDry());
        assertTrue(fire.emberParticles());
        assertFalse(fire.igniteMeleeAttackers());
    }

    @Test
    void ashwinder_ignitesAttackersButDoesNotRequireFire() {
        FireAffinity fire = onlyFireAffinity(parse(read("ashwinder.json"), "ashwinder"));
        assertTrue(fire.fireImmune());
        assertTrue(fire.igniteMeleeAttackers());
        assertTrue(fire.emberParticles());
        assertFalse(fire.requiresFire());
        assertFalse(fire.seekFireWhenDry());
    }

    @Test
    void fireCrab_isFireImmuneOnly() {
        FireAffinity fire = onlyFireAffinity(parse(read("fire_crab.json"), "fire_crab"));
        assertTrue(fire.fireImmune());
        assertFalse(fire.requiresFire());
        assertFalse(fire.igniteMeleeAttackers());
        assertFalse(fire.seekFireWhenDry());
        assertFalse(fire.emberParticles());
    }

    @Test
    void fireAffinity_roundTripIsIdempotent() {
        CreatureDefinition def = parse(read("salamander.json"), "salamander");
        JsonElement encoded = CreatureDefinition.CODEC.encodeStart(JsonOps.INSTANCE, def).result().orElseThrow();
        CreatureDefinition reparsed = CreatureDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
        assertEquals(def, reparsed, "encode -> decode must round-trip");
    }

    @Test
    void signatureAndCommonAbilities_dispatchToCorrectTypes() {
        assertAbilityType("nundu.json", CreatureAbility.Type.NUNDU_PESTILENCE);
        assertAbilityType("lethifold.json", CreatureAbility.Type.LETHIFOLD_SMOTHER);
        assertAbilityType("boggart.json", CreatureAbility.Type.BOGGART_DREAD);
        assertAbilityType("thunderbird.json", CreatureAbility.Type.THUNDERBIRD_STORM);
        assertAbilityType("fwooper.json", CreatureAbility.Type.FWOOPER_SONG);
        assertAbilityType("acromantula.json", CreatureAbility.Type.WEB_SNARE);
        assertAbilityType("grindylow.json", CreatureAbility.Type.WATER_AFFINITY);
        assertAbilityType("unicorn.json", CreatureAbility.Type.HEAL_AURA);
        assertAbilityType("graphorn.json", CreatureAbility.Type.SPELL_RESIST);
        assertAbilityType("erumpent.json", CreatureAbility.Type.EXPLOSIVE_HORN);
        assertAbilityType("jobberknoll.json", CreatureAbility.Type.DEATH_CRY);
        assertAbilityType("ramora.json", CreatureAbility.Type.ANCHOR);
        assertAbilityType("occamy.json", CreatureAbility.Type.OCCAMY_CHORANAPTYXIS);
        assertAbilityType("fire_crab.json", CreatureAbility.Type.FLAME_BURST);
        assertAbilityType("ashwinder.json", CreatureAbility.Type.EMBER_TRAIL);
        assertAbilityType("kneazle.json", CreatureAbility.Type.DANGER_SENSE);
        // New ability pass (2026-06-29): tint, evasion, pack_tactics, damage_reduction, spore_cloud,
        // frenzy, dive_bomb + the jarvey/sphinx signatures.
        assertAbilityType("ashwinder.json", CreatureAbility.Type.TINT);
        assertAbilityType("demiguise.json", CreatureAbility.Type.EVASION);
        assertAbilityType("quintaped.json", CreatureAbility.Type.PACK_TACTICS);
        assertAbilityType("graphorn.json", CreatureAbility.Type.DAMAGE_REDUCTION);
        assertAbilityType("glumbumble.json", CreatureAbility.Type.SPORE_CLOUD);
        assertAbilityType("werewolf.json", CreatureAbility.Type.FRENZY);
        assertAbilityType("griffin.json", CreatureAbility.Type.DIVE_BOMB);
        assertAbilityType("jarvey.json", CreatureAbility.Type.JARVEY_JINX);
        assertAbilityType("sphinx.json", CreatureAbility.Type.SPHINX_RIDDLE);
    }

    @Test
    void tint_parsesHexColorAndPulse() {
        Tint tint = parse(read("ashwinder.json"), "ashwinder").abilities().stream()
                .filter(Tint.class::isInstance).map(Tint.class::cast).findFirst()
                .orElseThrow(() -> new AssertionError("expected a Tint ability"));
        assertEquals(0xFFFF6A00, tint.color(), "#RRGGBB -> opaque ARGB");
        assertTrue(tint.pulse());
        assertEquals(0xFFFFD000, tint.pulseColor().orElseThrow());
    }

    @Test
    void multiAbilityCreature_decodesEachEntry() {
        CreatureDefinition nundu = parse(read("nundu.json"), "nundu");
        assertEquals(3, nundu.abilities().size(), "nundu carries pestilence + enrage + dread");
    }

    private static void assertAbilityType(String fileName, CreatureAbility.Type expected) {
        CreatureDefinition def = parse(read(fileName), fileName);
        assertTrue(def.abilities().stream().anyMatch(a -> a.type() == expected),
                fileName + " should declare a " + expected + " ability");
    }

    private static FireAffinity onlyFireAffinity(CreatureDefinition def) {
        return def.abilities().stream()
                .filter(FireAffinity.class::isInstance)
                .map(FireAffinity.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a FireAffinity ability"));
    }

    private static String read(String fileName) {
        try {
            return Files.readString(CREATURES_DIR.resolve(fileName));
        } catch (IOException e) {
            throw new AssertionError("could not read " + fileName, e);
        }
    }

    private static CreatureDefinition parse(String json, String label) {
        JsonElement element = GSON.fromJson(json, JsonElement.class);
        var result = CreatureDefinition.CODEC.parse(JsonOps.INSTANCE, element);
        if (result.error().isPresent()) {
            throw new AssertionError("Creature codec parse failed for " + label + ": " + result.error().get().message());
        }
        return result.result().orElseThrow();
    }
}
