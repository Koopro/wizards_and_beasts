package at.koopro.wizardsandbeasts.stats;

/**
 * S-curve scaling for stat training increments.
 *
 * <p>As a trainable stat approaches 100 it becomes progressively harder to raise.
 * The existing {@link at.koopro.wizardsandbeasts.spell.proficiency.ProficiencyScaler}
 * uses a smoothstep/power-curve tuned for spell scaling (damage/cooldown/duration);
 * those multipliers are domain-specific and not suitable here, so this class
 * implements a separate curve rather than delegating to ProficiencyScaler.</p>
 *
 * <p>Curve: {@code multiplier = (1 - normalised)^1.5} where {@code normalised = stat / 100.0}.</p>
 * <ul>
 *   <li>stat=0  → multiplier 1.000 (full effect)</li>
 *   <li>stat=50 → multiplier ≈ 0.354</li>
 *   <li>stat=80 → multiplier ≈ 0.089</li>
 *   <li>stat=100 → multiplier 0.000 (stat cannot grow further via training)</li>
 * </ul>
 */
public final class StatTrainingScaler {

    private StatTrainingScaler() {}

    /**
     * Scale a raw training increment by the S-curve for the given current stat value.
     *
     * @param rawAmount  raw training progress to add (caller-supplied)
     * @param currentStat current stat integer value [0..100]
     * @return effective training progress after S-curve reduction
     */
    public static float scale(float rawAmount, int currentStat) {
        return rawAmount * multiplier(currentStat);
    }

    /**
     * One training event's whole effect: the stat afterwards, the accumulator afterwards, and how
     * many whole points were earned along the way.
     *
     * @param stat     stat value after the event, clamped to {@link PlayerStatsData#MAX_VALUE}
     * @param progress fraction into the *next* point, always in [0, 1)
     * @param gained   whole points earned by this one event, normally 0 or 1
     */
    public record Step(int stat, float progress, int gained) {}

    /**
     * Applies one training event.
     *
     * <p>This is the accumulator itself, lifted out of {@code PlayerStatsAPI.addTrainingProgress} so
     * the tests can drive the real arithmetic rather than a copy of it. They used to carry their own
     * transcription of the loop, which meant the pinned grind lengths could keep passing after the
     * shipped loop changed underneath them — the failure mode the reachability tests exist to catch.
     *
     * <p>The event is scaled once, against the stat as it was <em>before</em> the event. Every
     * {@link StatTraining} constant is far under 1.0, so the loop below can only ever run once in
     * play and the distinction does not arise — {@code StatTrainingAccumulatorTest} pins that
     * invariant, because it is what keeps this simple. The loop exists only so that a grant large
     * enough to cross several points at once lands them all and reports them all, rather than
     * silently discarding the surplus.
     *
     * @param currentStat stat value before the event
     * @param progress    accumulator before the event
     * @param rawAmount   raw, pre-curve training amount
     */
    public static Step apply(int currentStat, float progress, float rawAmount) {
        int stat = Math.max(PlayerStatsData.MIN_VALUE, Math.min(PlayerStatsData.MAX_VALUE, currentStat));
        float accumulated = Math.max(0f, progress);
        int gained = 0;

        // A stat already at the ceiling has nowhere to put the progress, and the curve pays zero
        // there anyway — bail before adding, so the accumulator does not creep towards a point that
        // can never be spent.
        if (stat >= PlayerStatsData.MAX_VALUE) {
            return new Step(stat, 0f, 0);
        }

        accumulated += Math.max(0f, scale(rawAmount, stat));
        while (accumulated >= 1.0f && stat < PlayerStatsData.MAX_VALUE) {
            accumulated -= 1.0f;
            stat++;
            gained++;
        }

        // At the ceiling the leftover fraction is dropped rather than banked: it can never be spent,
        // and a bar frozen at 87% would read as training that stopped working.
        if (stat >= PlayerStatsData.MAX_VALUE) {
            accumulated = 0f;
        }
        return new Step(stat, accumulated, gained);
    }

    /** The S-curve's multiplier at a stat value, in [0, 1]. */
    private static float multiplier(int currentStat) {
        float normalised = Math.max(0f, Math.min(1f, currentStat / 100.0f));
        return (float) Math.pow(1.0f - normalised, 1.5);
    }
}
