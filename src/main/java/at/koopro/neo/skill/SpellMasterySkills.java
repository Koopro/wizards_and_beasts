package at.koopro.neo.skill;

import at.koopro.neo.spell.SpellCategory;

/**
 * Spell Mastery tree skill definitions. Registered via {@link SkillTrees#register(Skill)}.
 */
final class SpellMasterySkills {

    private SpellMasterySkills() {}

    static final Skill BASIC_CASTING = SkillTrees.register(Skill.builder("basic_casting", "Basic Casting")
            .tree(SkillTreeId.SPELL_MASTERY).cost(1)
            .description("Begin your journey as a spellcaster.")
            .effect(new SkillEffect.UnlockAbility("basic_casting"))
            .position(0, 1)
            .build());

    static final Skill STUPEFY_UNLOCK = SkillTrees.register(Skill.builder("stupefy_unlock", "Learn Stupefy")
            .tree(SkillTreeId.SPELL_MASTERY).cost(2)
            .description("Learn the Stupefy stunning spell.")
            .prerequisite("basic_casting")
            .effect(new SkillEffect.LearnSpell("stupefy"))
            .position(1, 0)
            .build());

    static final Skill LUMOS_UNLOCK = SkillTrees.register(Skill.builder("lumos_unlock", "Learn Lumos")
            .tree(SkillTreeId.SPELL_MASTERY).cost(1)
            .description("Learn the Lumos light spell.")
            .prerequisite("basic_casting")
            .effect(new SkillEffect.LearnSpell("lumos"))
            .position(1, 1)
            .build());

    static final Skill PROTEGO_UNLOCK = SkillTrees.register(Skill.builder("protego_unlock", "Learn Protego")
            .tree(SkillTreeId.SPELL_MASTERY).cost(2)
            .description("Learn the Protego shield charm.")
            .prerequisite("basic_casting")
            .effect(new SkillEffect.LearnSpell("protego"))
            .position(1, 2)
            .build());

    static final Skill STUPEFY_POWER = SkillTrees.register(Skill.builder("stupefy_power", "Stupefy Power")
            .tree(SkillTreeId.SPELL_MASTERY).maxLevel(3).cost(1)
            .description("+10% Stupefy damage per level.")
            .prerequisite("stupefy_unlock")
            .effect(new SkillEffect.SpellDamageBonus("stupefy", 0.10f))
            .position(2, 0)
            .build());

    static final Skill EXPELLIARMUS_UNLOCK = SkillTrees.register(Skill.builder("expelliarmus_unlock", "Learn Expelliarmus")
            .tree(SkillTreeId.SPELL_MASTERY).cost(2)
            .description("Learn the disarming charm.")
            .prerequisite("stupefy_unlock")
            .effect(new SkillEffect.LearnSpell("expelliarmus"))
            .position(2, 1)
            .build());

    static final Skill ACCIO_UNLOCK = SkillTrees.register(Skill.builder("accio_unlock", "Learn Accio")
            .tree(SkillTreeId.SPELL_MASTERY).cost(2)
            .description("Learn the summoning charm.")
            .prerequisite("lumos_unlock")
            .effect(new SkillEffect.LearnSpell("accio"))
            .position(2, 2)
            .build());

    static final Skill NOX_UNLOCK = SkillTrees.register(Skill.builder("nox_unlock", "Learn Nox")
            .tree(SkillTreeId.SPELL_MASTERY).cost(1)
            .description("Learn the counter-charm to Lumos. Snuffs out the magical light.")
            .prerequisite("lumos_unlock")
            .effect(new SkillEffect.LearnSpell("nox"))
            .position(2, 3)
            .build());

    static final Skill INCENDIO_UNLOCK = SkillTrees.register(Skill.builder("incendio_unlock", "Learn Incendio")
            .tree(SkillTreeId.SPELL_MASTERY).cost(3)
            .description("Learn the fire-making spell.")
            .prerequisite("stupefy_power")
            .effect(new SkillEffect.LearnSpell("incendio"))
            .position(3, 0)
            .build());

    static final Skill FLIPENDO_UNLOCK = SkillTrees.register(Skill.builder("flipendo_unlock", "Learn Flipendo")
            .tree(SkillTreeId.SPELL_MASTERY).cost(2)
            .description("Learn the knockback jinx.")
            .prerequisite("expelliarmus_unlock")
            .effect(new SkillEffect.LearnSpell("flipendo"))
            .position(3, 1)
            .build());

    static final Skill REPARO_UNLOCK = SkillTrees.register(Skill.builder("reparo_unlock", "Learn Reparo")
            .tree(SkillTreeId.SPELL_MASTERY).cost(2)
            .description("Learn the mending charm.")
            .prerequisite("accio_unlock")
            .effect(new SkillEffect.LearnSpell("reparo"))
            .position(3, 2)
            .build());

    static final Skill COMBAT_FOCUS = SkillTrees.register(Skill.builder("combat_focus", "Combat Focus")
            .tree(SkillTreeId.SPELL_MASTERY).maxLevel(3).cost(2)
            .description("+5% damage to all combat spells per level.")
            .prerequisite("incendio_unlock")
            .effect(new SkillEffect.CategoryDamageBonus(SpellCategory.COMBAT, 0.05f))
            .position(4, 0)
            .build());

    static final Skill UTILITY_MASTERY = SkillTrees.register(Skill.builder("utility_mastery", "Utility Mastery")
            .tree(SkillTreeId.SPELL_MASTERY).maxLevel(3).cost(2)
            .description("-10% cooldown on all utility spells per level.")
            .prerequisite("reparo_unlock")
            .effect(new SkillEffect.CategoryCooldownReduction(SpellCategory.UTILITY, 0.10f))
            .position(4, 2)
            .build());

    static final Skill WINGARDIUM_UNLOCK = SkillTrees.register(Skill.builder("wingardium_unlock", "Learn Wingardium Leviosa")
            .tree(SkillTreeId.SPELL_MASTERY).cost(2)
            .description("Learn the levitation charm.")
            .prerequisite("flipendo_unlock")
            .effect(new SkillEffect.LearnSpell("wingardium_leviosa"))
            .position(4, 1)
            .build());

    static final Skill BOMBARDA_UNLOCK = SkillTrees.register(Skill.builder("bombarda_unlock", "Learn Bombarda")
            .tree(SkillTreeId.SPELL_MASTERY).cost(3)
            .description("Learn the explosive charm.")
            .prerequisite("combat_focus")
            .effect(new SkillEffect.LearnSpell("bombarda"))
            .position(5, 0)
            .build());

    static final Skill ALOHOMORA_UNLOCK = SkillTrees.register(Skill.builder("alohomora_unlock", "Learn Alohomora")
            .tree(SkillTreeId.SPELL_MASTERY).cost(2)
            .description("Learn the unlocking charm.")
            .prerequisite("wingardium_unlock")
            .effect(new SkillEffect.LearnSpell("alohomora"))
            .position(5, 1)
            .build());

    static final Skill EXPECTO_PATRONUM_UNLOCK = SkillTrees.register(Skill.builder("expecto_patronum_unlock", "Learn Expecto Patronum")
            .tree(SkillTreeId.SPELL_MASTERY).cost(4)
            .description("Learn the Patronus charm.")
            .prerequisite("protego_unlock")
            .effect(new SkillEffect.LearnSpell("expecto_patronum"))
            .position(5, 2)
            .build());
}
