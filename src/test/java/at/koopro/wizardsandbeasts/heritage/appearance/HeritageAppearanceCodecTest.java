package at.koopro.wizardsandbeasts.heritage.appearance;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Codec tests for {@link HeritageAppearance} and its three union arms. Pure parsing — no Minecraft
 * bootstrap, since the codecs only touch {@code Identifier}, plain enums and the heritage enums.
 *
 * <p>The cross-field invariants are the point. An entry that declares both a proportion and a form,
 * or a provenance that claims a citation <em>and</em> fan-extrapolation, parses fine field-by-field
 * and only misbehaves once a player wears the heritage. That has to fail at datapack load.
 */
class HeritageAppearanceCodecTest {

    private static DataResult<HeritageAppearance> parse(String json) {
        JsonElement element = JsonParser.parseString(json);
        return HeritageAppearance.CODEC.parse(JsonOps.INSTANCE, element);
    }

    private static HeritageAppearance parseOk(String json) {
        return parse(json).getOrThrow(msg -> new AssertionError("expected a successful parse: " + msg));
    }

    private static void assertRoundTrips(HeritageAppearance entry) {
        JsonElement encoded = HeritageAppearance.CODEC.encodeStart(JsonOps.INSTANCE, entry)
                .getOrThrow(msg -> new AssertionError("failed to encode: " + msg));
        HeritageAppearance again = HeritageAppearance.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(msg -> new AssertionError("failed to re-decode: " + msg));
        assertEquals(entry, again, "did not survive a codec round trip");
    }

    // ── union arms ──

    @Test
    void proportionArm_roundTrips() {
        HeritageAppearance entry = parseOk("""
                {
                  "id": "wizards_and_beasts:half_giant",
                  "heritage": "giant",
                  "variant": "half_giant",
                  "mechanisms": [
                    {
                      "type": "proportion",
                      "scale": 1.6,
                      "boneOffsets": {
                        "head": { "y": -0.5 },
                        "right_arm": { "x": 0.25, "z": 0.1 }
                      }
                    }
                  ],
                  "provenance": { "citation": "Goblet of Fire, ch. 23" }
                }
                """);

        Proportion proportion = entry.proportion();
        assertNotNull(proportion, "proportion arm should be present");
        assertEquals(1.6f, proportion.scale());
        assertEquals(2, proportion.boneOffsets().size());
        assertEquals(-0.5f, proportion.boneOffsets().get("head").y());
        assertEquals(0.25f, proportion.boneOffsets().get("right_arm").x());
        assertNull(entry.form(), "a proportion entry must not also yield a form arm");
        assertFalse(proportion.isIdentity());
        assertRoundTrips(entry);
    }

    @Test
    void formArm_roundTrips() {
        HeritageAppearance entry = parseOk("""
                {
                  "id": "wizards_and_beasts:werewolf",
                  "heritage": "werewolf",
                  "mechanisms": [
                    {
                      "type": "form",
                      "formId": "werewolf_wolf",
                      "texture": "wizards_and_beasts:textures/entity/form/werewolf.png",
                      "trigger": "full_moon"
                    }
                  ],
                  "provenance": { "citation": "Prisoner of Azkaban, ch. 20" }
                }
                """);

        FormAppearance form = entry.form();
        assertNotNull(form, "form arm should be present");
        assertEquals("werewolf_wolf", form.formId());
        assertTrue(form.texture().isPresent());
        assertTrue(form.model().isEmpty(), "an omitted override must stay absent, not default to something");
        assertEquals("full_moon", form.trigger().orElseThrow());
        assertRoundTrips(entry);
    }

    @Test
    void overlayArm_roundTrips() {
        HeritageAppearance entry = parseOk("""
                {
                  "id": "wizards_and_beasts:obscurial_unleashed",
                  "heritage": "obscurial",
                  "variant": "unleashed",
                  "mechanisms": [
                    {
                      "type": "overlay",
                      "particle": "minecraft:large_smoke",
                      "tint": -13421773,
                      "emissive": true
                    }
                  ],
                  "provenance": { "citation": "Fantastic Beasts and Where to Find Them (2016 film)" }
                }
                """);

        OverlayAppearance overlay = entry.overlay();
        assertNotNull(overlay, "overlay arm should be present");
        assertTrue(overlay.particle().isPresent());
        assertTrue(overlay.emissive());
        assertTrue(overlay.isAlwaysOn(), "an overlay with no trigger is always-on");
        assertRoundTrips(entry);
    }

