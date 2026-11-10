package at.koopro.wizardsandbeasts.entity.flight;

import at.koopro.wizardsandbeasts.creature.profile.CreatureActivity;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The rules of the shared flight framework that need no world (BEAST_RENAISSANCE §4.6): which loop a large flier
 * shows, where it will land, and when it hunts from the air.
 */
class FlightFrameworkTest {

    private static final Set<String> FULL = Set.of("idle", "walk", "fly", "glide", "sleep", "run");
    private static final Set<String> OLD = Set.of("idle", "fly");

    // ── loop ──────────────────────────────────────────────────────────────────

    @Test
    void onTheGroundItWalksInsteadOfFlapping() {
        var loop = FlightLoop.pick(true, true, 0.0, true, CreatureActivity.IDLE, FULL::contains);
        assertEquals("walk", loop.clip());
    }

    @Test
    void standingItIdles() {
        assertEquals("idle", FlightLoop.pick(true, false, 0.0, false, CreatureActivity.IDLE, FULL::contains).clip());
    }

    @Test
    void groundActivitiesShowOnTheGround() {
        assertEquals("sleep", FlightLoop.pick(true, false, 0.0, false, CreatureActivity.SLEEP, FULL::contains).clip());
        assertEquals("run", FlightLoop.pick(true, true, 0.0, true, CreatureActivity.FLEE, FULL::contains).clip());
    }

    @Test
    void inTheAirItFliesWhateverTheActivitySays() {
        var loop = FlightLoop.pick(false, true, 0.2, true, CreatureActivity.SLEEP, FULL::contains);
        assertEquals("fly", loop.clip());
        assertEquals(1.0, loop.speed());
    }

    @Test
    void sinkingOnTheMoveIsAGlide() {
        assertEquals("glide", FlightLoop.pick(false, true, -0.2, true, CreatureActivity.IDLE, FULL::contains).clip());
        // Dropping straight down is not a glide: the wings work.
        assertEquals("fly", FlightLoop.pick(false, true, -0.2, false, CreatureActivity.IDLE, FULL::contains).clip());
    }

    @Test
    void aRigWithoutTheNewClipsIsNeverAskedForThem() {
        for (boolean supported : new boolean[] {true, false}) {
            for (boolean moving : new boolean[] {true, false}) {
                for (double climb : new double[] {-0.3, 0.0, 0.3}) {
                    for (CreatureActivity activity : CreatureActivity.values()) {
                        var loop = FlightLoop.pick(supported, moving, climb, moving, activity, OLD::contains);
                        assertTrue(OLD.contains(loop.clip()),
                                () -> activity + " asked an old rig for '" + loop.clip() + "'");
                    }
                }
            }
        }
    }

    @Test
    void anOldRigWalksOnSlowedWingsNotAtFullFlap() {
        var loop = FlightLoop.pick(true, true, 0.0, true, CreatureActivity.IDLE, OLD::contains);
        assertEquals("fly", loop.clip());
        assertTrue(loop.speed() < 1.0);
    }

    // ── landing ───────────────────────────────────────────────────────────────

    @Test
    void itNeverLandsOnWaterOrATreetop() {
        assertTrue(WingedWalkerFlightGoal.landingScore(70, 70, true, false) < 0, "water");
        assertTrue(WingedWalkerFlightGoal.landingScore(78, 70, false, false) < 0, "leaves over the ground");
        assertTrue(WingedWalkerFlightGoal.landingScore(78, 70, false, true) < 0, "a percher does not take a treetop either");
        assertTrue(WingedWalkerFlightGoal.landingScore(70, 70, false, false) >= 0);
    }

    @Test
    void aPercherPrefersTheHigherGround() {
        assertTrue(WingedWalkerFlightGoal.landingScore(120, 120, false, true)
                > WingedWalkerFlightGoal.landingScore(64, 64, false, true));
        assertEquals(WingedWalkerFlightGoal.landingScore(120, 120, false, false),
                WingedWalkerFlightGoal.landingScore(64, 64, false, false), "anything else lands anywhere dry");
        assertTrue(WingedWalkerFlightGoal.landingScore(-60, -60, false, true) >= 0, "deep-world columns still count");
    }

    // ── startled takeoff ──────────────────────────────────────────────────────

    @Test
    void justHurtItLeavesByAir() {
        assertTrue(WingedWalkerFlightGoal.startled(true, 5));
        assertFalse(WingedWalkerFlightGoal.startled(true, WingedWalkerFlightGoal.STARTLED_TICKS));
        assertFalse(WingedWalkerFlightGoal.startled(false, 5), "nothing hurt it");
    }

    // ── aerial hunt ───────────────────────────────────────────────────────────

    @Test
    void aTargetOverheadOrFarOffIsHuntedFromTheAir() {
        assertTrue(WingedPursuitGoal.takesToTheAir(4.0, 2.0), "on a pillar");
        assertTrue(WingedPursuitGoal.takesToTheAir(0.0, 20.0), "across a field");
        assertFalse(WingedPursuitGoal.takesToTheAir(0.5, 5.0), "close and level is a ground fight");
    }

    @Test
    void itSettlesOnlyAfterAPassAndOnlyOnATargetItCanReach() {
        assertFalse(WingedPursuitGoal.settles(0.0, 3.0, 0), "not before its first pass");
        assertTrue(WingedPursuitGoal.settles(0.0, 3.0, 1));
        assertFalse(WingedPursuitGoal.settles(5.0, 3.0, 3), "a target on a pillar is never a ground fight");
        assertFalse(WingedPursuitGoal.settles(0.0, 15.0, 1), "still far off");
    }
}
