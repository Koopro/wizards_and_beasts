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
        float normalised = Math.max(0f, Math.min(1f, currentStat / 100.0f));
        float multiplier = (float) Math.pow(1.0f - normalised, 1.5);
        return rawAmount * multiplier;
    }
}
