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
                    // Pushed into PlayerSpellData at allocation by SkillSystemAPI.applyImmediateEffects
                    // and revoked by revokeWebTaughtSpells, because spell knowledge is stored rather
                    // than derived. Nothing for a read-side cache to aggregate.
                    case SkillEffect.LearnSpell ignored -> {}
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
     * The per-spell damage bonus alone, with no category component.
     *
     * <p>Separate from {@link #getSpellDamageMultiplier} because the two halves reach cast time by
     * different routes: category bonuses are pre-aggregated into {@code PlayerSkillBonusData} when
     * allocation changes, and only the per-spell half is missing there. A caller that wants "the
     * skill web's contribution" and already has the category half must add only this, or categories
     * count twice.
     */
    public float getSpellOnlyDamageMultiplier(String spellId) {
        return 1.0f + spellDamageBonuses.getOrDefault(spellId, 0f);
    }

    /** Per-spell cooldown reduction alone. Floored the same way as the combined form. */
    public float getSpellOnlyCooldownMultiplier(String spellId) {
        return Math.max(0.1f, 1.0f - spellCooldownReductions.getOrDefault(spellId, 0f));
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
