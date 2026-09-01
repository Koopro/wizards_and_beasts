package at.koopro.wizardsandbeasts.brew;

import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Whether a batch comes out, and what makes it more or less likely to.
 *
 * <h2>Why the arithmetic lives here</h2>
 * <p>Failure chance is assembled from four places — the recipe's own difficulty, a missed catalyst,
 * anything wrong dropped in the pot, and the brewer's skill — and every one of them is a number that
 * wants tuning. Composed in one pure function so the balance can be read, argued with and tested
 * without a cauldron.
 *
 * <h2>Skill reduces, never eliminates</h2>
 * <p>{@code potion_potency} is the same herbology skill that makes a draught last longer: a wizard
 * who is good at potions is both more reliable and gets more out of what they make. It is capped
 * below certainty on purpose — a skill level that guaranteed success would delete the mechanic for
 * exactly the players most likely to be attempting the difficult recipes.
 */
@NullMarked
public final class BrewFailure {

    /** Skill id, shared with {@link BrewPotency}. */
    public static final String SKILL_ID = BrewPotency.SKILL_ID;

    /** Each level of {@code potion_potency} removes this much of the remaining failure chance. */
    public static final float REDUCTION_PER_LEVEL = 0.15f;

    /** Levels beyond this stop helping, so the skill cannot buy certainty. */
    public static final int MAX_LEVEL_BONUS = 3;

    /**
     * The floor a skilled brewer cannot get under on a risky recipe.
     *
     * <p>A recipe that declares any risk keeps at least this much, whatever the brewer's skill. The
     * Draught of Living Death being a coin flip for a novice and a certainty for an expert would make
     * the difficulty a tutorial rather than a property of the potion.
     */
    public static final float FLOOR_WHEN_RISKY = 0.02f;

    private BrewFailure() {}

    /**
     * The chance this brew is ruined, all contributions folded in.
     *
     * @param base        the recipe's declared {@code failureChance}
     * @param penalties   accumulated in the pot: a missed catalyst, contamination
     * @param skillLevel  the brewer's {@code potion_potency}; negatives are treated as zero
     */
    public static float chance(float base, float penalties, int skillLevel) {
        float raw = Math.max(0f, base) + Math.max(0f, penalties);
        if (raw <= 0f) {
            return 0f;
        }
        int effective = Math.min(Math.max(skillLevel, 0), MAX_LEVEL_BONUS);
        float reduced = raw * (1f - REDUCTION_PER_LEVEL * effective);
        return Math.min(1f, Math.max(FLOOR_WHEN_RISKY, reduced));
    }

    /** The same figure for a live brewer, or the unreduced chance when they are offline. */
    public static float chanceFor(@Nullable ServerPlayer brewer, float base, float penalties) {
        int level = brewer == null ? 0
                : SkillSystemAPI.getSkillData(brewer).getSkillLevel(SKILL_ID);
        return chance(base, penalties, level);
    }
}
