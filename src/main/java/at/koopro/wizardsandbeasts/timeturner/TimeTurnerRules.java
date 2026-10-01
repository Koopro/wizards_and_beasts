package at.koopro.wizardsandbeasts.timeturner;

import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;

/**
 * What a Time-Turner can do, as pure rules.
 *
 * <p><b>Canon.</b> "Each turn is an hour back" (<i>Prisoner of Azkaban</i> ch. 21). The wearer goes back; the world
 * does not. Hermione relived her own hours, and Harry saw himself across the lake. It is also the most controlled
 * object in the Ministry: time is not something to meddle with.
 *
 * <p><b>The game's reading.</b> Each turn takes the wearer back one in-game hour ({@value #TICKS_PER_TURN} ticks) to
 * where they stood then, up to {@value #MAX_TURNS} turns. The world keeps everything that happened since. Only the
 * wearer's place in it goes back. It cannot reach further back than it has been on the wearer: it remembers the
 * hours it spent with them, and a trip back spends those hours, so the next trip must wait until the wearer has
 * lived them again. There is no separate cooldown. You cannot be in the same hour twice.
 *
 * <p>This replaces a version that moved the <em>whole world's</em> clock <em>forward</em> for everyone on the server.
 * That was the opposite direction, and a shared side effect no single player should own.
 */
@NullMarked
public final class TimeTurnerRules {

    /** One turn: one in-game hour. */
    public static final long TICKS_PER_TURN = 1000L;
    /** The most turns one use can wind: three hours, as Hermione's schedule needed. */
    public static final int MAX_TURNS = 3;
    /** Ticks of winding that make one turn. */
    public static final int WIND_TICKS_PER_TURN = 20;
    /** How often the wearer's place is remembered. */
    public static final int RECORD_EVERY_TICKS = 20;
    /** How many remembered places cover the longest trip, plus a margin. */
    public static final int TRAIL_CAPACITY = (int) (MAX_TURNS * TICKS_PER_TURN / RECORD_EVERY_TICKS) + 2;

    private TimeTurnerRules() {}

    /** Where the wearer stood at a remembered moment. {@code dimension} is the level's id. */
    public record Moment(long time, String dimension, double x, double y, double z) {}

    /** Turns wound by holding the Time-Turner for {@code windTicks}. */
    public static int turns(int windTicks) {
        return Math.max(0, Math.min(MAX_TURNS, windTicks / WIND_TICKS_PER_TURN));
    }

    /**
     * The moment {@code turns} hours before {@code now}: the latest remembered place at or before it. Empty when the
     * trail does not reach that far back — the Time-Turner has not been with the wearer that long.
     *
     * @param trailOldestFirst remembered places, oldest first
     */
    public static Optional<Moment> momentFor(List<Moment> trailOldestFirst, long now, int turns) {
        if (turns < 1 || trailOldestFirst.isEmpty()) {
            return Optional.empty();
        }
        long target = now - turns * TICKS_PER_TURN;
        if (trailOldestFirst.getFirst().time() > target) {
            return Optional.empty();
        }
        Moment found = null;
        for (Moment moment : trailOldestFirst) {
            if (moment.time() > target) {
                break;
            }
            found = moment;
        }
        return Optional.ofNullable(found);
    }
}
