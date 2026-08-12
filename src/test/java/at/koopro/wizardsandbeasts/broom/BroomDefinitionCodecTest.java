package at.koopro.wizardsandbeasts.broom;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Back-compat gate for {@link BroomDefinition}'s codec after {@code model_slots} and
 * {@code wood_tint} were added.
 *
 * <p>The payoff of the master-model route is that a datapack assembles a broom from existing part
 * variants. That is worth nothing if adding the field breaks the seven brooms already on disk, so
 * the first test is the one that matters: every shipped JSON, unmodified, still decodes — and comes
 * out carrying the default silhouette rather than an empty slot map that would render nothing.
 */
class BroomDefinitionCodecTest {

    private static final Gson GSON = new Gson();
    private static final Path BROOM_DIR =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "broom_definitions");

    @Test
    void everyShippedBroomJson_stillParsesUnmodified() throws IOException {
        try (Stream<Path> files = Files.list(BROOM_DIR)) {
            List<Path> jsons = files.filter(p -> p.toString().endsWith(".json")).toList();
            assertFalse(jsons.isEmpty(), "expected broom definition JSONs on disk");
            for (Path json : jsons) {
                String name = json.getFileName().toString();
                String raw = Files.readString(json);
                BroomDefinition def = parse(raw, name);

                // Assert the fallback claim only where it is actually being exercised. broom.json
                // authors both keys, so asserting defaults there would pass for the wrong reason.
                if (!raw.contains("\"model_slots\"")) {
                    assertEquals(BroomSlot.defaults(), def.modelSlots(),
                            name + ": a JSON with no model_slots must fall back to the default silhouette");
                }
                if (!raw.contains("\"wood_tint\"")) {
                    assertEquals(BroomDefinition.UNTINTED, def.woodTint(),
                            name + ": a JSON with no wood_tint must decode as untinted");
                }
            }
        }
    }

    @Test
    void defaultSilhouette_coversTheRequiredSlotsAndNothingElse() {
        var defaults = BroomSlot.defaults();
        for (BroomSlot slot : BroomSlot.values()) {
            if (slot.isRequired()) {
                assertTrue(defaults.containsKey(slot),
                        slot + " is required, so it must have a default variant to fall back to");
            }
        }
        assertTrue(defaults.containsKey(BroomSlot.FOOTSTRAP),
                "the footstrap is default-present: reference shows the strap on training brooms too");
        assertEquals(Identifier.fromNamespaceAndPath("wizards_and_beasts", "none"),
                defaults.get(BroomSlot.ACCENT),
                "no nameplate by default, expressed as the explicit 'none' variant rather than an "
                        + "absent key, so the renderer needs no special case for an unset slot");
    }

    @Test
    void authoredModelSlotsAndWoodTint_decode() {
        BroomDefinition def = parse(withExtra("""
                  "model_slots": {
                    "shaft": "wizards_and_beasts:swept",
                    "bristles": "wizards_and_beasts:blade"
                  },
                  "wood_tint": "#7D5531"
                """), "authored");

        assertEquals(Identifier.fromNamespaceAndPath("wizards_and_beasts", "swept"),
                def.modelSlot(BroomSlot.SHAFT).orElseThrow());
        assertEquals(Identifier.fromNamespaceAndPath("wizards_and_beasts", "blade"),
                def.modelSlot(BroomSlot.BRISTLES).orElseThrow());
        assertTrue(def.modelSlot(BroomSlot.ACCENT).isEmpty(),
                "a slot the JSON does not name stays unset, so nothing is drawn for it");
        assertEquals(0xFF7D5531, def.woodTint(), "#RRGGBB is opaque");
    }

    /**
     * The pre-rename {@code footrest} key still decodes, onto {@link BroomSlot#FOOTSTRAP}.
     *
     * <p>The slot codec rejects unknown ids by design, so without the alias a datapack written
     * against the old name would fail to load rather than degrade — the loudest possible break for
     * a rename that changes no behaviour.
     */
    @Test
    void legacyFootrestKey_stillDecodes() {
        BroomDefinition def = parse(withExtra("""
                  "model_slots": {
                    "footrest": "wizards_and_beasts:leather"
                  }
                """), "legacy footrest key");

        assertEquals(Identifier.fromNamespaceAndPath("wizards_and_beasts", "leather"),
                def.modelSlot(BroomSlot.FOOTSTRAP).orElseThrow(),
                "the old key must land on the renamed slot");
    }

    @Test
    void woodTint_acceptsExplicitAlpha() {
        BroomDefinition def = parse(withExtra("\"wood_tint\": \"#807D5531\""), "alpha");
        assertEquals(0x807D5531, def.woodTint(), "#AARRGGBB is taken as written");
    }

    @Test
    void unknownSlotKey_isRejected() {
        var result = decode(withExtra("\"model_slots\": {\"tailfin\": \"wizards_and_beasts:x\"}"));
        assertTrue(result.error().isPresent(), "an unknown slot name must not decode silently");
        assertTrue(result.error().orElseThrow().message().contains("tailfin"),
                "the error should name the offending slot");
    }

    @Test
    void malformedWoodTint_isRejected() {
        assertTrue(decode(withExtra("\"wood_tint\": \"#12345\"")).error().isPresent(),
                "a hex string of the wrong length must not decode");
        assertTrue(decode(withExtra("\"wood_tint\": \"#GGGGGG\"")).error().isPresent(),
                "a non-hex string must not decode");
    }

    /**
     * The in-code fallback and {@code broom_definitions/broom.json} describe the same broom. They
     * have to: the JSON is what normally loads, the constant is what remains when a datapack deletes
     * it, and a player should not be able to tell which one they are flying.
     */
    @Test
    void codeDefault_matchesShippedBroomJson() throws IOException {
        BroomDefinition json = parse(Files.readString(BROOM_DIR.resolve("broom.json")), "broom.json");
        BroomDefinition code = BroomDefinitionRegistry.codeDefault();

        assertEquals(json.id(), code.id());
        assertEquals(json.tier(), code.tier());
        assertEquals(json.maxSpeed(), code.maxSpeed(), 0.0f);
        assertEquals(json.acceleration(), code.acceleration(), 0.0f);
        assertEquals(json.deceleration(), code.deceleration(), 0.0f);
        assertEquals(json.boostMultiplier(), code.boostMultiplier(), 0.0f);
        assertEquals(json.boostDurationTicks(), code.boostDurationTicks());
        assertEquals(json.boostCooldownTicks(), code.boostCooldownTicks());
        assertEquals(json.weakGravity(), code.weakGravity(), 0.0f);
        assertEquals(json.lerpFactor(), code.lerpFactor(), 0.0f);
        assertEquals(json.turnSpeed(), code.turnSpeed(), 0.0f);
        assertEquals(json.ascentSpeed(), code.ascentSpeed(), 0.0f);
        assertEquals(json.descentSpeed(), code.descentSpeed(), 0.0f);
        assertEquals(json.handlingRating(), code.handlingRating(), 0.0f);
        assertEquals(json.stabilityRating(), code.stabilityRating(), 0.0f);
        assertEquals(json.durability(), code.durability());
        assertEquals(json.repairMaterial(), code.repairMaterial());
        assertEquals(json.modelSlots(), code.modelSlots());
        assertEquals(json.woodTint(), code.woodTint());
    }

    /** With nothing loaded, resolving a broom must still yield a broom rather than blowing up. */
    @Test
    void getFallback_degradesToCodeDefault_withNoDefinitionsLoaded() {
        BroomDefinitionRegistry.replaceAll(java.util.Map.of());
        assertSame(BroomDefinitionRegistry.codeDefault(), BroomDefinitionRegistry.getFallback(),
                "an empty registry must degrade to the code default, not throw");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** A minimal valid broom, plus whatever extra keys the test is exercising. */
    private static String withExtra(String extraKeys) {
        return """
                {
                  "id": "wizards_and_beasts:test_broom",
                  "displayName": {"translate": "item.wizards_and_beasts.test_broom"},
                  "tier": "SCHOOL",
                  "maxSpeed": 0.35,
                  "acceleration": 0.050,
                  "deceleration": 0.012,
                  "boostMultiplier": 1.3,
                  "boostDurationTicks": 40,
                  "boostCooldownTicks": 200,
                  "weakGravity": 0.012,
                  "lerpFactor": 0.10,
                  "turnSpeed": 0.75,
                  "ascentSpeed": 0.14,
                  "descentSpeed": 0.18,
                  "handlingRating": 0.40,
                  "stabilityRating": 0.80,
                  "durability": 120,
                  "repairMaterial": "#minecraft:planks",
                """ + extraKeys + "\n}";
    }

    private static com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<BroomDefinition, JsonElement>>
            decode(String json) {
        return BroomDefinition.CODEC.decode(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class));
    }

    private static BroomDefinition parse(String json, String what) {
        return decode(json)
                .resultOrPartial(err -> fail(what + " failed to parse: " + err))
                .orElseThrow()
                .getFirst();
    }
}
