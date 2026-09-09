package at.koopro.wizardsandbeasts.creature.ability;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The size search behind the Occamy's choranaptyxis.
 *
 * <p>The interesting half of "it grows or shrinks to fill the space" is the refusal: a creature
 * that grows into a wall suffocates on its own ability. {@link OccamyChoranaptyxis#largestFittingScale}
 * is the guard, and it is deliberately level-free — it takes a probe — so this can assert the
 * decision itself rather than standing a world up around it.
 */
class OccamyChoranaptyxisTest {

    private static final float FLOOR = 0.35f;
    private static final float CEILING = 2.2f;

    /** A probe that accepts anything at or below a ceiling, recording what it was asked. */
    private static OccamyChoranaptyxis.FitProbe upTo(float limit, List<Float> asked) {
        return scale -> {
            asked.add(scale);
            return scale <= limit;
        };
    }

    @Test
    void takesWhatItWantsWhenTheSpaceAllowsIt() {
        float got = OccamyChoranaptyxis.largestFittingScale(
                FLOOR, CEILING, OccamyChoranaptyxis.PROBE_STEPS, scale -> true);
        assertEquals(CEILING, got, 1.0e-4f, "nothing was in the way, so it should be at its maximum");
    }

    @Test
    void neverReturnsMoreThanItWasAskedFor() {
        // Even in a cathedral, a calm Occamy wants its natural size and must not exceed it.
        float got = OccamyChoranaptyxis.largestFittingScale(
                FLOOR, 1.0f, OccamyChoranaptyxis.PROBE_STEPS, scale -> true);
        assertEquals(1.0f, got, 1.0e-4f);
    }

    @Test
    void takesTheLargestSizeThatActuallyFits() {
        List<Float> asked = new ArrayList<>();
        float got = OccamyChoranaptyxis.largestFittingScale(
                FLOOR, CEILING, OccamyChoranaptyxis.PROBE_STEPS, upTo(1.2f, asked));

        assertTrue(got <= 1.2f, "returned " + got + ", which the probe said does not fit");
        assertTrue(got > 1.0f, "returned " + got + "; a larger candidate below 1.2 was available");
        // It must walk down from what it wants, not up from the floor: the first candidate
        // offered is the wanted size, so an unobstructed creature settles in one probe.
        assertEquals(CEILING, asked.get(0), 1.0e-4f);
    }

    @Test
    void fallsBackToTheFloorWhenNothingFits() {
        // Walled in. The floor is the smallest it is willing to be, not a claim that it fits;
        // from there it is in vanilla's suffocation like any other mob in a wall.
        float got = OccamyChoranaptyxis.largestFittingScale(
                FLOOR, CEILING, OccamyChoranaptyxis.PROBE_STEPS, scale -> false);
        assertEquals(FLOOR, got, 1.0e-4f);
    }

    @Test
    void neverReturnsBelowTheFloorEvenWhenAskedTo() {
        // A datapack may set calm_scale below min_scale; the floor still wins.
        float got = OccamyChoranaptyxis.largestFittingScale(
                FLOOR, 0.1f, OccamyChoranaptyxis.PROBE_STEPS, scale -> true);
        assertEquals(FLOOR, got, 1.0e-4f);
    }

    @Test
    void everyCandidateItOffersIsInsideTheDeclaredRange() {
        List<Float> asked = new ArrayList<>();
        OccamyChoranaptyxis.largestFittingScale(
                FLOOR, CEILING, OccamyChoranaptyxis.PROBE_STEPS, upTo(-1f, asked));

        assertEquals(OccamyChoranaptyxis.PROBE_STEPS, asked.size(), "should try every step before giving up");
        for (float candidate : asked) {
            assertTrue(candidate >= FLOOR && candidate <= CEILING,
                    "probed " + candidate + ", outside [" + FLOOR + ", " + CEILING + "]");
        }
    }

    @Test
    void shipsTheDefaultsTheDatapackDeclares() {
        // The record's own defaults are what a datapack omitting the fields gets, so they are
        // part of the contract rather than an implementation detail.
        OccamyChoranaptyxis defaults = new OccamyChoranaptyxis(0.35f, 2.2f, 1.0f, 0.06f);
        assertTrue(defaults.minScale() < defaults.calmScale(), "calm size must be above the floor");
        assertTrue(defaults.maxScale() > defaults.calmScale(), "roused size must be above calm");
        assertTrue(OccamyChoranaptyxis.SHRINK_URGENCY > 1.0f,
                "being crushed is a need and must beat swelling, which is a want");
    }
}
