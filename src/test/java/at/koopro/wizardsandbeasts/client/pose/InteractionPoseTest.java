package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.pose.PlayerGesture;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Player Animation Renaissance P2/P3: gesture clips, the derived holds' timing, falling and landing. */
class InteractionPoseTest {

    @Test
    void everyGestureHasAWayToPlayAndAUniqueWireId() {
        Set<String> ids = new HashSet<>();
        for (PlayerGesture gesture : PlayerGesture.values()) {
            assertTrue(ids.add(gesture.id()), "duplicate id " + gesture.id());
            assertSame(gesture, PlayerGesture.byId(gesture.id()));
            assertTrue(gesture.ticks() > 0);
            if (gesture != PlayerGesture.WAND_INSPECT) {
                assertNotNull(GesturePoseConstants.clip(gesture), gesture + " has no clip");
            }
        }
    }

    @Test
    void clipsAreOrderedStartAtZeroAndEndAtOne() {
        for (PlayerGesture gesture : PlayerGesture.values()) {
            GesturePoseConstants.Clip clip = GesturePoseConstants.clip(gesture);
            if (clip == null) {
                continue;
            }
            var keys = clip.keys();
            assertEquals(0f, keys[0].t(), 1e-6f, gesture + " does not start at 0");
            assertEquals(1f, keys[keys.length - 1].t(), 1e-6f, gesture + " does not end at 1");
            for (int i = 1; i < keys.length; i++) {
                assertTrue(keys[i].t() > keys[i - 1].t(), gesture + " keys out of order");
            }
        }
    }

    @Test
    void sampledClipsAreContinuousAtEveryKey() {
        // A sample just either side of each key must agree: a jump there is a one-frame snap in the game.
        for (PlayerGesture gesture : PlayerGesture.values()) {
            GesturePoseConstants.Clip clip = GesturePoseConstants.clip(gesture);
            if (clip == null) {
                continue;
            }
            for (var key : clip.keys()) {
                var before = clip.at(Math.max(0f, key.t() - 1e-4f));
                var after = clip.at(Math.min(1f, key.t() + 1e-4f));
                assertEquals(before.main().xRot(), after.main().xRot(), 0.5f, gesture + " jumps at " + key.t());
                assertEquals(before.lean(), after.lean(), 0.01f, gesture + " lean jumps at " + key.t());
            }
        }
    }

    @Test
    void theStabRisesBeforeItFalls() {
        GesturePoseConstants.Clip stab = GesturePoseConstants.clip(PlayerGesture.STAB);
        assertTrue(stab.at(0.35f).main().xRot() < stab.at(0f).main().xRot(), "the blade never went up");
        assertTrue(stab.at(0.6f).main().xRot() > stab.at(0.35f).main().xRot(), "the blade never came down");
        assertTrue(stab.at(0.6f).lean() > 0f, "a downward stab with no lean behind it");
    }

    @Test
    void aPageTurnsOncePerPeriodAndRestsBetween() {
        assertEquals(0f, ItemUsePosePass.pageFlick(0f), 1e-6f);
        assertTrue(ItemUsePosePass.pageFlick(ItemUsePoseConstants.PAGE_TICKS * 0.125f) > 0.9f);
        assertEquals(0f, ItemUsePosePass.pageFlick(ItemUsePoseConstants.PAGE_TICKS * 0.6f), 1e-6f);
        assertEquals(ItemUsePosePass.pageFlick(3f), ItemUsePosePass.pageFlick(3f + ItemUsePoseConstants.PAGE_TICKS),
                1e-5f, "the page turn drifts");
    }

    @Test
    void aDrinkTipsTheHeadBackAndAnEatDoesNot() {
        assertTrue(ItemUsePoseConstants.DRINK.headPerMotion() < 0f, "a drink must tip the head back");
        assertTrue(ItemUsePoseConstants.DRINK.armYRot() > 0f, "the cup must come in toward the mouth");
        assertTrue(ItemUsePoseConstants.EAT.headXRot() >= 0f);
    }

    @Test
    void aLandingDropsFastRisesSlowAndEnds() {
        assertEquals(0f, MovementPosePass.landBend(0f), 1e-6f);
        assertEquals(1f, MovementPosePass.landBend(2f), 1e-6f, "the bend should bottom out quickly");
        assertTrue(MovementPosePass.landBend(5f) > 0f && MovementPosePass.landBend(5f) < 1f);
        assertEquals(0f, MovementPosePass.landBend(MovementPosePass.LAND_TICKS), 1e-6f);
        assertEquals(0f, MovementPosePass.landBend(-1f), 1e-6f);
    }

    @Test
    void movementSitsUnderTheCast() {
        assertEquals(PoseBand.LOCOMOTION, PoseBand.require(MovementPosePass.PRIORITY, "movement"));
        assertTrue(MovementPosePass.PRIORITY < CastPosePass.PRIORITY);
        assertTrue(MovementPosePass.FALL_FROM > 0.42f, "a jump's own fall would read as a fall");
    }
}
