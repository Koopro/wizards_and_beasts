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
    HARVEST_BONUS_CHANCE
}