    @Test
    void bodyArmAndOverlayTogether_isLegal() {
        HeritageAppearance entry = parseOk("""
                {
                  "id": "wizards_and_beasts:vampire",
                  "heritage": "vampire",
                  "mechanisms": [
                    { "type": "proportion", "scale": 1.0 },
                    { "type": "overlay", "texture": "wizards_and_beasts:textures/entity/form/pallor.png" }
                  ],
                  "provenance": { "citation": "Half-Blood Prince, ch. 15" }
                }
                """);

        assertNotNull(entry.proportion(), "vampire carries both a proportion and an overlay");
        assertNotNull(entry.overlay());
        assertRoundTrips(entry);
    }

    // ── defaults ──

    @Test
    void omittedMechanisms_yieldAnEmptyEntryRatherThanAFailure() {
        HeritageAppearance entry = parseOk("""
                {
                  "id": "wizards_and_beasts:wizardkind",
                  "heritage": "wizardkind",
                  "provenance": { "fanExtrapolation": true }
                }
                """);

        assertTrue(entry.isEmpty(), "an entry with no mechanisms is legal and renders nothing");
        assertNull(entry.proportion());
        assertNull(entry.form());
        assertNull(entry.overlay());
        assertRoundTrips(entry);
    }

    @Test
    void proportionDefaults_areIdentity() {
        HeritageAppearance entry = parseOk("""
                {
                  "id": "wizards_and_beasts:plain",
                  "heritage": "wizardkind",
                  "mechanisms": [ { "type": "proportion" } ],
                  "provenance": { "fanExtrapolation": true }
                }
                """);

        Proportion proportion = entry.proportion();
        assertNotNull(proportion);
        assertEquals(1.0f, proportion.scale());
        assertTrue(proportion.boneOffsets().isEmpty());
        assertTrue(proportion.isIdentity(), "an all-defaults proportion changes nothing");
    }

    @Test
    void overlayTintDefaults_toOpaqueWhite() {
        HeritageAppearance entry = parseOk("""
                {
                  "id": "wizards_and_beasts:plain_overlay",
                  "heritage": "wizardkind",
                  "mechanisms": [
                    { "type": "overlay", "texture": "wizards_and_beasts:textures/entity/form/pallor.png" }
                  ],
                  "provenance": { "fanExtrapolation": true }
                }
                """);

        OverlayAppearance overlay = entry.overlay();
        assertNotNull(overlay);
        assertEquals(OverlayAppearance.NO_TINT, overlay.tint());
        assertFalse(overlay.emissive());
    }

    // ── cross-field invariants ──

    @Test
    void proportionAndFormTogether_isRejected() {
        DataResult<HeritageAppearance> result = parse("""
                {
                  "id": "wizards_and_beasts:contradiction",
                  "heritage": "werewolf",
                  "mechanisms": [
                    { "type": "proportion", "scale": 1.2 },
                    { "type": "form", "formId": "werewolf_wolf" }
                  ],
                  "provenance": { "fanExtrapolation": true }
                }
                """);

        assertTrue(result.isError(), "proportion and form are mutually exclusive");
        assertTrue(result.error().orElseThrow().message().contains("mutually exclusive"));
    }

    @Test
    void twoOverlays_areRejected() {
        DataResult<HeritageAppearance> result = parse("""
                {
                  "id": "wizards_and_beasts:double_overlay",
                  "heritage": "vampire",
                  "mechanisms": [
                    { "type": "overlay", "texture": "wizards_and_beasts:textures/a.png" },
                    { "type": "overlay", "texture": "wizards_and_beasts:textures/b.png" }
                  ],
                  "provenance": { "fanExtrapolation": true }
                }
                """);

        assertTrue(result.isError(), "at most one overlay may appear");
    }

