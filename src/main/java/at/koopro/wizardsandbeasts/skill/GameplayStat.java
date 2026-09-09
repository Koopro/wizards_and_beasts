package at.koopro.wizardsandbeasts.skill;

/**
 * Scalar gameplay bonuses granted by skill nodes. Aggregated per-player in
 * {@link SkillEffectCache} (summed as {@code perLevel * level}) and read by gameplay
 * handlers via {@link SkillSystemAPI#getGameplayBonus(net.minecraft.server.level.ServerPlayer, GameplayStat)}.
 *
 * <p>Unlike {@link SkillEffect.UnlockAbility} (a boolean flag) these are continuous, level-scaling
 * numbers — the right tool whenever a skill says "{@code +X% per level}".
 */
public enum GameplayStat {
    /** Fractional reduction of incoming damage dealt by non-player creatures ({@code 0.10} = -10%). */
    BEAST_DAMAGE_RESISTANCE,
    /** Per-harvest chance to double a broken crop's drops ({@code 0.15} = 15%). */
    HARVEST_BONUS_CHANCE,
    /**
     * Absolute reduction of the cast's misfire chance ({@code 0.05} = five percentage points off).
     * Subtracted from the {@code ModifierStack} misfire total in
     * {@link SkillSystemAPI#applySkillModifiers}, which clamps the running sum to {@code [0, 1]} —
     * so it can cancel a wand's fizzle or an unbound wand's penalty, and never goes negative.
     */
    SPELL_MISFIRE_REDUCTION,
    /**
     * Added to the target's trained Occlumency level when someone casts Legilimency on them
     * ({@code 0.25} = a quarter of a full defence). Read in {@code LegilimencyServerLogic}; the sum
     * of trained level and web bonus is clamped before it scales by Willpower, so a fully studied
     * Occlumens cannot exceed the defence a trained one already had.
     */
    OCCLUMENCY_SHIELD
}
