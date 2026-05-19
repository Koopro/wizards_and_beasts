package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.spell.core.SpellCategory;

/**
 * Dark Arts tree skill definitions.
 */
final class DarkArtsSkills {

    private DarkArtsSkills() {}

    static final Skill DARK_KNOWLEDGE = SkillTrees.register(Skill.builder("dark_knowledge", "Dark Knowledge")
            .tree(SkillTreeId.DARK_ARTS).cost(2)
            .description("Delve into the forbidden arts.")
            .effect(new SkillEffect.UnlockAbility("dark_knowledge"))
            .effect(new SkillEffect.CategoryDamageBonus(SpellCategory.DARK_ARTS, 0.04f))
            .position(0, 1)
            .build());

    static final Skill CRUCIO_UNLOCK = SkillTrees.register(Skill.builder("crucio_unlock", "Crucio Discipline")
            .tree(SkillTreeId.DARK_ARTS).cost(4)
            .description("Study the discipline and control needed for Cruciatus casting.")
            .prerequisite("dark_knowledge")
            .effect(new SkillEffect.SpellDamageBonus("crucio", 0.06f))
            .position(1, 0)
            .build());

    static final Skill DARK_DAMAGE = SkillTrees.register(Skill.builder("dark_damage", "Dark Power")
            .tree(SkillTreeId.DARK_ARTS).maxLevel(3).cost(2)
            .description("+8% damage to all Dark Arts spells per level.")
            .prerequisite("dark_knowledge")
            .effect(new SkillEffect.CategoryDamageBonus(SpellCategory.DARK_ARTS, 0.08f))
            .position(1, 2)
            .build());

    static final Skill IMPERIO_UNLOCK = SkillTrees.register(Skill.builder("imperio_unlock", "Imperio Discipline")
            .tree(SkillTreeId.DARK_ARTS).cost(4)
            .description("Practice coercive-control casting patterns and mental focus.")
            .prerequisite("crucio_unlock")
            .effect(new SkillEffect.SpellCooldownReduction("imperio", 0.10f))
            .position(2, 0)
            .build());

    static final Skill DARK_RESILIENCE = SkillTrees.register(Skill.builder("dark_resilience", "Dark Resilience")
            .tree(SkillTreeId.DARK_ARTS).maxLevel(2).cost(2)
            .description("+2 max health per level from dark magic.")
            .prerequisite("dark_damage")
            .effect(new SkillEffect.PassiveAttribute("max_health", 2.0))
            .position(2, 2)
            .build());

    static final Skill AVADA_KEDAVRA_UNLOCK = SkillTrees.register(Skill.builder("avada_kedavra_unlock", "Killing Curse Mastery")
            .tree(SkillTreeId.DARK_ARTS).cost(6)
            .description("Pursue forbidden mastery of lethal dark-arts channeling.")
            .prerequisite("imperio_unlock")
            .prerequisite("dark_resilience")
            .effect(new SkillEffect.SpellCooldownReduction("avada_kedavra", 0.08f))
            .position(3, 1)
            .build());
}
