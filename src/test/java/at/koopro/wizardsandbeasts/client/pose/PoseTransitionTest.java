package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Player Animation Renaissance P0: no pose may cut in or out in one frame.
 *
 * <p>Each test asserts the share at the moment of a change (where a cut would show) and after the blend window.
 */
class PoseTransitionTest {

    private static final int ID = 7;

    @AfterEach
    void reset() {
        ClientCastAnimationState.clear();
    }

    private static SpellCastAnimationS2CPayload heldWindup() {
        return new SpellCastAnimationS2CPayload(ID, "test", 1, 0.35f, 0.6f, 0);
    }

    private static SpellCastAnimationS2CPayload strike() {
        return new SpellCastAnimationS2CPayload(ID, "test", 10, 0f, 0.3f, SpellCastAnimationS2CPayload.NO_HOLD);
    }

    private static SpellCastAnimationS2CPayload off() {
        return new SpellCastAnimationS2CPayload(ID, "", 0, 0f, 0f, SpellCastAnimationS2CPayload.NO_HOLD);
    }

    // ── casts ────────────────────────────────────────────────────────────────

    @Test
    void aHeldGatherRisesInsteadOfAppearing() {
        ClientCastAnimationState.handle(heldWindup(), 100L, 100.0);

        var atStart = ClientCastAnimationState.resolve(ID, 100L, 100.0);
        assertNotNull(atStart);
        assertEquals(0f, atStart.weight(), 1e-6f, "the gather was on screen the frame the key went down");

        var midway = ClientCastAnimationState.resolve(ID, 102L, 102.0);
        assertTrue(midway.weight() > 0.2f && midway.weight() < 0.8f, "midway share " + midway.weight());

        var settled = ClientCastAnimationState.resolve(ID, 104L, 104.0 + ClientCastAnimationState.HOLD_BLEND_TICKS);
        assertEquals(1f, settled.weight(), 1e-6f, "a held gather settles at full weight");
    }

    @Test
    void anInterruptedGatherLetsGoOfTheLastPose() {
        ClientCastAnimationState.handle(heldWindup(), 100L, 100.0);
        ClientCastAnimationState.handle(off(), 110L, 110.0);

        var atOff = ClientCastAnimationState.resolve(ID, 110L, 110.0);
        assertNotNull(atOff, "an off dropped the arm in one frame");
        assertEquals(1f, atOff.weight(), 1e-6f);
        assertNull(atOff.cast(), "letting go is not a running cast");
        assertEquals(ClientCastAnimationState.resolve(ID, 110L, 110.5).pose(), atOff.pose(),
                "the pose held while letting go is the one that was on screen");

        assertNull(ClientCastAnimationState.resolve(ID, 115L, 110.0 + ClientCastAnimationState.OFF_BLEND_TICKS),
                "the let-go ends");
    }

    @Test
    void anOffWithNothingShowingStaysSilent() {
        ClientCastAnimationState.handle(off(), 50L, 50.0);
        assertNull(ClientCastAnimationState.resolve(ID, 50L, 50.0));
    }

    @Test
    void theStrikeTakesOverFromTheGatherWithoutAJump() {
        ClientCastAnimationState.handle(heldWindup(), 100L, 100.0);
        var gather = ClientCastAnimationState.resolve(ID, 120L, 120.0);
        ClientCastAnimationState.handle(strike(), 120L, 120.0);
        var handover = ClientCastAnimationState.resolve(ID, 120L, 120.0);

        assertEquals(gather.weight(), handover.weight(), 1e-5f);
        assertEquals(gather.pose().castingArm().xRot(), handover.pose().castingArm().xRot(), 1e-3f,
                "the arm jumped at the strike handover");
    }

    @Test
    void smoothstepHasFlatEnds() {
        assertEquals(0f, ClientCastAnimationState.smoothstep(-1f));
        assertEquals(0.5f, ClientCastAnimationState.smoothstep(0.5f), 1e-6f);
        assertEquals(1f, ClientCastAnimationState.smoothstep(2f));
        assertTrue(ClientCastAnimationState.smoothstep(0.05f) < 0.05f, "no velocity jump at the start");
    }

    // ── derived holds ────────────────────────────────────────────────────────

    private enum K { A, B }

    @Test
    void aHoldRisesAndFalls() {
        KindFade<K> fade = new KindFade<>(4, 2);
        assertEquals(0f, fade.observe(1, K.A, 0.0).weight(), 1e-6f, "the hold cut in");
        assertEquals(1f, fade.observe(1, K.A, 4.0).weight(), 1e-6f);

        var letting = fade.observe(1, null, 10.0);
        assertEquals(K.A, letting.previous());
        assertEquals(1f, letting.fall(), 1e-6f, "the hold cut out");
        assertEquals(0f, fade.observe(1, null, 12.0).fall(), 1e-6f);
        assertTrue(fade.observe(1, null, 12.0).idle());
    }

    @Test
    void switchingHoldsCrossfades() {
        KindFade<K> fade = new KindFade<>(4, 4);
        fade.observe(1, K.A, 0.0);
        var swap = fade.observe(1, K.B, 10.0);
        assertEquals(K.B, swap.current());
        assertEquals(K.A, swap.previous());
        assertEquals(1f, swap.fall(), 1e-6f);
        assertEquals(0f, swap.weight(), 1e-6f);
    }

    @Test
    void comingBackMidLetGoResumesFromWhereItWas() {
        KindFade<K> fade = new KindFade<>(4, 4);
        fade.observe(1, K.A, 0.0);
        fade.observe(1, null, 10.0);
        float shown = fade.observe(1, null, 12.0).fall();
        var back = fade.observe(1, K.A, 12.0);
        assertEquals(shown, back.weight(), 1e-6f, "the hold restarted from nothing");
    }

    @Test
    void observingTwiceInOneFrameChangesNothing() {
        KindFade<K> fade = new KindFade<>(4, 4);
        fade.observe(1, K.A, 0.0);
        var first = fade.observe(1, K.A, 2.0);
        var second = fade.observe(1, K.A, 2.0);
        assertEquals(first, second);
    }

    @Test
    void entitiesAreTrackedApartAndSwept() {
        KindFade<K> fade = new KindFade<>(4, 4);
        fade.observe(1, K.A, 0.0);
        fade.observe(2, K.B, 0.0);
        assertEquals(K.A, fade.observe(1, K.A, 1.0).current());
        assertEquals(K.B, fade.observe(2, K.B, 1.0).current());
        fade.sweep(500.0);
        assertEquals(0, fade.size(), "players gone from view are forgotten");
    }

    @Test
    void opWeightIsStampedOnQueuedOps() {
        PoseBuilder builder = new PoseBuilder();
        builder.opWeight(0.25f);
        builder.get(PlayerModelPart.HEAD).setRotDeg(PoseTarget.X_ROT, 10f);
        builder.opWeight(1f);
        builder.get(PlayerModelPart.HEAD).setRotDeg(PoseTarget.X_ROT, 20f);
        var ops = builder.parts().get(PlayerModelPart.HEAD).ops();
        assertEquals(0.25f, ops.get(0).weight(), 1e-6f);
        assertEquals(1f, ops.get(1).weight(), 1e-6f);
        builder.clear();
        assertEquals(1f, builder.opWeight(), 1e-6f, "clear must reset the op weight for the next pass");
    }
}
