package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;

import java.util.*;

/**
 * Pre-computed aggregate of all skill bonuses for a player.
 * Rebuilt when skills change. Never serialized.
 */
public final class SkillEffectCache {

    private final Map<String, Float> spellDamageBonuses;
    private final Map<String, Float> spellCooldownReductions;
    private final Map<SpellCategory, Float> categoryDamageBonuses;
    private final Map<SpellCategory, Float> categoryCooldownReductions;
    private final Set<String> unlockedAbilities;
    private final Map<GameplayStat, Float> gameplayBonuses;

    private SkillEffectCache(Map<String, Float> spellDamageBonuses,
                             Map<String, Float> spellCooldownReductions,
                             Map<SpellCategory, Float> categoryDamageBonuses,
                             Map<SpellCategory, Float> categoryCooldownReductions,
                             Set<String> unlockedAbilities,
                             Map<GameplayStat, Float> gameplayBonuses) {
        this.spellDamageBonuses = spellDamageBonuses;
        this.spellCooldownReductions = spellCooldownReductions;
        this.categoryDamageBonuses = categoryDamageBonuses;
        this.categoryCooldownReductions = categoryCooldownReductions;
        this.unlockedAbilities = unlockedAbilities;
        this.gameplayBonuses = gameplayBonuses;
    }

    /**
     * Computes the cache from the player's current skill data.
     */
    public static SkillEffectCache compute(PlayerSkillData data) {
        Map<String, Float> spellDmg = new HashMap<>();
        Map<String, Float> spellCd = new HashMap<>();
        Map<SpellCategory, Float> catDmg = new EnumMap<>(SpellCategory.class);
        Map<SpellCategory, Float> catCd = new EnumMap<>(SpellCategory.class);
        Set<String> abilities = new HashSet<>();
        Map<GameplayStat, Float> gameplay = new EnumMap<>(GameplayStat.class);

        for (Map.Entry<String, Integer> entry : data.getUnlockedSkills().entrySet()) {
            Skill skill = SkillTrees.byId(entry.getKey());
            if (skill == null) continue;
            int level = entry.getValue();

            for (SkillEffect effect : skill.getEffects()) {
                switch (effect) {
                    case SkillEffect.SpellDamageBonus e ->
                            spellDmg.merge(e.spellId(), e.bonusPerLevel() * level, Float::sum);
                    case SkillEffect.SpellCooldownReduction e ->
                            spellCd.merge(e.spellId(), e.reductionPerLevel() * level, Float::sum);
                    case SkillEffect.CategoryDamageBonus e ->
                            catDmg.merge(e.category(), e.bonusPerLevel() * level, Float::sum);
                    case SkillEffect.CategoryCooldownReduction e ->
                            catCd.merge(e.category(), e.reductionPerLevel() * level, Float::sum);
                    case SkillEffect.UnlockAbility e ->
                            abilities.add(e.abilityId());
                    case SkillEffect.GameplayBonus e ->
                            gameplay.merge(e.stat(), e.perLevel() * level, Float::sum);
                    case SkillEffect.LearnSpell ignored -> {} // no-op: spell learning is handled outside skill trees
                    case SkillEffect.PassiveAttribute ignored -> {} // handled at unlock time
                    // Grant/refinement flow through the source-tracked AbilityGrants layer, not this cache.
                    case SkillEffect.GrantAbility ignored -> {}
                    case SkillEffect.AbilityRefinement ignored -> {}
                }
            }
        }

        return new SkillEffectCache(spellDmg, spellCd, catDmg, catCd, abilities, gameplay);
    }

    /**
     * Returns the total damage multiplier from skills for a specific spell.
     * Combines spell-specific bonuses + category bonuses.
     * Returns 1.0 if no bonuses apply (multiplicative identity).
     */
    public float getSpellDamageMultiplier(String spellId, SpellCategory category) {
        float bonus = spellDamageBonuses.getOrDefault(spellId, 0f)
                + categoryDamageBonuses.getOrDefault(category, 0f);
        return 1.0f + bonus;
    }

    /**
     * Returns the total cooldown multiplier from skills for a specific spell.
     * Combines spell-specific reductions + category reductions.
     * Returns 1.0 if no reductions apply. Lower = faster cooldown.
     */
    public float getSpellCooldownMultiplier(String spellId, SpellCategory category) {
        float reduction = spellCooldownReductions.getOrDefault(spellId, 0f)
                + categoryCooldownReductions.getOrDefault(category, 0f);
        return Math.max(0.1f, 1.0f - reduction); // floor at 10% cooldown
    }

    /**
     * Checks if the player has unlocked a named ability.
     */
    public boolean hasAbility(String abilityId) {
        return unlockedAbilities.contains(abilityId);
    }

    /**
     * Returns the summed value of a scalar gameplay bonus, or {@code 0} if none applies.
     */
    public float getGameplayBonus(GameplayStat stat) {
        return gameplayBonuses.getOrDefault(stat, 0f);
    }

    /**
     * Empty cache with no bonuses.
     */
    public static final SkillEffectCache EMPTY = new SkillEffectCache(
            Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptySet(), Collections.emptyMap());
}
