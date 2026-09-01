package at.koopro.wizardsandbeasts.floo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic behind a mumbled address.
 *
 * <p>{@link FlooAccess#resolveSpoken} itself needs a player, an attachment and saved data, so what is
 * pinned here is the pure half it rests on: the capped edit distance that decides whether a typed
 * address was close enough to be understood as something. That number is the whole difference between
 * "nothing answers to that" and "you have arrived in Knockturn Alley", so it is worth holding still.
 *
 * <p>The cap is load-bearing rather than an optimisation, and it is why the function is allowed to
 * return a number larger than the true distance: anything over the cap is equally out of reach, and
 * saying <em>how</em> far out would mean finishing a matrix whose answer changes nothing.
 */
class FlooSpokenAddressTest {

    private static final int TOLERANCE = 2;

    @Test
    void anExactAddressIsDistanceZero() {
        assertEquals(0, FlooAccess.editDistance("the leaky cauldron", "the leaky cauldron", TOLERANCE));
    }

    @Test
    void oneDroppedLetterIsStillUnderstood() {
        // "Diagon Alle" for "Diagon Alley" - a slip, not a different place.
        assertTrue(FlooAccess.editDistance("diagon alle", "diagon alley", TOLERANCE) <= TOLERANCE,
                "a single dropped character must still connect");
    }

    @Test
    void oneTypoIsStillUnderstood() {
        assertTrue(FlooAccess.editDistance("diagom alley", "diagon alley", TOLERANCE) <= TOLERANCE,
                "a single substitution must still connect");
    }

    @Test
    void theFamousMumbleIsHeardAsSomethingButIsNotTheAddress() {
        // "Diagonally" is exactly two edits from "Diagon Alley" - insert the space, insert the 'e' -
        // so at the shipped tolerance the Network does understand it as *something*. That is the
        // whole mechanism, not a leak in it: resolveSpoken returning a near match is precisely what
        // beginTravel reads as a mumble, and a mumble is sent to the weighted misfire table rather
        // than to the address it nearly was. Harry says "Diagonally" and arrives in Knockturn Alley.
        //
        // Both halves are asserted together because either one alone is the wrong feature. Over
        // tolerance and nothing answers at all, which is a flat refusal and no journey. At distance
        // zero it would be an exact address, and the most famous misfire in the fiction would quietly
        // become a successful trip.
        int distance = FlooAccess.editDistance("diagonally", "diagon alley", TOLERANCE);
        assertTrue(distance <= TOLERANCE,
                "\"Diagonally\" must be close enough to be heard as something, or it is refused "
                        + "outright and never misfires at all");
        assertTrue(distance > 0,
                "\"Diagonally\" must not be an exact match, or it would travel cleanly to Diagon "
                        + "Alley and the mumble would have no consequence");
    }

    @Test
    void twoUnrelatedAddressesAreNeverConfused() {
        assertTrue(FlooAccess.editDistance("the burrow", "malfoy manor", TOLERANCE) > TOLERANCE,
                "unrelated addresses must not be within tolerance of each other");
    }

    @Test
    void aZeroToleranceReadsAsExactMatchOnly() {
        // resolveSpoken short-circuits before ever calling this when the tolerance is zero, but the
        // distance function must still agree: with no budget, anything but equality is out of reach.
        assertEquals(0, FlooAccess.editDistance("the burrow", "the burrow", 0));
        assertTrue(FlooAccess.editDistance("the burro", "the burrow", 0) > 0);
    }

    @Test
    void aLengthGapWiderThanTheCapIsRejectedWithoutMeasuring() {
        // The pre-check is what keeps a long network affordable: two strings that differ in length by
        // more than the cap cannot possibly come in under it, whatever their contents.
        int distance = FlooAccess.editDistance("a", "a very long address indeed", TOLERANCE);
        assertTrue(distance > TOLERANCE, "a length gap alone must rule a candidate out");
    }

    @Test
    void distanceIsSymmetric() {
        // Not decorative: resolveSpoken always passes the typed address first, and a function that
        // scored differently by argument order would make the result depend on which side was typed.
        assertEquals(FlooAccess.editDistance("diagon alle", "diagon alley", TOLERANCE),
                FlooAccess.editDistance("diagon alley", "diagon alle", TOLERANCE));
    }

    @Test
    void anEmptyAddressIsNeverCloseToAnything() {
        assertTrue(FlooAccess.editDistance("", "the burrow", TOLERANCE) > TOLERANCE);
    }
}
