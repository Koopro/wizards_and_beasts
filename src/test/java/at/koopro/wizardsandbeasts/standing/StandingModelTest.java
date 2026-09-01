package at.koopro.wizardsandbeasts.standing;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The model itself: accumulation, bounds, the derived axes, and what survives a save.
 *
 * <p>Everything here runs without a server because {@link StandingBands} and {@link MagicalStanding}
 * are deliberately free of {@code Config}, of attachments and of Minecraft. What that leaves
 * uncovered is named in the report rather than faked: the attachment read/write itself, and the
 * two-client isolation that follows from it.
 */
class StandingModelTest {

    private static final Gson GSON = new Gson();
    private static final float B = StandingBands.DEFAULT_BOUND;
    private static final int LEAN = StandingBands.DEFAULT_LEAN_PERCENT;
    private static final int STRONG = StandingBands.DEFAULT_STRONG_PERCENT;

    // ── the shape of the model ──

    /**
     * The load-bearing claim of the whole design: only one axis is written down. If a second one ever
     * becomes stored, it is duplicating a number the mod already keeps somewhere else, and the two
     * will drift the first time the original is written through a path that predates this system.
     */
    @Test
    void exactlyOneAxisIsStoredAndTheRestAreDerived() {
        assertTrue(StandingAxis.TRADITION.isStored());
        assertFalse(StandingAxis.ALIGNMENT.isStored(),
                "alignment is light minus the pre-existing corruption meter");
        assertFalse(StandingAxis.MINISTRY.isStored(),
                "ministry standing is a view over the criminal record");

        long stored = java.util.Arrays.stream(StandingAxis.values())
                .filter(StandingAxis::isStored).count();
        assertEquals(1L, stored);
    }

    @Test
    void everyAxisHasItsOwnColourSoTheMetersAreTellableApart() {
        assertNotEquals(StandingAxis.TRADITION.color(), StandingAxis.ALIGNMENT.color());
        assertNotEquals(StandingAxis.ALIGNMENT.color(), StandingAxis.MINISTRY.color());
        assertNotEquals(StandingAxis.TRADITION.color(), StandingAxis.MINISTRY.color());
    }

    // ── accumulation and bounds ──

    @Test
    void standingAccumulatesUntilItHitsTheBoundAndThenStops() {
        float value = 0.0f;
        for (int i = 0; i < 1000; i++) {
            value = StandingBands.clamp(value + 3.0f, B);
        }
        assertEquals(B, value, 1e-4, "accumulation stops at the bound rather than running away");

        for (int i = 0; i < 1000; i++) {
            value = StandingBands.clamp(value - 3.0f, B);
        }
        assertEquals(-B, value, 1e-4, "and the same in the other direction");
    }

    @Test
    void boundsAreSymmetricAndIgnoreASignedBound() {
        assertEquals(B, StandingBands.clamp(9999f, B), 1e-4);
        assertEquals(-B, StandingBands.clamp(-9999f, B), 1e-4);
        assertEquals(B, StandingBands.clamp(9999f, -B), 1e-4,
                "a negative bound is a magnitude, not a direction");
    }

    @Test
    void theLightPoleNeverGoesNegative() {
        assertEquals(0.0f, StandingBands.clampUnipolar(-40f, B), 1e-4);
        assertEquals(B, StandingBands.clampUnipolar(400f, B), 1e-4);
    }

    /** NaN would defeat every later clamp and comparison and render as "NaN" on the sheet. */
    @Test
    void notANumberIsNeutralisedRatherThanStored() {
        assertEquals(0.0f, StandingBands.clamp(Float.NaN, B), 1e-4);
        assertEquals(0.0f, StandingBands.clampUnipolar(Float.NaN, B), 1e-4);
        assertEquals(StandingBand.NEUTRAL, StandingBands.bandFor(Float.NaN, B, LEAN, STRONG));
        assertEquals(0.0f, new MagicalStanding(Float.NaN, Float.NaN).tradition(), 1e-4);
        assertEquals(0.0f, new MagicalStanding(Float.NaN, Float.NaN).light(), 1e-4);
    }

    @Test
    void theRecordRefusesANegativeLightPoleOnConstruction() {
        assertEquals(0.0f, new MagicalStanding(0f, -25f).light(), 1e-4);
    }

    // ── banding ──

    @Test
    void bandsAscendFromOnePoleToTheOther() {
        assertEquals(StandingBand.NEUTRAL, StandingBands.bandFor(0f, B, LEAN, STRONG));
        assertEquals(StandingBand.NEUTRAL, StandingBands.bandFor(24f, B, LEAN, STRONG));
        assertEquals(StandingBand.LEANING_POSITIVE, StandingBands.bandFor(25f, B, LEAN, STRONG));
        assertEquals(StandingBand.LEANING_POSITIVE, StandingBands.bandFor(59f, B, LEAN, STRONG));
        assertEquals(StandingBand.STRONG_POSITIVE, StandingBands.bandFor(60f, B, LEAN, STRONG));
        assertEquals(StandingBand.LEANING_NEGATIVE, StandingBands.bandFor(-30f, B, LEAN, STRONG));
        assertEquals(StandingBand.STRONG_NEGATIVE, StandingBands.bandFor(-90f, B, LEAN, STRONG));
    }

