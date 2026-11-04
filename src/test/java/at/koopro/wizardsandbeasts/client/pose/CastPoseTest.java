package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The cast phase blend, the hold semantics, and the clear signal. */
class CastPoseTest {

    private static ClientCastAnimationState.ActiveCast running(int ticks) {
        return new ClientCastAnimationState.ActiveCast("test", ticks, 0.35f, 0.6f, 0L,
                SpellCastAnimationS2CPayload.NO_HOLD);
    }

    private static ClientCastAnimationState.ActiveCast held(int phase) {
        return new ClientCastAnimationState.ActiveCast("test", 1, 0.35f, 0.6f, 0L, phase);
    }

    @Test
    void theBlendWalksNeutralThroughWindupReleaseAndBackToNeutral() {
        var cast = running(20);

        assertEquals(CastPoseConstants.NEUTRAL, CastPoseConstants.at(cast, 0f),
                "a cast starts from neutral");
        assertEquals(CastPoseConstants.WINDUP, CastPoseConstants.at(cast, 0.35f),
                "the end of wind-up is the wind-up pose");
        assertEquals(CastPoseConstants.RELEASE, CastPoseConstants.at(cast, 0.6f),
                "the end of release is the release pose");
        assertEquals(CastPoseConstants.NEUTRAL, CastPoseConstants.at(cast, 1f),
                "recovery lands on neutral — the end of a cast is not casting");
    }

    /** Mid-phase must actually interpolate, or the pose snaps between three static frames. */
    @Test
    void midPhaseIsBetweenItsEndpoints() {
        var cast = running(20);
        float mid = CastPoseConstants.at(cast, 0.175f).castingArm().xRot();
        float end = CastPoseConstants.WINDUP.castingArm().xRot();
        assertTrue(mid < 0f && mid > end,
                "halfway into wind-up should be between neutral and the wind-up pose, got " + mid);
    }

    /**
     * The performance reads as gather, strike, settle: the wind-up lifts the arm above the strike, the strike points
     * level along the look, and recovery falls back below it. (The 2026-08 placeholder had the opposite shape, a small
     * forward swing before a deeper one, and read as a recoil.)
     */
    @Test
    void windupGathersAboveALevelStrikeAndRecoveryFallsBelowIt() {
        float windup = CastPoseConstants.WINDUP.castingArm().xRot();
        float strike = CastPoseConstants.RELEASE.castingArm().xRot();
        float recovery = CastPoseConstants.RECOVERY.castingArm().xRot();
        assertTrue(windup < strike, "the wind-up must raise the arm above the strike");
        assertTrue(Math.abs(strike + 90f) <= 10f, "the strike must point level, give or take the look");
        assertTrue(recovery > strike, "recovery must fall back from the strike");
        assertTrue(CastPoseConstants.RELEASE.aim() == 1f, "the strike must follow the look entirely");
        assertTrue(CastPoseConstants.WINDUP.aim() == 0f, "the gather must not follow the look");
    }

    /** G-1: Wingardium Leviosa alone plays the book's swish and flick; every other spell plays the default. */
    @Test
    void leviosaSwishesAcrossThenFlicksUp() {
        assertTrue(CastPoseConstants.setFor("wizards_and_beasts:wingardium_leviosa") == CastPoseConstants.LEVIOSA);
        assertTrue(CastPoseConstants.setFor("wizards_and_beasts:stupefy") == CastPoseConstants.DEFAULT);
        var leviosa = CastPoseConstants.LEVIOSA;
        assertTrue(leviosa.release().castingArm().yRot() - leviosa.windup().castingArm().yRot() >= 45f,
                "the swish must sweep the arm across");
        assertTrue(leviosa.recovery().castingArm().xRot() < leviosa.release().castingArm().xRot(),
                "the flick must lift the wand above the swish");
    }

    /** D4: the pose layer owns the arm. Legs and the off arm are not this pass's business. */
    @Test
    void theTableOnlyMovesTheArmAndSupportingBody() {
        for (var pose : new CastPoseConstants.CastPose[]{
                CastPoseConstants.WINDUP, CastPoseConstants.RELEASE, CastPoseConstants.RECOVERY}) {
            assertNotEquals(0f, pose.castingArm().xRot(), "every phase must move the casting arm");
            assertEquals(0f, pose.chest().zRot(), 1e-5f,
                    "the chest twists in yaw only — a roll here reads as a stagger");
        }
    }

    // ── hold semantics ───────────────────────────────────────────────────────

    /** A held cast parks inside its phase and never advances, whatever the clock does. */
    @Test
    void aHeldCastDoesNotAdvance() {
        var cast = held(1);
        assertTrue(cast.held());
        float early = cast.progress(0L, 0f);
        float late = cast.progress(9999L, 0.5f);
        assertEquals(early, late, 1e-6f, "a held cast must not drift with the clock");
    }

    /** Holding a phase must land inside that phase, or the command shows the wrong pose. */
    @Test
    void eachHeldPhaseLandsInsideItself() {
        var windup = held(0);
        float p = windup.progress(0L, 0f);
        assertTrue(p > 0f && p <= windup.windupEnd(), "wind-up hold outside its window: " + p);

        var release = held(1);
        p = release.progress(0L, 0f);
        assertTrue(p > release.windupEnd() && p < release.releaseEnd(),
                "release hold outside its window: " + p);

        var recovery = held(2);
        p = recovery.progress(0L, 0f);
        assertTrue(p > recovery.releaseEnd() && p <= 1f, "recovery hold outside its window: " + p);
    }

    /** Held casts never expire — they exist to be looked at for as long as it takes. */
    @Test
    void aHeldCastNeverExpires() {
        assertFalse(held(1).expired(100_000L));
        assertTrue(running(20).expired(20L), "a running cast still expires normally");
    }

    // ── wire behaviour ───────────────────────────────────────────────────────

    /** Zero ticks is the clear signal, so "off" needs no second payload type. */
    @Test
    void aZeroTickPayloadClearsRatherThanStoringADegenerateCast() {
        ClientCastAnimationState.clear();
        ClientCastAnimationState.handle(new SpellCastAnimationS2CPayload(
                7, "test", 20, 0.35f, 0.6f, SpellCastAnimationS2CPayload.NO_HOLD), 0L);
        assertEquals(1, ClientCastAnimationState.size());

        ClientCastAnimationState.handle(new SpellCastAnimationS2CPayload(
                7, "test", 0, 0f, 0f, SpellCastAnimationS2CPayload.NO_HOLD), 0L);
        assertEquals(0, ClientCastAnimationState.size(), "zero ticks must clear the entry");
    }

    /** A cast that leaves render distance is never read again, so the tick sweep has to drop it. */
    @Test
    void finishedCastsAreSweptEvenIfNeverRead() {
        ClientCastAnimationState.clear();
        ClientCastAnimationState.handle(new SpellCastAnimationS2CPayload(
                9, "test", 10, 0.35f, 0.6f, SpellCastAnimationS2CPayload.NO_HOLD), 0L);
        ClientCastAnimationState.tick(5L);
        assertEquals(1, ClientCastAnimationState.size(), "still running at tick 5");
        ClientCastAnimationState.tick(10L);
        assertEquals(0, ClientCastAnimationState.size(), "swept once finished");
    }

    @Test
    void sitsInTheCastingBand() {
        assertDoesNotThrow(() -> PoseBand.require(CastPosePass.PRIORITY, "cast"));
        assertTrue(CastPosePass.PRIORITY > FlightPosePass.PRIORITY,
                "a cast must read over a flight attitude, not under it");
    }
}
