package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.spell.core.SpellCategory;

/**
 * What unlocking a skill does. Effects are code-only — never serialized.
 * The data layer stores only skill IDs + levels; effects are derived from definitions.
 */
public sealed interface SkillEffect {

    /** Teaches the player a spell when unlocked. */
    record LearnSpell(String spellId) implements SkillEffect {}

    /** Adds a per-level damage bonus to a specific spell. */
    record SpellDamageBonus(String spellId, float bonusPerLevel) implements SkillEffect {}

    /** Reduces cooldown of a specific spell per level. */
    record SpellCooldownReduction(String spellId, float reductionPerLevel) implements SkillEffect {}

    /** Adds a per-level damage bonus to all spells in a category. */
    record CategoryDamageBonus(SpellCategory category, float bonusPerLevel) implements SkillEffect {}

    /** Reduces cooldown of all spells in a category per level. */
    record CategoryCooldownReduction(SpellCategory category, float reductionPerLevel) implements SkillEffect {}

    /** Applies an attribute modifier per level (e.g., max health, speed). */
    record PassiveAttribute(String attributeId, double amountPerLevel) implements SkillEffect {}

    /** Unlocks a named ability flag (checked via SkillEffectCache.hasAbility). */
    record UnlockAbility(String abilityId) implements SkillEffect {}

    /** Adds a per-level scalar gameplay bonus (e.g. beast resistance, harvest luck). */
    record GameplayBonus(GameplayStat stat, float perLevel) implements SkillEffect {}
}
