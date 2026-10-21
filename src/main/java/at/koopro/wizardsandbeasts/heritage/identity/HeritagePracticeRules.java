package at.koopro.wizardsandbeasts.heritage.identity;

import org.jspecify.annotations.NullMarked;

/**
 * The arithmetic of practice, with no game attached.
 *
 * <p>Every use of a people's own art adds its {@link PracticeKind#weight} to a running progress, at most
 * {@link #DAILY_CAP} a day; every {@link #PER_POINT} of progress is one profession point. The cap is what keeps
 * practice a habit rather than a grind: a house-elf who answers every summons and cooks every meal earns a point
 * every few days, the same pace a wizard reaches by mastering spells, and no faster for snapping their fingers all
 * night.
 */
@NullMarked
public final class HeritagePracticeRules {

    /** Progress worth one profession point. */
    public static final int PER_POINT = 20;
    /** Most progress that counts in one in-game day. */
    public static final int DAILY_CAP = 8;

    private HeritagePracticeRules() {}

    /** What one use did: the new state and how many points it paid. */
    public record Outcome(HeritagePracticeData data, int points) {}

    /**
     * Records one use of {@code kind} on {@code day}.
     *
     * <p>A new day resets the daily count; progress beyond the cap is lost, not banked.
     */
    public static Outcome practise(HeritagePracticeData data, PracticeKind kind, long day) {
        int today = data.day() == day ? data.today() : 0;
        int gain = Math.max(0, Math.min(kind.weight(), DAILY_CAP - today));
        int progress = data.progress() + gain;
        int points = progress / PER_POINT;
        HeritagePracticeData next = new HeritagePracticeData(
                data.withCount(kind).counts(), progress % PER_POINT, day, today + gain, data.pointsEarned() + points);
        return new Outcome(next, points);
    }

    /** Progress still counting today. */
    public static int leftToday(HeritagePracticeData data, long day) {
        return DAILY_CAP - (data.day() == day ? data.today() : 0);
    }
}
