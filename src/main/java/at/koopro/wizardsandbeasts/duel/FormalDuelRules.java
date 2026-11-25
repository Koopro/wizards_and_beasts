package at.koopro.wizardsandbeasts.duel;

import org.jspecify.annotations.NullMarked;

/**
 * The formal duel's rules, pure so they are unit-tested; {@link FormalDuels} keeps the duels and applies them.
 *
 * <p>Canon: the Duelling Club (CoS ch.11): the two face each other, bow, raise wands, and on Lockhart's count of
 * three cast to disarm or stun, not to kill. Sanctioned duels between wizards are no crime; a duel ends when one
 * duellist is disarmed, stunned or down, or yields.
 */
@NullMarked
public final class FormalDuelRules {

    /** A challenge stands this long (30 s) before it lapses. */
    public static final int CHALLENGE_TICKS = 600;
    /** How near the one challenged must stand. */
    public static final double CHALLENGE_RANGE = 16.0;
    /** "One, two, three": a second per count. */
    public static final int COUNTDOWN_TICKS = 60;
    /** Duellists who walk this far apart have abandoned the duel. */
    public static final double LEASH = 32.0;
    /** A duel nobody wins in five minutes is called a draw. */
    public static final int MAX_TICKS = 6000;
    /** Vocation flag: may issue a formal challenge (the duelist). */
    public static final String CHALLENGE_FLAG = "formal_duel";

    private FormalDuelRules() {}

    /** Why a duel ended. */
    public enum End { NONE, WON, YIELDED, ABANDONED, TIME, DEATH }

    /** Whether wands may be raised yet, {@code elapsed} ticks after the duel was accepted. */
    public static boolean started(long elapsed) {
        return elapsed >= COUNTDOWN_TICKS;
    }

    /** The count called at {@code elapsed}: 3, 2, 1 at whole seconds before the start, else 0. */
    public static int count(long elapsed) {
        if (elapsed < 0 || elapsed >= COUNTDOWN_TICKS || elapsed % 20 != 0) {
            return 0;
        }
        return (int) ((COUNTDOWN_TICKS - elapsed) / 20);
    }

    /**
     * Where the duel stands: a duellist down loses (the first one checked, if both), a pair too far apart or past
     * the time limit ends it.
     */
    public static End check(long elapsed, boolean bothAlive, boolean sameLevel, double distanceSquared,
                            boolean firstDown, boolean secondDown) {
        if (!bothAlive) {
            return End.DEATH;
        }
        if (!sameLevel || distanceSquared > LEASH * LEASH) {
            return End.ABANDONED;
        }
        if (started(elapsed) && (firstDown || secondDown)) {
            return End.WON;
        }
        return elapsed >= MAX_TICKS ? End.TIME : End.NONE;
    }

    /** The most damage a duellist's blow may do: never the last of the victim's health. */
    public static float cap(float amount, float health) {
        return Math.max(0.0f, Math.min(amount, health - 1.0f));
    }
}
