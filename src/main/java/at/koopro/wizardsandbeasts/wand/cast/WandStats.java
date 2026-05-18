package at.koopro.wizardsandbeasts.wand.cast;

import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable bundle of wand-derived multipliers and per-{@link SpellCategory}
 * affinity bonuses. Combined with skill and proficiency multipliers in the
 * cast pipeline; see {@link at.koopro.wizardsandbeasts.spell.SpellExecutor} and
 * {@link at.koopro.wizardsandbeasts.network.SpellCastC2SPayload}.
 *
 * <p>Multiplier semantics:
 * <ul>
 *   <li>{@code damageMultiplier}, {@code cooldownMultiplier},
 *       {@code rangeMultiplier} are <em>multiplicative</em> on top of skill
 *       and proficiency math.</li>
 *   <li>{@code categoryDamageBonus} is <em>additive</em>: a value of {@code 0.15}
 *       means +15% damage when the cast spell belongs to that category.</li>
 *   <li>{@code fizzleChance} is a probability in {@code [0, 1]} that the cast
 *       fails outright; resolved per-cast in {@code SpellExecutor}.</li>
 * </ul>
 */
public record WandStats(
        float damageMultiplier,
        float cooldownMultiplier,
        float rangeMultiplier,
        float fizzleChance,
        Map<SpellCategory, Float> categoryDamageBonus) {

    /** Neutral wand: every multiplier is 1.0, no bonuses, no fizzle. Used as a fallback. */
    public static final WandStats NEUTRAL = new WandStats(
            1.0f, 1.0f, 1.0f, 0.0f, Collections.emptyMap());

    public WandStats {
        // Defensive copy + clamp.
        Map<SpellCategory, Float> copy = new EnumMap<>(SpellCategory.class);
        if (categoryDamageBonus != null) {
            copy.putAll(categoryDamageBonus);
        }
        categoryDamageBonus = Collections.unmodifiableMap(copy);

        if (fizzleChance < 0.0f) fizzleChance = 0.0f;
        if (fizzleChance > 1.0f) fizzleChance = 1.0f;
    }

    /** Final per-cast damage multiplier for this spell, including category affinity. */
    public float damageFor(Spell spell) {
        float bonus = categoryDamageBonus.getOrDefault(spell.getCategory(), 0.0f);
        return damageMultiplier * (1.0f + bonus);
    }

    /** Final per-cast cooldown multiplier for this spell. */
    public float cooldownFor(Spell spell) {
        return cooldownMultiplier;
    }

    /** Final per-cast range multiplier for this spell. */
    public float rangeFor(Spell spell) {
        return rangeMultiplier;
    }

    /**
     * Builder for constructing wand-stat contributions piece-by-piece without
     * mutating shared state. Each contributor (wood/core/length/flex) returns
     * a new builder; {@link Builder#build()} produces a {@link WandStats}.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private float damage = 1.0f;
        private float cooldown = 1.0f;
        private float range = 1.0f;
        private float fizzle = 0.0f;
        private final EnumMap<SpellCategory, Float> categoryBonus = new EnumMap<>(SpellCategory.class);

        private Builder() {}

        /** Multiply current damage multiplier by {@code mult}. */
        public Builder mulDamage(float mult) { damage *= mult; return this; }

        /** Multiply current cooldown multiplier by {@code mult}. */
        public Builder mulCooldown(float mult) { cooldown *= mult; return this; }

        /** Multiply current range multiplier by {@code mult}. */
        public Builder mulRange(float mult) { range *= mult; return this; }

        /** Add (positively or negatively) to fizzle chance. */
        public Builder addFizzle(float delta) { fizzle += delta; return this; }

        /** Add additive damage bonus for a category (stacks across contributors). */
        public Builder addCategoryDamageBonus(SpellCategory category, float bonus) {
            categoryBonus.merge(category, bonus, Float::sum);
            return this;
        }

        public WandStats build() {
            return new WandStats(damage, cooldown, range, fizzle, categoryBonus);
        }
    }
}
