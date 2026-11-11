package at.koopro.wizardsandbeasts.entity.creature.ai;

import at.koopro.wizardsandbeasts.creature.ability.RangedShot;
import at.koopro.wizardsandbeasts.creature.profile.CombatProfile;
import at.koopro.wizardsandbeasts.creature.profile.CombatProfile.Style;
import at.koopro.wizardsandbeasts.entity.creature.ai.CombatRhythm.Event;
import at.koopro.wizardsandbeasts.entity.creature.ai.CombatRhythm.Input;
import at.koopro.wizardsandbeasts.entity.creature.ai.CombatRhythm.Move;
import at.koopro.wizardsandbeasts.entity.creature.ai.CombatRhythm.Phase;
import at.koopro.wizardsandbeasts.entity.creature.ai.CombatRhythm.Step;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The beat of a creature's fight without a world (BEAST_RENAISSANCE §4.7): what each style does before, during and
 * after a blow, and where a thrown thing must be aimed.
 */
class CombatRhythmTest {

    private static final Input FAR = new Input(12.0, false, false, 1.0f, false, 0);
    private static final Input ADJACENT = new Input(1.0, true, true, 1.0f, false, 0);
    /** One step back from a blow: out of ordinary reach, still inside a lunge. */
    private static final Input STEPPED_BACK = new Input(2.5, false, true, 1.0f, false, 0);
    private static final Input GONE = new Input(5.0, false, false, 1.0f, false, 0);

    private static CombatRhythm rhythm(Style style, int windup, int recovery, double range, float retreat) {
        CombatRhythm rhythm = new CombatRhythm(new CombatProfile(style, 1.2, windup, recovery, range, retreat));
        rhythm.engage();
        return rhythm;
    }

    private static Step run(CombatRhythm rhythm, Input in, int ticks) {
        Step last = null;
        for (int i = 0; i < ticks; i++) {
            last = rhythm.step(in);
        }
        return last;
    }

    // ── brute ─────────────────────────────────────────────────────────────────

    @Test
    void aBruteWalksInAndTelegraphsBeforeItHits() {
        CombatRhythm troll = rhythm(Style.BRUTE, 16, 24, 0.0, 0.0f);
        assertEquals(Move.APPROACH, troll.step(FAR).move());
        Step first = troll.step(ADJACENT);
        assertEquals(Event.TELEGRAPH, first.event());
        assertEquals(Move.STAND, first.move(), "a creature winding up is committed and stands");
        for (int i = 0; i < 15; i++) {
            assertEquals(Event.NONE, troll.step(ADJACENT).event(), "no blow before the windup ends (tick " + i + ")");
        }
        assertEquals(Event.STRIKE, troll.step(ADJACENT).event());
    }

    @Test
    void aTargetThatLeavesDuringTheWindupIsMissed() {
        CombatRhythm troll = rhythm(Style.BRUTE, 10, 20, 0.0, 0.0f);
        troll.step(ADJACENT);
        assertEquals(Event.WHIFF, run(troll, GONE, 10).event());
    }

    @Test
    void recoveryIsStoodOutAndTheNextBlowWaitsForIt() {
        CombatRhythm troll = rhythm(Style.BRUTE, 4, 24, 0.0, 0.0f);
        troll.step(ADJACENT);
        assertEquals(Event.STRIKE, run(troll, ADJACENT, 4).event());
        int ticksToNextTelegraph = 0;
        Step step;
        do {
            step = troll.step(ADJACENT);
            ticksToNextTelegraph++;
            assertNotEquals(Event.STRIKE, step.event());
        } while (step.event() != Event.TELEGRAPH && ticksToNextTelegraph < 200);
        assertEquals(CombatRhythm.BASE_COOLDOWN + 24, ticksToNextTelegraph,
                "the pause between blows is vanilla's plus the profile's recovery");
    }

    @Test
    void withoutAWindupTheBlowIsImmediate() {
        CombatRhythm plain = rhythm(Style.BRUTE, 0, 0, 0.0, 0.0f);
        assertEquals(Event.STRIKE, plain.step(ADJACENT).event());
        assertEquals(Event.NONE, plain.step(ADJACENT).event(), "and then it is on cooldown");
    }

    // ── ambusher ──────────────────────────────────────────────────────────────

    @Test
    void anAmbusherLiesStillUntilTheTargetIsClose() {
        CombatRhythm dugbog = rhythm(Style.AMBUSHER, 4, 30, 3.0, 0.0f);
        Step waiting = run(dugbog, new Input(8.0, false, false, 1.0f, false, 0), 40);
        assertEquals(Phase.WAIT, waiting.phase());
        assertEquals(Move.STAND, waiting.move());
        Step sprung = dugbog.step(new Input(2.9, false, false, 1.0f, false, 0));
        assertEquals(Move.APPROACH, sprung.move());
    }

