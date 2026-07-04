package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.spell.core.SpellCategory;

/**
 * Wandlore tree skill definitions.
 */
final class WandloreSkills {

    private WandloreSkills() {}

    static final Skill WAND_STUDY = SkillTrees.register(Skill.builder("wand_study", "Wand Study")
            .tree(SkillTreeId.WANDLORE).cost(1)
            .description("Begin studying the secrets of wands.")
            .effect(new SkillEffect.UnlockAbility("wand_study"))
            .position(0, 1)
            .build());

    static final Skill QUICK_CAST = SkillTrees.register(Skill.builder("quick_cast", "Quick Cast")
            .tree(SkillTreeId.WANDLORE).maxLevel(3).cost(2)
            .description("-5% cooldown on all spells per level.")
            .prerequisite("wand_study")
            .effect(new SkillEffect.CategoryCooldownReduction(SpellCategory.COMBAT, 0.05f))
            .effect(new SkillEffect.CategoryCooldownReduction(SpellCategory.UTILITY, 0.05f))
            .effect(new SkillEffect.CategoryCooldownReduction(SpellCategory.DEFENSE, 0.05f))
            .effect(new SkillEffect.CategoryCooldownReduction(SpellCategory.DARK_ARTS, 0.05f))
            .position(1, 0)
            .build());

    static final Skill WAND_PRECISION = SkillTrees.register(Skill.builder("wand_precision", "Wand Precision")
            .tree(SkillTreeId.WANDLORE).maxLevel(2).cost(2)
            .description("+5% damage with all spells per level.")
            .prerequisite("wand_study")
            .effect(new SkillEffect.CategoryDamageBonus(SpellCategory.COMBAT, 0.05f))
            .effect(new SkillEffect.CategoryDamageBonus(SpellCategory.UTILITY, 0.05f))
            .effect(new SkillEffect.CategoryDamageBonus(SpellCategory.DEFENSE, 0.05f))
            .effect(new SkillEffect.CategoryDamageBonus(SpellCategory.DARK_ARTS, 0.05f))
            .position(1, 2)
            .build());

    static final Skill SPELL_EFFICIENCY = SkillTrees.register(Skill.builder("spell_efficiency", "Spell Efficiency")
            .tree(SkillTreeId.WANDLORE).maxLevel(3).cost(2)
            .description("-8% cooldown on combat spells per level.")
            .prerequisite("quick_cast")
            .effect(new SkillEffect.CategoryCooldownReduction(SpellCategory.COMBAT, 0.08f))
            .position(2, 0)
            .build());

    static final Skill ARCANE_RESERVE = SkillTrees.register(Skill.builder("arcane_reserve", "Arcane Reserve")
            .tree(SkillTreeId.WANDLORE).maxLevel(2).cost(2)
            .description("Deeper wandwork eases utility casting. -8% utility cooldown per level.")
            .prerequisite("wand_precision")
            .effect(new SkillEffect.CategoryCooldownReduction(SpellCategory.UTILITY, 0.08f))
            .position(2, 2)
            .build());

    static final Skill WAND_MASTERY = SkillTrees.register(Skill.builder("wand_mastery", "Wand Mastery")
            .tree(SkillTreeId.WANDLORE).cost(4)
            .description("Achieve true mastery over your wand.")
            .prerequisite("spell_efficiency")
            .prerequisite("wand_precision")
            .effect(new SkillEffect.UnlockAbility("wand_mastery"))
            .effect(new SkillEffect.CategoryDamageBonus(SpellCategory.COMBAT, 0.10f))
            .effect(new SkillEffect.CategoryCooldownReduction(SpellCategory.COMBAT, 0.10f))
            .position(3, 1)
            .build());

    static final Skill APPARITION_TRAINING = SkillTrees.register(Skill.builder("apparition_training", "Apparition Training")
            .tree(SkillTreeId.WANDLORE).cost(5)
            .description("Undergo formal apparition training and gain license-level control.")
            .prerequisite("wand_mastery")
            .effect(new SkillEffect.UnlockAbility("apparition_training"))
            .effect(new SkillEffect.CategoryCooldownReduction(SpellCategory.UTILITY, 0.05f))
            .position(4, 1)
            .build());
}
