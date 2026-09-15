package at.koopro.wizardsandbeasts.spell.clash;

import at.koopro.wizardsandbeasts.spell.core.SpellIds;

/**
 * The rules of a spell clash — when two bolts lock instead of passing through each other, which way
 * the lock slides, and who wins it.
 *
 * <p>Deliberately free of Minecraft types and of {@code Config}: this is the part worth testing, and
 * a class that reads config cannot be unit-tested in this repo (touching {@code Config} builds the
 * whole spec and kills the test JVM).
 */
public final class SpellClashRules {

    /** How close two bolts must come, in blocks, before they lock. */
    public static final double CLASH_RADIUS = 0.4;

    /**
     * How long each caster has to pick the lock up. A bolt fires when the button comes <em>up</em>, so
     * both casters have let go by the time their bolts meet; this is the time to press and hold again.
     */
    public static final int HOLD_GRACE_TICKS = 30;   // 1.5s

    /** How close the joint may be pushed to a caster's wand, in blocks, before that caster is hit. */
    public static final double WAND_REACH = 0.75;

    /** Blocks per tick the joint moves at a total mismatch in power, while both casters hold. */
    public static final double DRIFT_PER_TICK = 0.08;

    /** How a held lock stands after a tick. */
    public enum Outcome {
        /** Both still in it. */
        HOLD,
        /** A wins: B gave up, or the joint reached B's wand. A's spell lands on B. */
        A_WINS,
        /** B wins: A gave up, or the joint reached A's wand. B's spell lands on A. */
        B_WINS,
        /** Both gave up. Nobody is hit. */
        BREAK
    }

    private SpellClashRules() {}

    /**
     * Whether these two spells lock rather than flying past one another.
     *
     * <p>Everything meets everything, with one canon exception: nothing stops the Killing Curse but
     * Expelliarmus — which is the pairing that causes the wand-lock in the first place. Two Killing
     * Curses do lock: that is Priori Incantatem itself.
     */
    public static boolean canClash(String spellA, String spellB) {
        if (spellA == null || spellB == null) {
            return false;
        }
        boolean killingA = SpellIds.matches(spellA, "avada_kedavra");
        boolean killingB = SpellIds.matches(spellB, "avada_kedavra");
        if (killingA && killingB) {
            return true;
        }
        if (killingA || killingB) {
            return SpellIds.matches(killingA ? spellB : spellA, "expelliarmus");
        }
        return true;
    }

    /**
     * Whether two bolts are flying at each other rather than the same way.
     *
     * <p>A clash is a head-on meeting. Two allies firing at one enemy send bolts that converge on the same
     * point, and those must land on the enemy, not lock with each other — so bolts whose directions are
     * more than a right angle apart qualify, and nothing else does.
     *
     * @param dot the dot product of the two bolts' velocities
     */
    public static boolean headOn(double dot) {
        return dot < 0.0;
    }

    /**
     * The moment within one tick at which two moving bolts are closest, as a fraction of the tick.
     *
     * <p>Bolts move a whole step per tick with no substeps, and two of them flying at each other close
     * at twice their speed — about three blocks a tick. Checking only where they ended up would let
     * almost every pair pass straight through each other between two samples, so the clash is decided
     * on the whole of both paths instead.
     *
     * @param rx where A starts relative to B ({@code a0 - b0}), and likewise {@code ry}, {@code rz}
     * @param vx how that offset changes over the tick ({@code (a1 - a0) - (b1 - b0)}), and likewise
     *           {@code vy}, {@code vz}
     * @return a value in {@code [0, 1]}; {@code 0} when the two are not moving relative to each other
     */
    public static double closestApproachTime(double rx, double ry, double rz,
                                             double vx, double vy, double vz) {
        double closingSqr = vx * vx + vy * vy + vz * vz;
        if (closingSqr < 1.0e-12) {
            return 0.0;
        }
        double t = -(rx * vx + ry * vy + rz * vz) / closingSqr;
        return Math.max(0.0, Math.min(1.0, t));
    }

    /**
     * Which way the lock slides: {@code -1} means all the way onto A, {@code +1} all the way onto B.
     * The stronger cast drives the joint towards the weaker caster, so one bolt can eventually
     * overpower the other.
     */
    public static float bias(float powerA, float powerB) {
        float a = Math.max(0.01f, powerA);
        float b = Math.max(0.01f, powerB);
        float bias = (a - b) / (a + b);
        return Math.max(-1.0f, Math.min(1.0f, bias));
    }

    /**
     * Judges one tick of a held lock.
     *
     * <p>A side <em>gives up</em> when it held and let go, or never held at all once
     * {@link #HOLD_GRACE_TICKS} are over. Giving up loses to a side still in it; both giving up breaks
     * the lock. While both hold, the joint reaching one caster's wand loses that caster the lock.
     *
     * @param holdingA   A is holding this tick
     * @param everHeldA  A has held at some point during this lock
     * @param ticksAlive ticks since the lock began
     * @param distToA    blocks from the joint to A's wand
     */
    public static Outcome judge(boolean holdingA, boolean everHeldA, boolean holdingB, boolean everHeldB,
                                int ticksAlive, double distToA, double distToB) {
        boolean graceOver = ticksAlive >= HOLD_GRACE_TICKS;
        boolean aGaveUp = !holdingA && (everHeldA || graceOver);
        boolean bGaveUp = !holdingB && (everHeldB || graceOver);
        if (aGaveUp && bGaveUp) {
            return Outcome.BREAK;
        }
        if (aGaveUp) {
            return Outcome.B_WINS;
        }
        if (bGaveUp) {
            return Outcome.A_WINS;
        }
        if (holdingA && holdingB) {
            if (distToA <= WAND_REACH) {
                return Outcome.B_WINS;
            }
            if (distToB <= WAND_REACH) {
                return Outcome.A_WINS;
            }
        }
        return Outcome.HOLD;
    }

    /**
     * How far the joint moves this tick, in blocks along the axis from A to B. Positive moves it towards
     * B. Applied only while both casters hold: a push needs someone pushing.
     */
    public static double jointStep(float powerA, float powerB) {
        return bias(powerA, powerB) * DRIFT_PER_TICK;
    }
}