    @Test
    void anAmbusherStruckFromAfarComesOut() {
        CombatRhythm dugbog = rhythm(Style.AMBUSHER, 4, 30, 3.0, 0.0f);
        assertEquals(Move.APPROACH, dugbog.step(new Input(10.0, false, false, 1.0f, true, 0)).move());
    }

    @Test
    void anAmbusherWithARangeLiesInWaitAndItsPatienceRunsOut() {
        CombatRhythm dugbog = rhythm(Style.AMBUSHER, 4, 30, 3.0, 0.0f);
        assertEquals(Move.STAND, run(dugbog, FAR, CombatRhythm.AMBUSH_PATIENCE - 1).move());
        assertEquals(Move.APPROACH, dugbog.step(FAR).move());
    }

    @Test
    void anAmbusherWithNoRangeOnlyCrouchesBeforeItPounces() {
        CombatRhythm spider = rhythm(Style.AMBUSHER, 10, 20, 0.0, 0.0f);
        assertEquals(Move.STAND, run(spider, FAR, CombatRhythm.CROUCH_TICKS - 1).move());
        assertEquals(Move.APPROACH, spider.step(FAR).move());
        assertTrue(CombatRhythm.CROUCH_TICKS < CombatRhythm.AMBUSH_PATIENCE);
    }

    @Test
    void aLungeStillCatchesOneStepBack() {
        CombatRhythm spider = rhythm(Style.AMBUSHER, 6, 20, 0.0, 0.0f);
        spider.step(new Input(1.0, true, true, 1.0f, true, 0));
        assertEquals(Event.STRIKE, run(spider, STEPPED_BACK, 6).event());
        assertTrue(CombatRhythm.lunge(Style.AMBUSHER) > CombatRhythm.lunge(Style.BRUTE));
    }

    // ── harrier, skirmisher, pack ─────────────────────────────────────────────

    @Test
    void aHarrierLeavesAfterItBitesAndComesBack() {
        CombatRhythm cat = rhythm(Style.HARRIER, 0, 0, 6.0, 0.0f);
        Step bite = cat.step(ADJACENT);
        assertEquals(Event.STRIKE, bite.event());
        assertEquals(Move.STATION, bite.move());
        assertEquals(6.0, bite.range());
        assertEquals(Move.STATION, run(cat, ADJACENT, CombatRhythm.MIN_BACK_OFF - 1).move());
        assertEquals(Move.APPROACH, run(cat, FAR, 2).move());
    }

    @Test
    void aSkirmisherHoldsItsRangeAndOnlyStrikesWhenCornered() {
        CombatRhythm centaur = rhythm(Style.SKIRMISHER, 0, 0, 10.0, 0.0f);
        Step far = centaur.step(FAR);
        assertEquals(Move.STATION, far.move());
        assertEquals(10.0, far.range());
        assertEquals(Move.STATION, centaur.step(new Input(4.0, false, false, 1.0f, false, 0)).move(),
                "it does not close with a target that walks at it");
        assertEquals(Event.STRIKE, centaur.step(ADJACENT).event(), "but a target on top of it is kicked");
    }

    @Test
    void aLonePackHunterIsCautiousAndAPackIsNot() {
        CombatRhythm lone = rhythm(Style.PACK_HUNTER, 0, 0, 5.0, 0.0f);
        assertEquals(Move.STATION, lone.step(ADJACENT).move(), "alone it backs off after a bite");

        Input withPack = new Input(1.0, true, true, 1.0f, false, 2);
        CombatRhythm pack = rhythm(Style.PACK_HUNTER, 0, 0, 5.0, 0.0f);
        Step bite = pack.step(withPack);
        assertEquals(Event.STRIKE, bite.event());
        assertEquals(Move.APPROACH, bite.move(), "with its pack it stays on the target");
    }

    @Test
    void aPackArrivingEndsALoneHuntersCaution() {
        CombatRhythm wolf = rhythm(Style.PACK_HUNTER, 0, 0, 5.0, 0.0f);
        wolf.step(ADJACENT);
        wolf.step(new Input(4.0, false, false, 1.0f, false, 2));
        assertEquals(Move.APPROACH, wolf.step(new Input(4.0, false, false, 1.0f, false, 2)).move());
    }

    // ── retreat ───────────────────────────────────────────────────────────────

