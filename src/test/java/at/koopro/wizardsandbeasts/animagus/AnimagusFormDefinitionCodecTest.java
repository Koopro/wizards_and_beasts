package at.koopro.wizardsandbeasts.animagus;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Codec tests for {@link AnimagusFormDefinition}. Pure parsing — no Minecraft bootstrap, since the
 * codec only touches {@code Identifier} and plain enums.
 *
 * <p>The two cross-field invariants are the point of these tests. A form that declares
 * {@code FLIGHT} without physics, or ships physics it never uses, parses fine field-by-field and
 * only misbehaves once a player transforms — which is exactly the class of bug that has to fail at
 * datapack load instead.
 */
class AnimagusFormDefinitionCodecTest {

    private static final Path FORMS_DIR =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "animagus_forms");

    @Test
    void everyShippedFormJson_roundTrips() throws IOException {
        try (Stream<Path> files = Files.list(FORMS_DIR)) {
            List<Path> jsons = files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
            assertEquals(4, jsons.size(), "expected rat, cat, dog and falcon on disk");

            for (Path json : jsons) {
                String name = json.getFileName().toString();
                JsonElement source = JsonParser.parseString(Files.readString(json));

                AnimagusFormDefinition decoded = AnimagusFormDefinition.CODEC
                        .parse(JsonOps.INSTANCE, source)
                        .getOrThrow(msg -> new AssertionError(name + " failed to decode: " + msg));

                JsonElement reencoded = AnimagusFormDefinition.CODEC
                        .encodeStart(JsonOps.INSTANCE, decoded)
                        .getOrThrow(msg -> new AssertionError(name + " failed to encode: " + msg));

                AnimagusFormDefinition again = AnimagusFormDefinition.CODEC
                        .parse(JsonOps.INSTANCE, reencoded)
                        .getOrThrow(msg -> new AssertionError(name + " failed to re-decode: " + msg));

                assertEquals(decoded, again, name + " did not survive a codec round trip");
            }
        }
    }

    @Test
    void falcon_isTheOnlyFlier_andCarriesItsPhysics() throws IOException {
        AnimagusFormDefinition falcon = parseShipped("falcon.json");
        assertTrue(falcon.hasCapability(AnimagusCapability.FLIGHT));
        assertTrue(falcon.flight().isPresent(), "a flier must carry flight physics");
        assertEquals(0.18, falcon.flight().orElseThrow().stallSpeed());
        assertEquals(AnimagusCanonTier.POTTERMORE, falcon.canonTier());

        for (String ground : List.of("rat.json", "cat.json", "dog.json")) {
            AnimagusFormDefinition def = parseShipped(ground);
            assertFalse(def.hasCapability(AnimagusCapability.FLIGHT), ground + " must not fly");
            assertTrue(def.flight().isEmpty(), ground + " must not carry flight physics");
            assertEquals(AnimagusCanonTier.BOOK, def.canonTier());
        }
    }

    @Test
    void attributeKeys_areRealIdentifiers() throws IOException {
        AnimagusFormDefinition dog = parseShipped("dog.json");
        assertEquals(-4.0, dog.attributes().get(Identifier.withDefaultNamespace("max_health")));
        assertEquals(0.4, dog.attributes().get(Identifier.withDefaultNamespace("step_height")));
    }

    @Test
    void flightCapability_withoutFlightBlock_isRejected() {
        DataResult<AnimagusFormDefinition> result = parse("""
                {
                  "canon_tier": "pottermore",
                  "model": "wizards_and_beasts:geckolib/models/entity/form/falcon.geo.json",
                  "texture": "wizards_and_beasts:textures/entity/form/falcon.png",
                  "animations": "wizards_and_beasts:geckolib/animations/entity/form/falcon.animation.json",
                  "animation_map": {
                    "idle": "a", "walk": "b", "run": "c", "jump": "d", "hurt": "e",
                    "glide": "f", "flap": "g"
                  },
                  "hitbox": { "width": 0.5, "height": 0.5, "eye_height": 0.4 },
                  "attributes": { "minecraft:scale": 0.4 },
                  "capabilities": ["FLIGHT"]
                }
                """);
        assertTrue(result.isError(), "FLIGHT without a flight block must not load");
        assertTrue(result.error().orElseThrow().message().contains("no 'flight' block"));
    }

    @Test
    void flightBlock_withoutFlightCapability_isRejected() {
        DataResult<AnimagusFormDefinition> result = parse("""
                {
                  "canon_tier": "book",
                  "model": "wizards_and_beasts:geckolib/models/entity/form/cat.geo.json",
                  "texture": "wizards_and_beasts:textures/entity/form/cat.png",
                  "animations": "wizards_and_beasts:geckolib/animations/entity/form/cat.animation.json",
                  "animation_map": {
                    "idle": "a", "walk": "b", "run": "c", "jump": "d", "hurt": "e"
                  },
                  "hitbox": { "width": 0.6, "height": 0.7, "eye_height": 0.55 },
                  "attributes": { "minecraft:scale": 0.55 },
                  "capabilities": ["CLIMB"],
                  "flight": {
                    "takeoff_impulse": 0.55, "flap_impulse": 0.42, "flap_cooldown_ticks": 12,
                    "max_speed": 1.35, "stall_speed": 0.18, "glide_drag": 0.985,
                    "flight_ceiling_offset": 160
                  }
                }
                """);
        assertTrue(result.isError(), "a flight block on a non-flier must not load");
        assertTrue(result.error().orElseThrow().message().contains("does not declare the FLIGHT capability"));
    }

    @Test
    void missingRequiredAnimationRole_isRejected() {
        DataResult<AnimagusFormDefinition> result = parse("""
                {
                  "canon_tier": "book",
                  "model": "wizards_and_beasts:geckolib/models/entity/form/rat.geo.json",
                  "texture": "wizards_and_beasts:textures/entity/form/rat.png",
                  "animations": "wizards_and_beasts:geckolib/animations/entity/form/rat.animation.json",
                  "animation_map": { "idle": "a", "walk": "b", "run": "c", "jump": "d" },
                  "hitbox": { "width": 0.4, "height": 0.35, "eye_height": 0.25 },
                  "attributes": { "minecraft:scale": 0.35 },
                  "capabilities": ["CLIMB"]
                }
                """);
        assertTrue(result.isError(), "a form missing the 'hurt' animation role must not load");
        assertTrue(result.error().orElseThrow().message().contains("'hurt'"));
    }

    @Test
    void flier_missingGlideOrFlap_isRejected() {
        DataResult<AnimagusFormDefinition> result = parse("""
                {
                  "canon_tier": "pottermore",
                  "model": "wizards_and_beasts:geckolib/models/entity/form/falcon.geo.json",
                  "texture": "wizards_and_beasts:textures/entity/form/falcon.png",
                  "animations": "wizards_and_beasts:geckolib/animations/entity/form/falcon.animation.json",
                  "animation_map": {
                    "idle": "a", "walk": "b", "run": "c", "jump": "d", "hurt": "e", "glide": "f"
                  },
                  "hitbox": { "width": 0.5, "height": 0.5, "eye_height": 0.4 },
                  "attributes": { "minecraft:scale": 0.4 },
                  "capabilities": ["FLIGHT"],
                  "flight": {
                    "takeoff_impulse": 0.55, "flap_impulse": 0.42, "flap_cooldown_ticks": 12,
                    "max_speed": 1.35, "stall_speed": 0.18, "glide_drag": 0.985,
                    "flight_ceiling_offset": 160
                  }
                }
                """);
        assertTrue(result.isError(), "a flier missing the 'flap' animation role must not load");
        assertTrue(result.error().orElseThrow().message().contains("'flap'"));
    }

    private static AnimagusFormDefinition parseShipped(String fileName) throws IOException {
        JsonElement source = JsonParser.parseString(Files.readString(FORMS_DIR.resolve(fileName)));
        return AnimagusFormDefinition.CODEC.parse(JsonOps.INSTANCE, source)
                .getOrThrow(msg -> new AssertionError(fileName + ": " + msg));
    }

    private static DataResult<AnimagusFormDefinition> parse(String json) {
        return AnimagusFormDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }
}
