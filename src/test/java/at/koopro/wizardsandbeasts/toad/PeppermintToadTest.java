package at.koopro.wizardsandbeasts.toad;

import at.koopro.wizardsandbeasts.whizzbee.Whizzbee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A novelty sweet's numbers, and the one thing that stops it quietly being useful.
 *
 * <p>{@link #theHopDividesTheDurationEvenly} is the small one worth having: the hop is driven by
 * {@code duration % HOP_INTERVAL}, so an interval that did not divide the duration would give a
 * ragged last hop — the joke landing at an odd moment rather than on the beat.
 */
class PeppermintToadTest {

    @Test
    void theBriefsNumbersAreWhatShipped() {
        assertEquals(12, PeppermintToad.DURATION_TICKS / 20);
        assertEquals(3, PeppermintToad.HOP_INTERVAL / 20);
    }

    @Test
    void theHopDividesTheDurationEvenly() {
        assertEquals(0, PeppermintToad.DURATION_TICKS % PeppermintToad.HOP_INTERVAL,
                "an interval that does not divide the duration gives a ragged last hop");
        assertEquals(4, PeppermintToad.DURATION_TICKS / PeppermintToad.HOP_INTERVAL,
                "twelve seconds at three-second intervals is four hops");
    }

    @Test
    void itStaysNoveltyRatherThanCompetingWithTheGoodSweets() {
        // A Whizzbee is the one that actually does something. If the toad ever out-lasted it at the
        // same price, nobody would carry either for the right reason.
        assertTrue(PeppermintToad.DURATION_TICKS > Whizzbee.DURATION_TICKS,
                "the toad is the longer but far weaker of the two, which is the trade");
        assertTrue(PeppermintToad.DURATION_TICKS <= 400,
                "novelty candy should not be a travel buff");
    }

    @Test
    void theHopIsOftenEnoughToBeAGagAndRareEnoughNotToNag() {
        assertTrue(PeppermintToad.HOP_INTERVAL >= 40,
                "a hop more than twice a second would be an alarm, not a joke");
        assertTrue(PeppermintToad.HOP_INTERVAL <= 100,
                "a hop rarer than every five seconds would be missed entirely");
    }
}