    @Test
    void aLosingCreatureBreaksOffRunsAndGivesUp() {
        CombatRhythm spider = rhythm(Style.BRUTE, 0, 0, 0.0, 0.2f);
        Input hurt = new Input(3.0, false, false, 0.15f, true, 0);
        Step first = spider.step(hurt);
        assertEquals(Event.BREAK_OFF, first.event());
        assertEquals(Move.AWAY, first.move());
        Step last = run(spider, hurt, CombatRhythm.FLEE_TICKS);
        assertEquals(Event.DISENGAGE, last.event());
    }

    @Test
    void aCorneredCreatureFightsInsteadOfRunningAgain() {
        CombatRhythm spider = rhythm(Style.BRUTE, 0, 0, 0.0, 0.2f);
        Input hurt = new Input(1.0, true, true, 0.15f, true, 0);
        run(spider, hurt, CombatRhythm.FLEE_TICKS + 1);
        spider.engage();   // struck again, the fight restarts
        Step step = spider.step(hurt);
        assertNotEquals(Move.AWAY, step.move());
        assertEquals(Event.STRIKE, step.event());
    }

    @Test
    void oneThatHealedCanLoseItsNerveAgain() {
        CombatRhythm spider = rhythm(Style.BRUTE, 0, 0, 0.0, 0.2f);
        run(spider, new Input(3.0, false, false, 0.1f, true, 0), CombatRhythm.FLEE_TICKS + 1);
        spider.engage();
        spider.step(FAR);   // full health
        assertEquals(Event.BREAK_OFF, spider.step(new Input(3.0, false, false, 0.1f, true, 0)).event());
    }

    @Test
    void aBlowAlreadyBegunIsFinishedBeforeItRuns() {
        CombatRhythm spider = rhythm(Style.BRUTE, 8, 0, 0.0, 0.2f);
        spider.step(ADJACENT);
        Input hurtMidSwing = new Input(1.0, true, true, 0.1f, true, 0);
        assertEquals(Phase.WINDUP, spider.step(hurtMidSwing).phase());
        assertEquals(Event.STRIKE, run(spider, hurtMidSwing, 7).event());
        assertEquals(Event.BREAK_OFF, spider.step(hurtMidSwing).event());
    }

    @Test
    void noRetreatThresholdMeansItNeverRuns() {
        CombatRhythm troll = rhythm(Style.BRUTE, 0, 0, 0.0, 0.0f);
        assertNotEquals(Move.AWAY, troll.step(new Input(3.0, false, false, 0.01f, true, 0)).move());
    }

    // ── missiles ──────────────────────────────────────────────────────────────

    @Test
    void aThrownThingIsAimedAboveItsTargetAndMoreSoFurtherOut() {
        var boulder = RangedShot.Kind.BOULDER;
        double near = RangedShot.lift(6.0, boulder.speed, boulder.gravity);
        double far = RangedShot.lift(14.0, boulder.speed, boulder.gravity);
        assertTrue(near > 0.0);
        assertTrue(far > near * 4.0, "drop grows with the square of the distance");
        var arrow = RangedShot.Kind.ARROW;
        assertTrue(RangedShot.lift(14.0, arrow.speed, arrow.gravity) < far, "a faster missile drops less");
    }

    /**
     * Flies the arc the way the game does (gravity, then 1% drag, each tick) and checks the aim point really lands
     * within a body's height of the target at every distance a creature shoots from.
     */
    @Test
    void theAimLandsOnAStandingTarget() {
        for (RangedShot.Kind kind : RangedShot.Kind.values()) {
            for (double distance = 4.0; distance <= 16.0; distance += 2.0) {
                double aimY = RangedShot.lift(distance, kind.speed, kind.gravity);
                double length = Math.sqrt(distance * distance + aimY * aimY);
                double vx = distance / length * kind.speed;
                double vy = aimY / length * kind.speed;
                double x = 0.0;
                double y = 0.0;
                while (x < distance) {
                    x += vx;
                    y += vy;
                    vy -= kind.gravity;
                    vx *= 0.99;
                    vy *= 0.99;
                }
                assertTrue(Math.abs(y) < 0.9,
                        kind + " at " + distance + " blocks lands " + y + " from the target's middle");
            }
        }
    }

    @Test
    void aShotHasABand() {
        RangedShot boulder = new RangedShot(RangedShot.Kind.BOULDER, 18.0, 5.0, 8.0f, java.util.Optional.empty(),
                100, 14, false);
        assertFalse(boulder.inBand(3.0), "a giant does not throw at someone under its feet");
        assertTrue(boulder.inBand(10.0));
        assertFalse(boulder.inBand(19.0));
    }
}