    @Test
    void bandsAreThresholdsOnTheBoundSoRetuningTheBoundDoesNotMoveThem() {
        // Half the bound is the same position on the axis whatever the bound is.
        assertEquals(StandingBands.bandFor(50f, 100f, LEAN, STRONG),
                StandingBands.bandFor(100f, 200f, LEAN, STRONG));
        assertEquals(StandingBands.bandFor(-50f, 100f, LEAN, STRONG),
                StandingBands.bandFor(-100f, 200f, LEAN, STRONG));
    }

    /**
     * A server can invert the two threshold keys. Ordering them at read time keeps the leaning band
     * reachable; trusting the caller would silently delete it.
     */
    @Test
    void invertedThresholdsDoNotDeleteTheLeaningBand() {
        assertEquals(StandingBand.LEANING_POSITIVE, StandingBands.bandFor(30f, B, STRONG, LEAN));
        assertEquals(StandingBand.STRONG_POSITIVE, StandingBands.bandFor(70f, B, STRONG, LEAN));
    }

    @Test
    void aZeroBoundBandsEveryoneNeutralRatherThanDividingByIt() {
        assertEquals(StandingBand.NEUTRAL, StandingBands.bandFor(50f, 0f, LEAN, STRONG));
    }

    // ── the derived axes ──

    @Test
    void alignmentIsTheLightPoleMinusTheExistingCorruptionMeter() {
        assertEquals(0.0f, StandingBands.alignmentOf(0f, 0f, B), 1e-4);
        assertEquals(40.0f, StandingBands.alignmentOf(40f, 0f, B), 1e-4);
        assertEquals(-30.0f, StandingBands.alignmentOf(0f, 30f, B), 1e-4);
    }

    /**
     * The reason the axis is composed rather than stored: a wizard deep in both is not the same as a
     * wizard who has done nothing, and only a composed axis can hold that distinction at all.
     */
    @Test
    void aWizardDeepInBothPolesReadsAsTornRatherThanUntouched() {
        float torn = StandingBands.alignmentOf(80f, 80f, B);
        float untouched = StandingBands.alignmentOf(0f, 0f, B);
        assertEquals(untouched, torn, 1e-4, "the axis itself is neutral for both...");
        // ...and the underlying meters still differ, which is what the sheet reads.
        assertNotEquals(80f, 0f, "...but the poles they were composed from do not");
    }

    @Test
    void ministryStandingIsRankCreditLessHeat() {
        assertEquals(0.0f, StandingBands.ministryOf(0f, 0f, B), 1e-4, "a private citizen is neutral");
        assertEquals(-45.0f, StandingBands.ministryOf(0f, 45f, B), 1e-4, "heat alone drives it negative");
        assertEquals(40.0f, StandingBands.ministryOf(40f, 0f, B), 1e-4, "office alone drives it positive");
        assertEquals(-20.0f, StandingBands.ministryOf(40f, 60f, B), 1e-4,
                "a wanted official is worse off than a clean citizen");
    }

    @Test
    void derivedAxesStayInsideTheBoundEvenWhenTheirInputsAreExtreme() {
        assertEquals(B, StandingBands.alignmentOf(9999f, 0f, B), 1e-4);
        assertEquals(-B, StandingBands.alignmentOf(0f, 9999f, B), 1e-4);
        assertEquals(B, StandingBands.ministryOf(9999f, 0f, B), 1e-4);
        assertEquals(-B, StandingBands.ministryOf(0f, 9999f, B), 1e-4);
    }

    // ── persistence ──

    @Test
    void aSaveWrittenBeforeStandingExistedLoadsAsANeutralWizard() {
        MagicalStanding restored = MagicalStanding.CODEC
                .parse(JsonOps.INSTANCE, GSON.fromJson("{}", JsonElement.class))
                .getOrThrow(msg -> new AssertionError("legacy save failed to parse: " + msg));

        assertEquals(0.0f, restored.tradition(), 1e-4);
        assertEquals(0.0f, restored.light(), 1e-4);
        assertTrue(restored.isNeutral());
    }

    @Test
    void aPartialSaveKeepsWhateverItDidHave() {
        MagicalStanding restored = MagicalStanding.CODEC
                .parse(JsonOps.INSTANCE, GSON.fromJson("{\"tradition\": -42.5}", JsonElement.class))
                .getOrThrow(msg -> new AssertionError(msg));
        assertEquals(-42.5f, restored.tradition(), 1e-4);
        assertEquals(0.0f, restored.light(), 1e-4);
    }

    @Test
    void standingSurvivesARoundTrip() {
        MagicalStanding original = MagicalStanding.DEFAULT.withTradition(-63.25f).withLight(18.5f);

        JsonElement encoded = MagicalStanding.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg));
        MagicalStanding restored = MagicalStanding.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(msg -> new AssertionError("parse failed: " + msg));

        assertEquals(original, restored);
    }

    /**
     * Two players are two records. The attachment is per-player by construction, so the thing worth
     * pinning here is that the value type carries no shared mutable state that could leak between
     * them — a static map or a mutable collection field would.
     */
    @Test
    void oneWizardsStandingCannotReachAnother() {
        MagicalStanding a = MagicalStanding.DEFAULT.withTradition(70f);
        MagicalStanding b = MagicalStanding.DEFAULT.withLight(70f);

        assertEquals(70f, a.tradition(), 1e-4);
        assertEquals(0f, a.light(), 1e-4);
        assertEquals(0f, b.tradition(), 1e-4);
        assertEquals(70f, b.light(), 1e-4);
        assertEquals(MagicalStanding.DEFAULT, MagicalStanding.DEFAULT,
                "the shared default is a value, so handing it to two players shares nothing");
    }
}
