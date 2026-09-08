package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.item.wand.WandItem;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wand release contract, one scenario per test.
 *
 * <p>{@code SpellCastC2SPayload} is an empty packet the client sends whenever it likes, so every way a
 * cast can be duplicated, replayed or forged arrives through it. These cases used to be handled by a
 * fifteen-tick ignore window — a delay standing in for state the guard could not see, which both ate
 * genuine re-presses inside the window and let duplicates through outside it.
 *
 * <p>The names below are the scenarios, not the branches: a reader should be able to check the list is
 * complete without reading {@link CastReleaseGate}.
 */
class CastReleaseGateTest {

    /**
     * {@code WandItem.USE_DURATION_TICKS} is a compile-time constant, so naming it here is folded to a
     * literal by javac and loads no Minecraft class — but the two can no longer drift apart.
     */
    private static final long MAX = WandItem.USE_DURATION_TICKS;

    private static CastReleaseGate.Inputs alive(boolean sessionOpen, boolean consumed, long age) {
        return new CastReleaseGate.Inputs(true, sessionOpen, consumed, age, MAX);
    }

    private static CastReleaseGate.Inputs dead(boolean sessionOpen, boolean consumed, long age) {
        return new CastReleaseGate.Inputs(false, sessionOpen, consumed, age, MAX);
    }

    @Test
    void normalCast_openSessionReleasedPromptly_isAccepted() {
        assertNull(CastReleaseGate.evaluate(alive(true, false, 0L)),
                "a release on the tick the hold began is the ordinary tap-cast and must land");
        assertNull(CastReleaseGate.evaluate(alive(true, false, 40L)),
                "a two-second hold is an ordinary charged cast and must land");
    }

    @Test
    void releaseWithoutCast_hasNoSessionToLandOn() {
        assertEquals(CastReleaseGate.NO_SESSION, CastReleaseGate.evaluate(alive(false, false, 0L)));
    }

    @Test
    void duplicateRelease_spendsOneTokenOnly() {
        assertNull(CastReleaseGate.evaluate(alive(true, false, 5L)));
        assertEquals(CastReleaseGate.ALREADY_RELEASED, CastReleaseGate.evaluate(alive(true, true, 5L)),
                "the second release of one hold must not cast, stamp a cooldown or bump a counter again");
    }

    /**
     * Avada ends its own channel on the kill by calling the release path server-side. The player is
     * still holding the button, so their own release packet arrives afterwards — and must be refused
     * however long afterwards it is, which the old fixed window could not promise.
     */
    @Test
    void clientReleaseAfterAServerDrivenOne_isRefusedAtAnyDelay() {
        assertEquals(CastReleaseGate.ALREADY_RELEASED, CastReleaseGate.evaluate(alive(true, true, 1L)));
        assertEquals(CastReleaseGate.ALREADY_RELEASED, CastReleaseGate.evaluate(alive(true, true, 16L)),
                "one tick past the old fifteen-tick window, where a duplicate used to be let through");
        assertEquals(CastReleaseGate.ALREADY_RELEASED, CastReleaseGate.evaluate(alive(true, true, 600L)));
    }

    @Test
    void releaseAfterDeath_neverCastsFromACorpse() {
        assertEquals(CastReleaseGate.CASTER_NOT_ALIVE, CastReleaseGate.evaluate(dead(true, false, 3L)));
    }

    /**
     * Death outranks the session checks so the verdict does not depend on which lifecycle listener ran
     * first — the death hook aborting the session and the client's dying release can arrive either way
     * round.
     */
    @Test
    void deathOutranksEverySessionState() {
        assertEquals(CastReleaseGate.CASTER_NOT_ALIVE, CastReleaseGate.evaluate(dead(false, false, 0L)));
        assertEquals(CastReleaseGate.CASTER_NOT_ALIVE, CastReleaseGate.evaluate(dead(true, true, 0L)));
        assertEquals(CastReleaseGate.CASTER_NOT_ALIVE, CastReleaseGate.evaluate(dead(true, false, MAX + 1)));
    }

    /**
     * Cancellation, respawn, dimension change and an admin reset all abort the session rather than
     * marking it released, so a packet still in flight meets IDLE.
     */
    @Test
    void releaseAfterCancellation_meetsAnAbortedSession() {
        assertEquals(CastReleaseGate.NO_SESSION, CastReleaseGate.evaluate(alive(false, false, 12L)));
    }

    @Test
    void sessionOlderThanTheWandsUseDuration_expires() {
        assertNull(CastReleaseGate.evaluate(alive(true, false, MAX)),
                "at exactly the use duration the hold is still the one vanilla is about to force-release");
        assertEquals(CastReleaseGate.SESSION_EXPIRED, CastReleaseGate.evaluate(alive(true, false, MAX + 1)));
    }

    @Test
    void negativeSessionAge_isAnImpossibleStateAndIsRefused() {
        assertEquals(CastReleaseGate.SESSION_EXPIRED, CastReleaseGate.evaluate(alive(true, false, -1L)));
    }

    /**
     * Rapid input: press, release, press, release. Each press opens a fresh session with an unspent
     * token, so the second cast is accepted — the case the old ignore window silently ate when the
     * first release had been server-driven.
     */
    @Test
    void rapidRepress_afterAConsumedSession_castsAgain() {
        assertEquals(CastReleaseGate.ALREADY_RELEASED, CastReleaseGate.evaluate(alive(true, true, 2L)));
        // ... the re-press opens a new session, which is an unspent token again.
        assertNull(CastReleaseGate.evaluate(alive(true, false, 0L)));
    }

    @Test
    void everyVerdict_carriesADistinctRejectCode() {
        Set<String> codes = new HashSet<>();
        for (CastReleaseGate gate : CastReleaseGate.values()) {
            String code = gate.rejectCode();
            assertNotNull(code);
            assertTrue(codes.add(code),
                    "verdict " + gate + " reuses reject code '" + code
                            + "' — two different refusals would be indistinguishable in telemetry");
        }
    }

    /** Every refusal must bucket as a wand release so the reject summary does not lose it under "other". */
    @Test
    void everyVerdictsCode_bucketsAsWandRelease() {
        for (CastReleaseGate gate : CastReleaseGate.values()) {
            assertEquals("wand_release", SpellRejectCodes.summaryBucket(gate.rejectCode()),
                    "verdict " + gate + " falls outside the wand_release telemetry bucket");
        }
    }

    /** These are desync diagnostics; a player who reads "no cast session" has learned nothing. */
    @Test
    void noVerdictsCode_isEverShownToAPlayer() {
        for (CastReleaseGate gate : CastReleaseGate.values()) {
            assertNull(SpellRejectCodes.castRejectMessageKey(gate.rejectCode()),
                    "verdict " + gate + " resolves to a player-facing message; it is a diagnostic");
        }
    }
}
