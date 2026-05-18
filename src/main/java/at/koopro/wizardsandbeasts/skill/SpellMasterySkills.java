package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.spell.core.SpellCategory;

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

    static final Skill STUPEFY_UNLOCK = SkillTrees.register(Skill.builder("stupefy_unlock", "Stupefy Attunement")
            .tree(SkillTreeId.SPELL_MASTERY).cost(2)
            .description("Attune your wandwork to Stupefy casting principles.")
            .prerequisite("basic_casting")
            .effect(new SkillEffect.SpellDamageBonus("stupefy", 0.08f))
            .position(1, 0)
            .build());

    static final Skill LUMOS_UNLOCK = SkillTrees.register(Skill.builder("lumos_unlock", "Lumos Attunement")
            .tree(SkillTreeId.SPELL_MASTERY).cost(1)
            .description("Refine your control over wand-light channeling.")
            .prerequisite("basic_casting")
            .effect(new SkillEffect.SpellCooldownReduction("lumos", 0.15f))
            .position(1, 1)
            .build());

    static final Skill PROTEGO_UNLOCK = SkillTrees.register(Skill.builder("protego_unlock", "Protego Discipline")
            .tree(SkillTreeId.SPELL_MASTERY).cost(2)
            .description("Study the timing and structure of defensive casting.")
            .prerequisite("basic_casting")
            .effect(new SkillEffect.SpellCooldownReduction("protego", 0.08f))
            .position(1, 2)
            .build());

    static final Skill STUPEFY_POWER = SkillTrees.register(Skill.builder("stupefy_power", "Stupefy Power")
            .tree(SkillTreeId.SPELL_MASTERY).maxLevel(3).cost(1)
            .description("+10% Stupefy damage per level.")
            .prerequisite("stupefy_unlock")
            .effect(new SkillEffect.SpellDamageBonus("stupefy", 0.10f))
            .position(2, 0)
            .build());

    static final Skill EXPELLIARMUS_UNLOCK = SkillTrees.register(Skill.builder("expelliarmus_unlock", "Expelliarmus Technique")
            .tree(SkillTreeId.SPELL_MASTERY).cost(2)
            .description("Practice precision disarm fundamentals.")
            .prerequisite("stupefy_unlock")
            .effect(new SkillEffect.SpellCooldownReduction("expelliarmus", 0.10f))
            .position(2, 1)
            .build());

    static final Skill ACCIO_UNLOCK = SkillTrees.register(Skill.builder("accio_unlock", "Accio Technique")
            .tree(SkillTreeId.SPELL_MASTERY).cost(2)
            .description("Improve summoning form and pull control.")
            .prerequisite("lumos_unlock")
            .effect(new SkillEffect.SpellCooldownReduction("accio", 0.10f))
            .position(2, 2)
            .build());

    static final Skill NOX_UNLOCK = SkillTrees.register(Skill.builder("nox_unlock", "Nox Control")
            .tree(SkillTreeId.SPELL_MASTERY).cost(1)
            .description("Train clean cancellation of wand-light channeling.")
            .prerequisite("lumos_unlock")
            .effect(new SkillEffect.SpellCooldownReduction("nox", 0.20f))
            .position(2, 3)
            .build());

    static final Skill INCENDIO_UNLOCK = SkillTrees.register(Skill.builder("incendio_unlock", "Incendio Handling")
            .tree(SkillTreeId.SPELL_MASTERY).cost(3)
            .description("Build safer ignition control and heat projection.")
            .prerequisite("stupefy_power")
            .effect(new SkillEffect.SpellDamageBonus("incendio", 0.08f))
            .position(3, 0)
            .build());

    static final Skill FLIPENDO_UNLOCK = SkillTrees.register(Skill.builder("flipendo_unlock", "Flipendo Handling")
            .tree(SkillTreeId.SPELL_MASTERY).cost(2)
            .description("Train impact timing for forceful knockback casts.")
            .prerequisite("expelliarmus_unlock")
            .effect(new SkillEffect.SpellDamageBonus("flipendo", 0.10f))
            .position(3, 1)
            .build());

    static final Skill REPARO_UNLOCK = SkillTrees.register(Skill.builder("reparo_unlock", "Reparo Craft")
            .tree(SkillTreeId.SPELL_MASTERY).cost(2)
            .description("Practice restoration structure and controlled repair flow.")
            .prerequisite("accio_unlock")
            .effect(new SkillEffect.SpellCooldownReduction("reparo", 0.15f))
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

    static final Skill WINGARDIUM_UNLOCK = SkillTrees.register(Skill.builder("wingardium_unlock", "Wingardium Technique")
            .tree(SkillTreeId.SPELL_MASTERY).cost(2)
            .description("Develop steady levitation form and lift precision.")
            .prerequisite("flipendo_unlock")
            .effect(new SkillEffect.SpellCooldownReduction("wingardium_leviosa", 0.10f))
            .position(4, 1)
            .build());

    static final Skill BOMBARDA_UNLOCK = SkillTrees.register(Skill.builder("bombarda_unlock", "Bombarda Handling")
            .tree(SkillTreeId.SPELL_MASTERY).cost(3)
            .description("Focus on blast shaping and controlled detonation timing.")
            .prerequisite("combat_focus")
            .effect(new SkillEffect.SpellDamageBonus("bombarda", 0.10f))
            .position(5, 0)
            .build());

    static final Skill ALOHOMORA_UNLOCK = SkillTrees.register(Skill.builder("alohomora_unlock", "Alohomora Technique")
            .tree(SkillTreeId.SPELL_MASTERY).cost(2)
            .description("Refine lock-manipulation intent and casting precision.")
            .prerequisite("wingardium_unlock")
            .effect(new SkillEffect.SpellCooldownReduction("alohomora", 0.20f))
            .position(5, 1)
            .build());

    static final Skill EXPECTO_PATRONUM_UNLOCK = SkillTrees.register(Skill.builder("expecto_patronum_unlock", "Patronus Focus")
            .tree(SkillTreeId.SPELL_MASTERY).cost(4)
            .description("Strengthen defensive focus and anti-dark channel stability.")
            .prerequisite("protego_unlock")
            .effect(new SkillEffect.SpellCooldownReduction("expecto_patronum", 0.10f))
            .position(5, 2)
            .build());
}
