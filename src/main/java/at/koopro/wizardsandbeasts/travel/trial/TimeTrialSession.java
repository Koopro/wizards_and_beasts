package at.koopro.wizardsandbeasts.travel.trial;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One player's flight round one course: armed at the counter, started through the first hoop, finished back through
 * it. A state machine fed one observed tick at a time; it knows nothing about players or worlds, so every rule —
 * including every way to be disqualified — is testable on plain numbers.
 *
 * <p><b>What it trusts.</b> The positions are the server's view of the rider's broom, which the rider's own client
 * reports. So the rules are about what that client cannot fake cheaply: the path is checked as a swept segment
 * (skipping a hoop between ticks does not work), a jump longer than the broom could fly in a tick disqualifies
 * (teleporting, Apparating or a doctored position packet), and leaving the broom disqualifies.
 * {@code BroomSpeedGuard} already sets down a broom flying faster than its definition allows.
 */
@NullMarked
public final class TimeTrialSession {

    /** Ticks an armed course waits for its start gate before it is withdrawn. */
    public static final int ARM_TICKS = 2_400;
    /** Most ticks between two hoops before the run is called off. */
    public static final int RING_TIMEOUT_TICKS = 1_200;

    /** What one tick of flight did. */
    public enum Event {
        NONE, STARTED, RING, FINISHED,
        EXPIRED, DQ_DISMOUNTED, DQ_TELEPORTED, DQ_TIMEOUT, DQ_LEFT_WORLD;

        public boolean ends() {
            return this == FINISHED || ordinal() >= EXPIRED.ordinal();
        }
    }

    private final String courseId;
    private final TimeTrialCourse course;
    private final long armedAt;
    private int passes;
    private long startedAt = -1L;
    private long lastRingAt = -1L;
    private @Nullable Vec3 lastPosition;

    public TimeTrialSession(String courseId, TimeTrialCourse course, long armedAt) {
        this.courseId = courseId;
        this.course = course;
        this.armedAt = armedAt;
    }

    public String courseId() {
        return courseId;
    }

    public TimeTrialCourse course() {
        return course;
    }

    public boolean running() {
        return startedAt >= 0L;
    }

    /** Hoops passed so far, start gate included. */
    public int passes() {
        return passes;
    }

    public long startedAt() {
        return startedAt;
    }

    /** Ticks from the start gate to {@code now}; 0 before the start. */
    public int elapsed(long now) {
        return running() ? (int) (now - startedAt) : 0;
    }

    /**
     * Feeds one tick: where the broom is now, whether the player is riding one, whether they are still in the world
     * the course was armed in, and the furthest this broom could legitimately move in a tick.
     */
    public Event step(Vec3 position, long now, boolean riding, boolean sameWorld, double maxStep) {
        if (!sameWorld) {
            return Event.DQ_LEFT_WORLD;
        }
        if (!running()) {
            if (now - armedAt > ARM_TICKS) {
                return Event.EXPIRED;
            }
            // Before the start nothing is policed: the rider is still walking to their broom.
            if (!riding) {
                lastPosition = null;
                return Event.NONE;
            }
        } else {
            if (!riding) {
                return Event.DQ_DISMOUNTED;
            }
            if (now - lastRingAt > RING_TIMEOUT_TICKS) {
                return Event.DQ_TIMEOUT;
            }
        }
        Vec3 from = lastPosition;
        lastPosition = position;
        if (from == null) {
            return Event.NONE;
        }
        if (from.distanceTo(position) > maxStep) {
            if (running()) {
                return Event.DQ_TELEPORTED;
            }
            return Event.NONE;
        }
        if (!TimeTrialCourse.passes(from, position, course.ringFor(passes), course.ringRadius())) {
            return Event.NONE;
        }
        passes++;
        lastRingAt = now;
        if (passes == 1) {
            startedAt = now;
            return Event.STARTED;
        }
        return passes >= course.passesToFinish() ? Event.FINISHED : Event.RING;
    }

    /**
     * The furthest a broom with this top speed may move in one tick before it reads as a teleport: twice its boosted
     * top speed (a late position packet carries two ticks of flight) plus a block of slack.
     */
    public static double maxStep(float topSpeed) {
        return topSpeed * 2.0 + 1.0;
    }
}
