package at.koopro.wizardsandbeasts.spell.proficiency;

import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import org.jspecify.annotations.NullMarked;

/**
 * What practising a spell does, as pure rules: how much practice a day can hold, and what a spell's practice is worth
 * on the power curve.
 *
 * <h2>Spread practice, not spam</h2>
 * A spell learns from its own use — a landed hit, a spell that did its work — and the count of those decides its tier
 * ({@link Proficiency}: Proficient at 50, Mastered at 200). Before this rule, a self-cast spell with a one-second
 * cooldown could be clicked to Mastered in under four minutes, and each tier paid skill points. Now a spell takes at
 * most {@link #DAILY_PRACTICE} practices per in-game day. Past that the spell still works, the wand still learns its
 * owner, but the practice has nothing left to teach until tomorrow. Mastery is weeks of use, which is what canon's
 * lessons are.
 *
 * <h2>One measure of skill</h2>
 * The tier and the power curve ({@link ProficiencyScaler}) used to read two different counters that drifted apart: at
 * "Mastered" the curve still stood at 0.4 and a mastered spell cast slightly <em>weaker</em> than an untrained one.
 * {@link #curve} ties them: no practice is 0, Proficient is 0.5 (baseline strength), Mastered is 1.0 (full strength).
 * KNOWLEDGE's study rate multiplies the practice a hit is worth on the curve, so a well-read wizard reaches full
 * strength before the tier says Mastered — the tier stays a record of practice, the curve reflects understanding.
 */
@NullMarked
public final class SpellPractice {

    /** Practices a single spell can take in one in-game day. */
    public static final int DAILY_PRACTICE = 40;

    /** An in-game day, in ticks. */
    public static final long DAY_TICKS = 24000L;

    private SpellPractice() {}

    /** The in-game day a game time falls on. */
    public static long day(long gameTime) {
        return Math.floorDiv(gameTime, DAY_TICKS);
    }

    /**
     * How many practices this spell has taken today, given what was recorded and on which day.
     *
     * @param recordedDay the day the stored count belongs to
     */
    public static int practicedToday(long today, long recordedDay, int recordedCount) {
        return today == recordedDay ? Math.max(0, recordedCount) : 0;
    }

    /** Whether one more practice counts today. */
    public static boolean counts(int practicedToday) {
        return practicedToday < DAILY_PRACTICE;
    }

    /**
     * A spell's standing on the power curve for this much practice: 0 untried, 0.5 at Proficient, 1.0 at Mastered,
     * linear in between.
     *
     * @param practice hits, already multiplied by any study rate
     */
    public static float curve(float practice) {
        float proficient = Proficiency.PROFICIENT.getCastsRequired();
        float mastered = Proficiency.MASTERED.getCastsRequired();
        if (practice <= 0.0f) {
            return 0.0f;
        }
        if (practice <= proficient) {
            return 0.5f * practice / proficient;
        }
        if (practice < mastered) {
            return 0.5f + 0.5f * (practice - proficient) / (mastered - proficient);
        }
        return 1.0f;
    }

    /**
     * The proficiency a spell casts at: what was stored, or what its practice alone guarantees, whichever is more. The
     * floor is taken at the plain study rate, because a reader of this (a HUD, a tooltip) may not know the caster's.
     */
    public static float effective(float stored, int hits) {
        return Math.max(stored, curve(hits));
    }

    /** The stored proficiency after one more practice: never less than before, never less than the practice earns. */
    public static float afterPractice(float stored, int hitsAfter, float studyRate) {
        return Math.min(1.0f, Math.max(stored, curve(hitsAfter * Math.max(1.0f, studyRate))));
    }
}
