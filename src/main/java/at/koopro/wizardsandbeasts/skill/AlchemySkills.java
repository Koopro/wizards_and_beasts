package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.spell.core.SpellCategory;

/**
 * Alchemy tree skill definitions. A wizard-audience tree focused on bodily fortitude
 * and the efficient brewing/utility casting of the practising alchemist.
 */
final class AlchemySkills {

    private AlchemySkills() {}

    static final Skill ALCHEMICAL_VIGOR = SkillTrees.register(Skill.builder("alchemical_vigor", "Alchemical Vigor")
            .tree(SkillTreeId.ALCHEMY).cost(1)
            .description("Begin the alchemist's path. Tempered elixirs fortify the body. +2 max health.")
            .effect(new SkillEffect.PassiveAttribute("max_health", 2.0))
            .position(0, 1)
            .build());

    static final Skill SWIFT_BREWER = SkillTrees.register(Skill.builder("swift_brewer", "Swift Brewer")
            .tree(SkillTreeId.ALCHEMY).maxLevel(3).cost(2)
            .description("Practised hands speed your craft. -8% utility cooldown per level.")
            .prerequisite("alchemical_vigor")
            .effect(new SkillEffect.CategoryCooldownReduction(SpellCategory.UTILITY, 0.08f))
            .position(1, 0)
            .build());

    static final Skill HARDENED_SKIN = SkillTrees.register(Skill.builder("hardened_skin", "Hardened Skin")
            .tree(SkillTreeId.ALCHEMY).maxLevel(2).cost(2)
            .description("Alchemical reagents toughen the flesh. +1 armor per level.")
            .prerequisite("alchemical_vigor")
            .effect(new SkillEffect.PassiveAttribute("armor", 1.0))
            .position(1, 2)
            .build());

    static final Skill TRANSMUTE_FOCUS = SkillTrees.register(Skill.builder("transmute_focus", "Transmutation Focus")
            .tree(SkillTreeId.ALCHEMY).maxLevel(2).cost(3)
            .description("Channel transmutive energy into your spellwork. +5% utility damage per level.")
            .prerequisite("swift_brewer")
            .effect(new SkillEffect.CategoryDamageBonus(SpellCategory.UTILITY, 0.05f))
            .position(2, 0)
            .build());

    static final Skill PHILOSOPHERS_STONE = SkillTrees.register(Skill.builder("philosophers_stone", "Philosopher's Stone")
            .tree(SkillTreeId.ALCHEMY).cost(5)
            .description("The alchemist's masterwork. +4 max health and -10% utility cooldown.")
            .prerequisite("transmute_focus")
            .prerequisite("hardened_skin")
            .effect(new SkillEffect.PassiveAttribute("max_health", 4.0))
            .effect(new SkillEffect.CategoryCooldownReduction(SpellCategory.UTILITY, 0.10f))
            .effect(new SkillEffect.UnlockAbility("philosophers_stone"))
            .position(3, 1)
            .build());
}
