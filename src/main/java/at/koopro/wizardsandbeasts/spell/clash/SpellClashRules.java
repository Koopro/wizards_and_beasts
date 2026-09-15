package at.koopro.wizardsandbeasts.spell.clash;

import at.koopro.wizardsandbeasts.spell.core.SpellIds;

/**
 * The rules of a spell clash — when two bolts lock instead of passing through each other, which way
 * the lock slides, and how long it holds.
 *
 * <p>Deliberately free of Minecraft types and of {@code Config}: this is the part worth testing, and
 * a class that reads config cannot be unit-tested in this repo (touching {@code Config} builds the
 * whole spec and kills the test JVM).
 */
public final class SpellClashRules {

    /** How close two bolts must come, in blocks, before they lock. */
    public static final double CLASH_RADIUS = 0.4;

    /** An even lock holds this long; a lopsided one breaks nearer {@link #MIN_LIFETIME_TICKS}. */
    public static final int MIN_LIFETIME_TICKS = 30;   // 1.5s
    public static final int MAX_LIFETIME_TICKS = 50;   // 2.5s

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
     * How long the lock holds. Evenly matched wizards hold the longest; a mismatch collapses sooner,
     * because the point of the drift is that somebody eventually loses it.
     */
    public static int lifetimeTicks(float powerA, float powerB) {
        float evenness = 1.0f - Math.abs(bias(powerA, powerB));
        return MIN_LIFETIME_TICKS + Math.round(evenness * (MAX_LIFETIME_TICKS - MIN_LIFETIME_TICKS));
    }
}
