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
        assertEquals(json.assets(), code.assets());
        assertEquals(json.handling(), code.handling());
        assertEquals(json.audio(), code.audio());
        assertEquals(json.seat(), code.seat());
    }

    /**
     * A definition authoring none of the new keys decodes to the broom it was before they existed.
     *
     * <p>This is the whole claim of the pass — sixteen optional fields, and a datapack written
     * against the old schema still describes the same broom. Asserted against a minimal JSON rather
     * than against a shipped one, because every shipped broom now authors these keys and would pass
     * for the wrong reason.
     */
    @Test
    void aDefinitionWithNoNewKeys_getsTheOldBehaviour() {
        BroomDefinition def = decode(withExtra("\"loreLines\": [ ]")).result().orElseThrow().getFirst();

        assertEquals(BroomAssets.DEFAULT, def.assets(),
                "no model, texture or animation means the shared broom rig");
        assertEquals(HandlingProfile.BALANCED, def.handling().profile());
        assertEquals(BroomHandling.DEFAULT, def.handling());
        assertEquals(BroomAudio.DEFAULT, def.audio());
        assertEquals(BroomSeat.DEFAULT, def.seat());

        // BALANCED's momentum is the value that reproduces BroomTuning's old drag constants; if
        // this drifts, every unauthored broom silently changes how far it coasts.
        assertEquals(0.990f, def.handling().coastDrag(), 1.0e-6f);
        assertEquals(0.995f, def.handling().inputDrag(), 1.0e-6f);
        // ...and the old hardcoded durability losses.
        assertEquals(1, def.handling().minorImpactDurabilityLoss());
        assertEquals(2, def.handling().moderateImpactDurabilityLoss());
        assertEquals(3, def.handling().severeImpactDurabilityLoss());
        assertEquals(1.0f, def.handling().crashDamageMultiplier(), 0.0f);
    }

    /** A profile supplies defaults; an explicit key beats it. That is what makes it not decoration. */
    @Test
    void handlingProfile_suppliesDefaults_andExplicitKeysWin() {
        BroomDefinition racing = decode(withExtra("\"handlingProfile\": \"racing\""))
                .result().orElseThrow().getFirst();
        assertEquals(HandlingProfile.RACING, racing.handling().profile());
        assertEquals(HandlingProfile.RACING.yawDrift(), racing.handling().yawDrift(), 0.0f);

        BroomDefinition tuned = decode(withExtra(
                "\"handlingProfile\": \"racing\", \"yawDrift\": 0.25"))
                .result().orElseThrow().getFirst();
        assertEquals(HandlingProfile.RACING, tuned.handling().profile());
        assertEquals(0.25f, tuned.handling().yawDrift(), 0.0f,
                "an authored key must beat the profile it sits next to");
        assertEquals(HandlingProfile.RACING.momentumRetention(), tuned.handling().momentumRetention(), 0.0f,
                "and must not disturb the fields it did not name");
    }

    /** An unknown profile is a codec error naming the value, not a silent fall back to balanced. */
    @Test
    void unknownHandlingProfile_isAnError() {
        var result = decode(withExtra("\"handlingProfile\": \"hovercraft\""));
        assertTrue(result.error().isPresent(), "an unknown handlingProfile must not decode");
        assertTrue(result.error().orElseThrow().message().contains("hovercraft"),
                "the error must name the offending value: " + result.error().orElseThrow().message());
    }

    /**
     * Every fault in one definition is reported at once.
     *
     * <p>The nested-flatMap decoder this replaced stopped at the first, so fixing a datapack with
     * four bad values took four reload cycles to discover them all.
     */
    @Test
    void everyFaultIsReportedTogether() {
        var result = decode(withExtra(
                "\"handlingProfile\": \"hovercraft\", \"yawDrift\": 9.0, \"momentumRetention\": 0.1"));
        String message = result.error().orElseThrow().message();
        assertTrue(message.contains("hovercraft"), message);
        assertTrue(message.contains("yawDrift"), message);
        assertTrue(message.contains("momentumRetention"), message);
    }

    /**
     * The acceptance claim: JSON alone redirects geometry, texture and animation.
     *
     * <p>Nothing downstream of this decodes anything else — {@code BroomVariantGeoModel} asks the
     * definition for each of the three and falls back to the base {@code broom} asset for whichever
     * is absent, so carrying the ids is the whole of the mechanism.
     */
    @Test
    void assets_redirectModelTextureAndAnimation_fromJsonAlone() {
        BroomDefinition def = decode(withExtra(
                "\"model\": \"wizards_and_beasts:broom_firebolt\", "
                        + "\"texture\": \"wizards_and_beasts:textures/entity/broom/firebolt.png\", "
                        + "\"animation\": \"wizards_and_beasts:broom\""))
                .result().orElseThrow().getFirst();

        assertEquals(Identifier.parse("wizards_and_beasts:broom_firebolt"),
                def.assets().model().orElseThrow());
        assertEquals(Identifier.parse("wizards_and_beasts:broom"),
                def.assets().animation().orElseThrow());
        assertTrue(def.assets().textureIsFullPath(),
                "a value already rooted at textures/ must be used verbatim, not formatted again");
    }

    /** The subpath form of {@code texture} is the one GeckoLib's own helpers produce. */
    @Test
    void texture_acceptsTheSubpathFormToo() {
        BroomDefinition def = decode(withExtra("\"texture\": \"wizards_and_beasts:broom/firebolt\""))
                .result().orElseThrow().getFirst();
        assertFalse(def.assets().textureIsFullPath(),
                "a subpath must be formatted under textures/entity/, not used as a path");
        assertTrue(def.hasOwnTexture());
    }

    /** The key this field carried for one day. Kept so nothing written against it breaks. */
    @Test
    void texture_stillAcceptsTheLegacyEntityTextureKey() {
        BroomDefinition def = decode(withExtra("\"entity_texture\": \"wizards_and_beasts:broom/firebolt\""))
                .result().orElseThrow().getFirst();
        assertEquals(Identifier.parse("wizards_and_beasts:broom/firebolt"),
                def.assets().texture().orElseThrow());
    }

    /**
     * Every shipped definition survives a full encode/decode round trip, unchanged.
     *
     * <p>This is the gate on the client sync. {@code BroomDefinitionsSyncS2CPayload} serialises with
     * {@link BroomDefinition#CODEC} itself rather than a hand-written field list, so a field that
     * {@code encode} forgets to write does not fail to compile and does not throw — it simply
     * arrives on the client as its default. That is invisible in single-player, where the client
     * reads the server's own static registry and no packet is involved at all.
     *
     * <p>Run through {@code NbtOps}, which is what the payload uses, rather than the {@code JsonOps}
     * the rest of this class uses: number widening differs between the two, and the sync is the case
     * that matters.
     */
    @Test
    void everyShippedBroom_survivesAnNbtRoundTrip() throws IOException {
        try (Stream<Path> files = Files.list(BROOM_DIR)) {
            for (Path json : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = json.getFileName().toString();
                BroomDefinition original = parse(Files.readString(json), name);

                var encoded = BroomDefinition.CODEC
                        .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, original)
                        .resultOrPartial(err -> fail(name + " failed to encode: " + err))
                        .orElseThrow();
                BroomDefinition round = BroomDefinition.CODEC
                        .parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded)
                        .resultOrPartial(err -> fail(name + " failed to decode: " + err))
                        .orElseThrow();

                assertEquals(original, round,
                        name + " does not survive a round trip. Whatever differs is a field encode "
                                + "does not write, and it would reach clients as its default.");
            }
        }
    }

    /** Unknown keys are ignored, so a definition from a later version still loads here. */
    @Test
    void unknownKeysAreIgnored() {
        assertTrue(decode(withExtra("\"antiGravityCoils\": 4")).result().isPresent(),
                "an unrecognised key must not fail the load — that is what forward compat means");
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
