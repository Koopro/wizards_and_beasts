package at.koopro.wizardsandbeasts.brew;

import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.server.level.ServerPlayer;

/**
 * Wires the {@code potion_potency} herbology skill into the brewing pillar.
 *
 * <p>Each level of {@code potion_potency} adds {@value #BONUS_PER_LEVEL} to
 * the duration multiplier ({@code 1.0 + 0.10 * level}); the skill maxes at
 * level {@value #MAX_LEVEL_BONUS} so the effective ceiling is +30% duration.
 *
 * <p>Numerical wiring lives in this class (not in
 * {@link at.koopro.wizardsandbeasts.item.wizarding.BrewItem}) so the same multiplier can
 * be reused by future flask/throwable-potion code without duplicating the
 * skill-level lookup.
 */
public final class BrewPotency {

    /** Skill id used by {@link at.koopro.wizardsandbeasts.skill.HerbologySkills#POTION_POTENCY}. */
    public static final String SKILL_ID = "potion_potency";

    /** Per-level bonus added to the duration multiplier. */
    public static final float BONUS_PER_LEVEL = 0.10f;

    /** Defensive cap so a misconfigured datapack with a high {@code maxLevel} can't trivialize the game. */
    public static final int MAX_LEVEL_BONUS = 3;

    private BrewPotency() {}

    /**
     * Returns the duration multiplier for {@code player} based on how many
     * levels of {@link #SKILL_ID} they own (capped by {@link #MAX_LEVEL_BONUS}).
     * A player with no skill levels gets {@code 1.0f}.
     */
    public static float multiplierFor(ServerPlayer player) {
        return multiplierForLevel(SkillSystemAPI.getSkillData(player).getSkillLevel(SKILL_ID));
    }

    /**
     * Pure level → multiplier function. Negative inputs are clamped to 0
     * (defensive: a corrupted skill data file shouldn't yield {@code <1.0}
     * multipliers and accidentally shorten potions). Inputs above
     * {@link #MAX_LEVEL_BONUS} are clamped to the cap.
     */
    public static float multiplierForLevel(int level) {
        int effectiveLevel = Math.min(Math.max(level, 0), MAX_LEVEL_BONUS);
        return 1.0f + BONUS_PER_LEVEL * effectiveLevel;
    }
}
