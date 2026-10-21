package at.koopro.wizardsandbeasts.travel;

import at.koopro.wizardsandbeasts.travel.trial.TimeTrialCourse;
import at.koopro.wizardsandbeasts.travel.trial.TimeTrialService;
import at.koopro.wizardsandbeasts.travel.trial.TimeTrialSession;
import at.koopro.wizardsandbeasts.travel.trial.TimeTrialSession.Event;
import io.netty.buffer.Unpooled;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Time trials as rules: the course is a deterministic loop in the sky, a hoop counts only in order and only when the
 * path passes through it, and every way to cheat ends the run.
 */
class TimeTrialTest {

    private static final TimeTrialCourse.Terrain FLAT = (x, z) -> 64;

    private static TimeTrialCourse course(long seed) {
        return TimeTrialCourse.generate(0.0, 0.0, seed, FLAT, 320);
    }

    @Test
    void aCourseIsASeededLoopOfHoopsAboveTheGround() {
        TimeTrialCourse a = course(42L);
        assertEquals(a, course(42L), "same seed, same course");
        assertFalse(a.equals(course(43L)), "different seed, different course");
        assertEquals(TimeTrialCourse.RING_COUNT, a.rings().size());
        for (Vec3 ring : a.rings()) {
            assertTrue(ring.y >= 64 + TimeTrialCourse.MIN_CLEARANCE, "hoop too low: " + ring);
            double r = Math.hypot(ring.x, ring.z);
            assertTrue(r >= TimeTrialCourse.COURSE_RADIUS - TimeTrialCourse.RADIAL_JITTER - 1e-6
                    && r <= TimeTrialCourse.COURSE_RADIUS + TimeTrialCourse.RADIAL_JITTER + 1e-6, "hoop off the circle");
        }
        TimeTrialCourse capped = TimeTrialCourse.generate(0.0, 0.0, 42L, (x, z) -> 318, 320);
        capped.rings().forEach(ring -> assertTrue(ring.y <= 316, "hoop above the build ceiling"));
        assertEquals(TimeTrialCourse.parFor(a.rings()), a.parTicks());
    }

    @Test
    void theBrokenFlightTestCatchesAHoopSkippedBetweenTicks() {
        Vec3 hoop = new Vec3(0, 80, 0);
        // A Firebolt at full boost covers ~2.4 blocks a tick: both ends of this tick are outside the hoop.
        assertTrue(TimeTrialCourse.passes(new Vec3(-5, 80, 0), new Vec3(5, 80, 0), hoop, 4.0));
        assertFalse(TimeTrialCourse.passes(new Vec3(-5, 90, 0), new Vec3(5, 90, 0), hoop, 4.0), "flew over it");
        assertFalse(TimeTrialCourse.passes(new Vec3(-10, 80, 0), new Vec3(-6, 80, 0), hoop, 4.0), "stopped short");
    }

    @Test
    void parSitsBetweenASchoolBroomAndAComet() {
        // The whole progression case in one inequality: a Cleansweep at cruise misses par, a Comet at cruise makes it.
        assertTrue(0.55 < TimeTrialCourse.PAR_SPEED && TimeTrialCourse.PAR_SPEED < 0.70);
    }

    /**
     * Flies straight through each hoop in turn. The hops between hoops are long, so the step limit is opened wide:
     * this checks ordering and the finish, and {@link #everyWayToCheatEndsTheRun} checks the limit.
     */
    private static Event flyLoop(TimeTrialSession session, TimeTrialCourse course, long startTick) {
        long tick = startTick;
        Event last = Event.NONE;
        for (int pass = 0; pass < course.passesToFinish(); pass++) {
            Vec3 hoop = course.ringFor(pass);
            for (Vec3 at : List.of(hoop.add(-0.5, 0, 0), hoop.add(0.5, 0, 0))) {
                Event event = session.step(at, tick++, true, true, 1000.0);
                if (event.ends()) {
                    return event;
                }
                last = event;
            }
        }
        return last;
    }

    @Test
    void aCleanLoopStartsAtTheGateAndFinishesBackThroughIt() {
        TimeTrialCourse course = course(7L);
        TimeTrialSession session = new TimeTrialSession("wizards_and_beasts:broom_shop", course, 0L);
        Vec3 gate = course.ringFor(0);
        session.step(gate.add(-0.5, 0, 0), 10L, true, true, 3.0);
        assertEquals(Event.STARTED, session.step(gate.add(0.5, 0, 0), 11L, true, true, 3.0));
        assertTrue(session.running());
        TimeTrialSession fresh = new TimeTrialSession("x", course, 0L);
        assertEquals(Event.FINISHED, flyLoop(fresh, course, 100L));
    }

