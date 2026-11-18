package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.network.spell.SpellCastAnimationS2CPayload;
import at.koopro.wizardsandbeasts.pose.PlayerGesture;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Player Animation Renaissance P1: the wand's pose sets, the charge, the inspect timeline and the gesture wire. */
class WandGestureTest {

    @Test
    void theFourG1OverridesAndProtegoResolveByIdWithOrWithoutNamespace() {
        assertSame(CastPoseConstants.LEVIOSA, CastPoseConstants.setFor("wizards_and_beasts:wingardium_leviosa"));
        assertSame(CastPoseConstants.EXPELLIARMUS, CastPoseConstants.setFor("expelliarmus"));
        assertSame(CastPoseConstants.PATRONUS, CastPoseConstants.setFor("wizards_and_beasts:expecto_patronum"));
        assertSame(CastPoseConstants.AVADA_KEDAVRA, CastPoseConstants.setFor("avada_kedavra"));
        assertSame(CastPoseConstants.PROTEGO, CastPoseConstants.setFor("wizards_and_beasts:protego"));
    }

    @Test
    void everyCategoryHasItsOwnSetAndUnknownFallsBackToCombat() {
        Set<CastPoseConstants.PoseSet> seen = new HashSet<>();
        for (SpellCategory category : SpellCategory.values()) {
            assertTrue(seen.add(CastPoseConstants.forCategory(category)), category + " shares a set");
        }
        assertSame(CastPoseConstants.DEFAULT, CastPoseConstants.forCategory(null));
        assertSame(CastPoseConstants.DEFAULT, CastPoseConstants.setFor(""));
    }

    @Test
    void theFreeArmDoesNotSwingInFromStraightDown() {
        // A one-armed pose blending into a two-armed one: the free arm's angles must already be the target's, so the
        // only thing that grows is its share.
        CastPoseConstants.CastPose blended =
                CastPoseConstants.NEUTRAL.lerp(CastPoseConstants.PROTEGO.windup(), 0.1f);
        assertEquals(CastPoseConstants.PROTEGO.windup().offArm(), blended.offArm());
        assertEquals(0.1f, blended.offShare(), 1e-6f);
    }

    @Test
    void theGuardRaisesTheFreeHandInwardForARightHandedCaster() {
        // Written as the left arm: inward is positive yRot.
        assertTrue(CastPoseConstants.PROTEGO.windup().offArm().yRot() > 0f);
        assertTrue(CastPoseConstants.DEFENSE.windup().offArm().yRot() > 0f);
        // The Patronus opens outward: negative zRot on a left arm.
        assertTrue(CastPoseConstants.PATRONUS.release().offArm().zRot() < 0f);
    }

    @Test
    void mountedCastsKeepTheBroomGripAndTwistLess() {
        CastPoseConstants.CastPose mounted = CastPoseConstants.PATRONUS.release().mounted(0.35f);
        assertEquals(0f, mounted.offShare(), "the free hand let go of the broom");
        assertEquals(CastPoseConstants.PATRONUS.release().chest().yRot() * 0.35f, mounted.chest().yRot(), 1e-5f);
    }

    @Test
    void onlyAHeldGatherCharges() {
        var gather = new ClientCastAnimationState.ActiveCast("x", 1, 0.35f, 0.6f, 100L, 0);
        var beam = new ClientCastAnimationState.ActiveCast("x", 1, 0.35f, 0.6f, 100L, 1);
        var timed = new ClientCastAnimationState.ActiveCast("x", 10, 0f, 0.3f, 100L,
                SpellCastAnimationS2CPayload.NO_HOLD);
        assertEquals(0f, CastPosePass.charge(gather, 100.0), 1e-6f);
        assertEquals(0.5f, CastPosePass.charge(gather, 100.0 + CastPoseConstants.CHARGE_TICKS / 2.0), 1e-6f);
        assertEquals(1f, CastPosePass.charge(gather, 10_000.0), 1e-6f, "the charge must cap");
        assertEquals(0f, CastPosePass.charge(beam, 10_000.0), 1e-6f);
        assertEquals(0f, CastPosePass.charge(timed, 10_000.0), 1e-6f);
        assertEquals(0f, CastPosePass.charge(null, 10_000.0), 1e-6f);
    }

    @Test
    void theInspectStartsAndEndsAtTheReadyHoldAndTurnsInTheMiddle() {
        var start = GesturePoseConstants.inspectAt(0f);
        var end = GesturePoseConstants.inspectAt(1f);
        var middle = GesturePoseConstants.inspectAt(0.5f);
        assertEquals(GesturePoseConstants.INSPECT_DOWN, start.wandArm(), "the inspect jumped up on its first frame");
        assertEquals(GesturePoseConstants.INSPECT_DOWN, end.wandArm(), "the inspect ended mid-air");
        assertTrue(middle.wandArm().xRot() < start.wandArm().xRot(), "the wand never came up");
        assertEquals(0f, start.freeShare(), 1e-6f);
        assertEquals(0f, end.freeShare(), 1e-6f);
        assertEquals(0f, GesturePoseConstants.inspectRoll(0f), 1e-4f);
        assertEquals(0f, GesturePoseConstants.inspectRoll(1f), 1e-4f);
        assertTrue(GesturePoseConstants.inspectRoll(0.5f) > 90f, "the wand never turned");
    }

    @Test
    void gesturesTravelByIdAndAStopLetsGoOnlyOfItsOwnGesture() {
        assertSame(PlayerGesture.WAND_INSPECT, PlayerGesture.byId("wand_inspect"));
        assertNull(PlayerGesture.byId("nope"));

        ClientGestureState.clear();
        ClientGestureState.handle(new at.koopro.wizardsandbeasts.network.pose.PlayerGestureS2CPayload(
                3, "wand_inspect", 50), 10L);
        assertNotNull(ClientGestureState.get(3, 20L));
        assertNull(ClientGestureState.get(3, 60L), "a gesture ends on its length");

        ClientGestureState.handle(new at.koopro.wizardsandbeasts.network.pose.PlayerGestureS2CPayload(
                3, "wand_inspect", 50), 100L);
        ClientGestureState.handle(new at.koopro.wizardsandbeasts.network.pose.PlayerGestureS2CPayload(
                3, "wand_inspect", 0), 101L);
        assertNull(ClientGestureState.get(3, 102L), "a stop must end it");
        ClientGestureState.clear();
    }

    @Test
    void theNewPassesSitInTheirBands() {
        assertEquals(PoseBand.POSTURE, PoseBand.require(WandReadyPass.PRIORITY, "ready"));
        assertEquals(PoseBand.POSTURE, PoseBand.require(GesturePosePass.PRIORITY, "gesture"));
        assertTrue(ItemUsePosePass.PRIORITY < WandReadyPass.PRIORITY);
        assertTrue(WandReadyPass.PRIORITY < GesturePosePass.PRIORITY);
        assertTrue(GesturePosePass.PRIORITY < CastPosePass.PRIORITY);
    }
}
