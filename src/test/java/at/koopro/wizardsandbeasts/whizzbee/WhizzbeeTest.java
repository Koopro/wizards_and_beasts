package at.koopro.wizardsandbeasts.whizzbee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whizz, and the one property that makes it a sweet rather than a potion.
 *
 * <p>{@link #theWhizzAddsToAJumpRatherThanReplacingIt} is the load-bearing one. Writing
 * {@code setDeltaMovement(x, WHIZZ, z)} instead of adding would throw away Jump Boost's contribution
 * entirely — the sweet would get <em>worse</em> the better your other buffs were, and it would still
 * look like it was working.
 */
class WhizzbeeTest {

    /** Roughly a vanilla jump's initial upward velocity, for scale. */
    private static final double VANILLA_JUMP = 0.42;

    @Test
    void theBriefsNumbersAreWhatShipped() {
        assertEquals(8, Whizzbee.DURATION_TICKS / 20);
        assertEquals(1, Whizzbee.JUMP_AMPLIFIER, "Jump Boost II is amplifier 1");
    }

    @Test
    void theWhizzAddsToAJumpRatherThanReplacingIt() {
        // A boosted jump must stay bigger than an unboosted one after the whizz is applied.
        double plain = Whizzbee.whizz(VANILLA_JUMP);
        double boosted = Whizzbee.whizz(VANILLA_JUMP + 0.2);
        assertTrue(boosted > plain,
                "the whizz must compound with Jump Boost, not overwrite it");
        assertEquals(0.2, boosted - plain, 1e-9,
                "the difference between the two jumps must survive the whizz untouched");
    }

    @Test
    void theWhizzIsAKickNotASecondJump() {
        double gain = Whizzbee.whizz(VANILLA_JUMP) - VANILLA_JUMP;
        assertTrue(gain > 0.0, "a whizz that adds nothing is not a whizz");
        assertTrue(gain < VANILLA_JUMP / 2.0,
                "the kick should be a fraction of a jump; got " + gain);
    }

    @Test
    void theWhizzIsLinearSoItNeverRunsAway() {
        // Applied once per jump, but a bug that applied it twice should degrade gracefully rather
        // than launching anybody into orbit.
        double once = Whizzbee.whizz(VANILLA_JUMP);
        double twice = Whizzbee.whizz(once);
        assertEquals(Whizzbee.WHIZZ_BOOST, twice - once, 1e-9,
                "each application adds the same fixed amount");
    }

    @Test
    void aDownwardVelocityIsHelpedRatherThanIgnored() {
        // LivingJumpEvent fires with vanilla's jump already applied, so this should never see a
        // negative — but if it does, adding is still the sane answer.
        assertTrue(Whizzbee.whizz(-0.1) > -0.1);
    }

    @Test
    void theFizzIsFrequentEnoughToLookContinuous() {
        assertTrue(Whizzbee.FIZZ_INTERVAL > 0 && Whizzbee.FIZZ_INTERVAL <= 5,
                "a sputter every quarter-second or slower stops reading as continuous");
    }

    @Test
    void theFloatIsShortEnoughToStayAJoke() {
        assertTrue(Whizzbee.DURATION_TICKS <= 400,
                "twenty seconds of float would be a mobility item, not a sweet");
    }
}