    @Test
    void hoopsCountOnlyInOrder() {
        TimeTrialCourse course = course(9L);
        TimeTrialSession session = new TimeTrialSession("x", course, 0L);
        Vec3 third = course.ringFor(3);
        session.step(third.add(-0.5, 0, 0), 1L, true, true, 3.0);
        assertEquals(Event.NONE, session.step(third.add(0.5, 0, 0), 2L, true, true, 3.0), "hoop 3 before the gate");
        assertEquals(0, session.passes());
    }

    @Test
    void everyWayToCheatEndsTheRun() {
        TimeTrialCourse course = course(11L);
        Vec3 gate = course.ringFor(0);

        TimeTrialSession teleport = started(course, gate);
        assertEquals(Event.DQ_TELEPORTED, teleport.step(gate.add(40, 0, 0), 12L, true, true, 3.0));

        TimeTrialSession dismount = started(course, gate);
        assertEquals(Event.DQ_DISMOUNTED, dismount.step(gate, 12L, false, true, 3.0));

        TimeTrialSession world = started(course, gate);
        assertEquals(Event.DQ_LEFT_WORLD, world.step(gate, 12L, true, false, 3.0));

        TimeTrialSession slow = started(course, gate);
        assertEquals(Event.DQ_TIMEOUT, slow.step(gate.add(1, 0, 0), 11L + TimeTrialSession.RING_TIMEOUT_TICKS + 1, true,
                true, 3.0));

        TimeTrialSession idle = new TimeTrialSession("x", course, 0L);
        assertEquals(Event.EXPIRED, idle.step(gate, TimeTrialSession.ARM_TICKS + 1L, false, true, 3.0));
        assertTrue(Event.EXPIRED.ends() && Event.DQ_TELEPORTED.ends() && Event.FINISHED.ends());
        assertFalse(Event.RING.ends() || Event.STARTED.ends());
    }

    @Test
    void walkingToYourBroomBeforeTheStartIsNotPoliced() {
        TimeTrialCourse course = course(13L);
        TimeTrialSession session = new TimeTrialSession("x", course, 0L);
        assertEquals(Event.NONE, session.step(new Vec3(0, 64, 0), 1L, false, true, 3.0));
        // Mounting is not a teleport: the first ridden tick has no previous position to compare with.
        assertEquals(Event.NONE, session.step(new Vec3(30, 64, 30), 2L, true, true, 3.0));
    }

    @Test
    void theStepLimitFollowsTheBroom() {
        assertEquals(1.0, TimeTrialSession.maxStep(0.0f), 1e-9);
        assertTrue(TimeTrialSession.maxStep(2.4f) > 2.4 * 2);
    }

    @Test
    void theClockReadsMinutesSecondsAndTenths() {
        assertEquals("0:00.0", TimeTrialService.clock(0));
        assertEquals("1:05.3", TimeTrialService.clock(1306));
    }

    @Test
    void theCourseSurvivesTheWire() {
        TimeTrialCourse course = course(5L);
        var buf = Unpooled.buffer();
        TimeTrialCourse.STREAM_CODEC.encode(buf, course);
        assertEquals(course, TimeTrialCourse.STREAM_CODEC.decode(buf));
    }

    @Test
    void bestTimesKeepTheFastest() {
        TravelRecord record = TravelRecord.EMPTY.withTrialFinished("c", 900).withTrialFinished("c", 1200);
        assertEquals(900, record.bestTicks("c"));
        assertEquals(2, record.trialsFinished());
        assertEquals(-1, record.bestTicks("other"));
        assertEquals(List.of(), List.copyOf(TravelRecord.EMPTY.bestTrialTicks().keySet()));
    }

    private static TimeTrialSession started(TimeTrialCourse course, Vec3 gate) {
        TimeTrialSession session = new TimeTrialSession("x", course, 0L);
        session.step(gate.add(-0.5, 0, 0), 10L, true, true, 3.0);
        assertEquals(Event.STARTED, session.step(gate.add(0.5, 0, 0), 11L, true, true, 3.0));
        return session;
    }
}
