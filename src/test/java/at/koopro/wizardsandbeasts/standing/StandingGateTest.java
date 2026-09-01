package at.koopro.wizardsandbeasts.standing;

import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.standing.gate.StandingGate;
import at.koopro.wizardsandbeasts.standing.gate.StandingGates;
import at.koopro.wizardsandbeasts.standing.gate.StandingRequirement;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gate half: what a datapack can close off, and to whom.
 *
 * <p>Gate evaluation takes a {@code StandingLookup} rather than a player, which is what makes it
 * testable here at all — the production call passes one lambda over {@code StandingService} and a
 * test passes bands directly.
 */
class StandingGateTest {

    private static final Gson GSON = new Gson();

    @AfterEach
    void clearRegistry() {
        // The registry is a static volatile shared with the running server; leaving one test's gates
        // in place would leak into the next and, in a dev session, into the game.
        StandingGates.replaceAll(Map.of());
    }

    private static DataResult<StandingGate> parse(String json) {
        return StandingGate.CODEC.parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class));
    }

    private static StandingGate parseOrThrow(String json) {
        return parse(json).getOrThrow(msg -> new AssertionError("expected to parse: " + msg));
    }

    /** A lookup that answers NEUTRAL for anything not named. */
    private static StandingGate.StandingLookup bands(Map<StandingAxis, StandingBand> map) {
        Map<StandingAxis, StandingBand> filled = new EnumMap<>(StandingAxis.class);
        for (StandingAxis axis : StandingAxis.values()) {
            filled.put(axis, map.getOrDefault(axis, StandingBand.NEUTRAL));
        }
        return filled::get;
    }

    // ── requirement windows ──

    @Test
    void aFloorAdmitsEveryBandAtOrAboveIt() {
        StandingRequirement req = new StandingRequirement(
                StandingAxis.TRADITION, StandingBand.LEANING_POSITIVE, null);

        assertFalse(req.isSatisfiedBy(StandingBand.NEUTRAL));
        assertFalse(req.isSatisfiedBy(StandingBand.LEANING_NEGATIVE));
        assertTrue(req.isSatisfiedBy(StandingBand.LEANING_POSITIVE));
        assertTrue(req.isSatisfiedBy(StandingBand.STRONG_POSITIVE));
    }

    @Test
    void aCeilingExpressesTheAbsenceOfALeaning() {
        // "not a dark wizard" is a real requirement and is not the same statement as "be a light one".
        StandingRequirement req = new StandingRequirement(
                StandingAxis.ALIGNMENT, null, StandingBand.NEUTRAL);

        assertTrue(req.isSatisfiedBy(StandingBand.STRONG_NEGATIVE));
        assertTrue(req.isSatisfiedBy(StandingBand.NEUTRAL));
        assertFalse(req.isSatisfiedBy(StandingBand.LEANING_POSITIVE));
    }

    @Test
    void aWindowWithBothEndsAdmitsOnlyTheMiddle() {
        StandingRequirement req = new StandingRequirement(
                StandingAxis.MINISTRY, StandingBand.LEANING_NEGATIVE, StandingBand.LEANING_POSITIVE);

        assertFalse(req.isSatisfiedBy(StandingBand.STRONG_NEGATIVE));
        assertTrue(req.isSatisfiedBy(StandingBand.LEANING_NEGATIVE));
        assertTrue(req.isSatisfiedBy(StandingBand.NEUTRAL));
        assertTrue(req.isSatisfiedBy(StandingBand.LEANING_POSITIVE));
        assertFalse(req.isSatisfiedBy(StandingBand.STRONG_POSITIVE));
    }

    @Test
    void aRequirementWithNeitherEndIsRefusedAtLoad() {
        assertTrue(parse("""
                { "tree": "dark_arts", "requirements": [ { "axis": "alignment" } ] }
                """).isError(), "a requirement that admits everyone gates nothing");
    }

    @Test
    void anImpossibleWindowIsRefusedAtLoad() {
        assertTrue(parse("""
                { "tree": "dark_arts", "requirements": [
                    { "axis": "alignment", "minBand": "strong_positive", "maxBand": "strong_negative" } ] }
                """).isError(), "no player could ever satisfy it, so it is a typo not a rule");
    }

    // ── gate shape ──

    @Test
    void aGateMustNameExactlyOneTarget() {
        assertTrue(parse("""
                { "requirements": [ { "axis": "alignment", "maxBand": "neutral" } ] }
                """).isError(), "neither tree nor node");

        assertTrue(parse("""
                { "tree": "dark_arts", "node": "curse_basics",
                  "requirements": [ { "axis": "alignment", "maxBand": "neutral" } ] }
                """).isError(), "both tree and node");

        assertTrue(parse("""
                { "tree": "dark_arts", "requirements": [ { "axis": "alignment", "maxBand": "neutral" } ] }
                """).result().isPresent());
    }

    @Test
    void aGateWithNoRequirementsIsRefused() {
        assertTrue(parse("{ \"tree\": \"dark_arts\", \"requirements\": [] }").isError());
    }

    @Test
    void anUnknownTreeIsRefusedRatherThanGatingNothing() {
        assertTrue(parse("""
                { "tree": "divination", "requirements": [ { "axis": "alignment", "maxBand": "neutral" } ] }
                """).isError());
    }

    // ── evaluation through the registry ──

    @Test
    void adefaultInstallGatesNothingAndCostsNothingToCheck() {
        assertTrue(StandingGates.isEmpty());
        assertNull(StandingGates.firstUnmet(SkillTreeId.DARK_ARTS, "anything",
                bands(Map.of(StandingAxis.ALIGNMENT, StandingBand.STRONG_POSITIVE))));
    }

    @Test
    void aTreeGateClosesEveryNodeInThatTreeAndNoOther() {
        StandingGates.replaceAll(Map.of(Identifier.fromNamespaceAndPath("test", "dark"),
                parseOrThrow("""
                        { "tree": "dark_arts",
                          "requirements": [ { "axis": "alignment", "maxBand": "neutral" } ] }
                        """)));

        StandingGate.StandingLookup saint = bands(Map.of(
                StandingAxis.ALIGNMENT, StandingBand.STRONG_POSITIVE));
        StandingGate.StandingLookup neutral = bands(Map.of());

        assertNotNull(StandingGates.firstUnmet(SkillTreeId.DARK_ARTS, "curse_basics", saint),
                "a wizard too far toward the light cannot take Dark Arts");
        assertNull(StandingGates.firstUnmet(SkillTreeId.DARK_ARTS, "curse_basics", neutral),
                "an untested wizard still can");
        assertNull(StandingGates.firstUnmet(SkillTreeId.HERBOLOGY, "sprouting", saint),
                "and no other tree is affected");
    }

    @Test
    void aNodeGateClosesOnlyThatNode() {
        StandingGates.replaceAll(Map.of(Identifier.fromNamespaceAndPath("test", "node"),
                parseOrThrow("""
                        { "node": "Blood_Rites",
                          "requirements": [ { "axis": "tradition", "minBand": "strong_positive" } ] }
                        """)));

        StandingGate.StandingLookup reformist = bands(Map.of(
                StandingAxis.TRADITION, StandingBand.STRONG_NEGATIVE));

        assertNotNull(StandingGates.firstUnmet(SkillTreeId.SPELL_MASTERY, "blood_rites", reformist),
                "node ids are matched case-insensitively");
        assertNull(StandingGates.firstUnmet(SkillTreeId.SPELL_MASTERY, "other_node", reformist));
    }

    @Test
    void theReportedRequirementIsTheOneThatFailedSoTheToastCanNameTheAxis() {
        StandingGates.replaceAll(Map.of(Identifier.fromNamespaceAndPath("test", "both"),
                parseOrThrow("""
                        { "tree": "wandlore",
                          "requirements": [
                            { "axis": "tradition", "minBand": "leaning_positive" },
                            { "axis": "ministry", "minBand": "leaning_positive" } ] }
                        """)));

        StandingRequirement unmet = StandingGates.firstUnmet(SkillTreeId.WANDLORE, "n",
                bands(Map.of(StandingAxis.TRADITION, StandingBand.STRONG_POSITIVE)));

        assertNotNull(unmet);
        assertEquals(StandingAxis.MINISTRY, unmet.axis(),
                "tradition was satisfied; the failing requirement is the one worth reporting");
    }

    @Test
    void aNodeGateIsReportedAheadOfATreeGateBecauseItIsTheMoreSpecificStatement() {
        Map<Identifier, StandingGate> gates = new LinkedHashMap<>();
        gates.put(Identifier.fromNamespaceAndPath("test", "tree"), parseOrThrow("""
                { "tree": "dark_arts", "requirements": [ { "axis": "ministry", "maxBand": "neutral" } ] }
                """));
        gates.put(Identifier.fromNamespaceAndPath("test", "node"), parseOrThrow("""
                { "node": "unforgivable", "requirements": [ { "axis": "tradition", "minBand": "strong_positive" } ] }
                """));
        StandingGates.replaceAll(gates);

        StandingRequirement unmet = StandingGates.firstUnmet(SkillTreeId.DARK_ARTS, "unforgivable",
                bands(Map.of(StandingAxis.MINISTRY, StandingBand.STRONG_POSITIVE)));

        assertNotNull(unmet);
        assertEquals(StandingAxis.TRADITION, unmet.axis());
    }

    @Test
    void allRequirementsOnAGateMustHold() {
        StandingGates.replaceAll(Map.of(Identifier.fromNamespaceAndPath("test", "and"),
                parseOrThrow("""
                        { "tree": "alchemy",
                          "requirements": [
                            { "axis": "tradition", "maxBand": "neutral" },
                            { "axis": "alignment", "minBand": "leaning_positive" } ] }
                        """)));

        assertNull(StandingGates.firstUnmet(SkillTreeId.ALCHEMY, "n", bands(Map.of(
                StandingAxis.TRADITION, StandingBand.LEANING_NEGATIVE,
                StandingAxis.ALIGNMENT, StandingBand.STRONG_POSITIVE))));

        assertNotNull(StandingGates.firstUnmet(SkillTreeId.ALCHEMY, "n", bands(Map.of(
                StandingAxis.TRADITION, StandingBand.LEANING_NEGATIVE,
                StandingAxis.ALIGNMENT, StandingBand.NEUTRAL))),
                "satisfying one of two is not satisfying the gate");
    }
}