    @Test
    void unknownHeritage_isRejected() {
        DataResult<HeritageAppearance> result = parse("""
                {
                  "id": "wizards_and_beasts:nope",
                  "heritage": "dementor",
                  "provenance": { "fanExtrapolation": true }
                }
                """);

        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("unknown heritage"));
    }

    @Test
    void variantFromADifferentHeritage_isRejected() {
        // "turned" is a vampire variant; claiming it under werewolf is the kind of copy-paste slip
        // that would otherwise resolve to nothing at render time and look like a missing texture.
        DataResult<HeritageAppearance> result = parse("""
                {
                  "id": "wizards_and_beasts:mismatch",
                  "heritage": "werewolf",
                  "variant": "turned",
                  "provenance": { "fanExtrapolation": true }
                }
                """);

        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("belongs to heritage"));
    }

    @Test
    void unknownVariant_isRejected() {
        DataResult<HeritageAppearance> result = parse("""
                {
                  "id": "wizards_and_beasts:nope",
                  "heritage": "werewolf",
                  "variant": "moon_touched",
                  "provenance": { "fanExtrapolation": true }
                }
                """);

        assertTrue(result.isError());
        assertTrue(result.error().orElseThrow().message().contains("unknown heritage variant"));
    }

    @Test
    void outOfRangeScale_isRejected() {
        DataResult<HeritageAppearance> result = parse("""
                {
                  "id": "wizards_and_beasts:typo",
                  "heritage": "giant",
                  "mechanisms": [ { "type": "proportion", "scale": 20.0 } ],
                  "provenance": { "fanExtrapolation": true }
                }
                """);

        assertTrue(result.isError(), "a 20x player is a typo, not a design decision");
        assertTrue(result.error().orElseThrow().message().contains("outside"));
    }

    @Test
    void overlayWithNeitherTextureNorParticle_isRejected() {
        DataResult<HeritageAppearance> result = parse("""
                {
                  "id": "wizards_and_beasts:empty_overlay",
                  "heritage": "vampire",
                  "mechanisms": [ { "type": "overlay", "emissive": true } ],
                  "provenance": { "fanExtrapolation": true }
                }
                """);

        assertTrue(result.isError(), "an overlay that names no asset renders nothing but looks wired");
    }

    @Test
    void blankFormId_isRejected() {
        DataResult<HeritageAppearance> result = parse("""
                {
                  "id": "wizards_and_beasts:blank",
                  "heritage": "veela",
                  "mechanisms": [ { "type": "form", "formId": "" } ],
                  "provenance": { "fanExtrapolation": true }
                }
                """);

        assertTrue(result.isError());
    }

    // ── provenance ──

    @Test
    void provenanceWithBothCitationAndExtrapolationFlag_isRejected() {
        DataResult<HeritageAppearance> result = parse("""
                {
                  "id": "wizards_and_beasts:both",
                  "heritage": "vampire",
                  "provenance": { "citation": "Half-Blood Prince, ch. 15", "fanExtrapolation": true }
                }
                """);

        assertTrue(result.isError(), "a citation and a fan-extrapolation flag contradict each other");
        assertTrue(result.error().orElseThrow().message().contains("pick one"));
    }

    @Test
    void provenanceWithNeither_isRejected() {
        DataResult<HeritageAppearance> result = parse("""
                {
                  "id": "wizards_and_beasts:neither",
                  "heritage": "vampire",
                  "provenance": {}
                }
                """);

        assertTrue(result.isError(), "an entry nobody has sourced or admitted to is not a decision");
    }

    @Test
    void provenanceWithABlankCitation_isRejected() {
        DataResult<HeritageAppearance> result = parse("""
                {
                  "id": "wizards_and_beasts:blank_citation",
                  "heritage": "vampire",
                  "provenance": { "citation": "   " }
                }
                """);

        assertTrue(result.isError(), "whitespace is not a citation");
    }

    @Test
    void provenanceFactories_produceValidValues() {
        assertTrue(Provenance.CODEC.encodeStart(JsonOps.INSTANCE, Provenance.cited("Goblet of Fire, ch. 8"))
                .result().isPresent());
        assertTrue(Provenance.CODEC.encodeStart(JsonOps.INSTANCE, Provenance.extrapolated())
                .result().isPresent());
    }
}
