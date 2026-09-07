package at.koopro.wizardsandbeasts.heritage.nutrition;

import org.jspecify.annotations.NullMarked;

/**
 * How a body is fed.
 *
 * <p>Nutrition used to be one rule for everyone, because vanilla only has one: a hunger bar, filled by
 * food. That is a statement about wizards which is simply false about half of this mod's heritages — a
 * vampire does not get anything out of bread, and a house-elf's magic is not a calorie count. This enum
 * is the seam where a heritage says which of those it is, so the answer is looked up once rather than
 * re-derived as {@code if (heritage == VAMPIRE)} at every site that touches food.
 *
 * <p>Resolution lives in {@link NutritionPolicyResolver}; enforcement, which is the part every policy
 * shares, lives in {@link NutritionEnforcer}. A new policy therefore costs a constant here, a branch in
 * the resolver and a branch in the enforcer — and nothing at all in the eight event handlers that only
 * ever ask {@link #usesVanillaHunger()}.
 */
@NullMarked
public enum NutritionPolicy {

    /** The vanilla contract: food fills a hunger bar, hunger drives regeneration and sprinting. */
    VANILLA,

    /**
     * Blood is the only nourishment. Food does nothing, the hunger bar is replaced by a blood meter,
     * and the vanilla food level becomes a <em>mirror</em> of the blood pool — see
     * {@link NutritionEnforcer} for why that is drawn rather than pinned.
     */
    BLOOD,

    /**
     * Nothing edible matters, and nothing replaces it. Reserved for heritages that should simply never
     * think about food; nothing ships with it yet, and it exists so the first one that wants it does not
     * have to invent the concept of a non-vanilla policy at the same time.
     */
    NONE;

    /** True when the vanilla hunger bar is this body's real resource and should be left alone. */
    public boolean usesVanillaHunger() {
        return this == VANILLA;
    }

    /** True when eating ordinary food should restore something. Only {@link #VANILLA} says yes. */
    public boolean feedsOnFood() {
        return this == VANILLA;
    }
}
