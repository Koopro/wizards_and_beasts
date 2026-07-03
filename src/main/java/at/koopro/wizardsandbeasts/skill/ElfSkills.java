package at.koopro.wizardsandbeasts.skill;

/**
 * House-elf "Binding Threads" tree — bound elf-magic.
 *
 * <p>Starter set. {@link SkillEffect.PassiveAttribute} nodes apply immediately; {@link
 * SkillEffect.UnlockAbility} nodes set a queryable flag whose gameplay consumers (ward-piercing
 * apparition, item handling, free-elf status) live in event.skill.ElfAbilityHandler
 * and ApparitionServerLogic.
 */
final class ElfSkills {

    private ElfSkills() {}

    static final Skill SILENT_STEP = SkillTrees.register(Skill.builder("elf_silent_step", "Silent Step")
            .tree(SkillTreeId.ELF_BOND).maxLevel(2).cost(1)
            .description("Move unseen and unheard. +movement speed per level.")
            .effect(new SkillEffect.PassiveAttribute("movement_speed", 0.01))
            .position(0, 1)
            .build());

    static final Skill DEVOTION = SkillTrees.register(Skill.builder("elf_devotion", "Devotion")
            .tree(SkillTreeId.ELF_BOND).maxLevel(2).cost(2)
            .description("A bound elf endures much. +1 heart per level.")
            .prerequisite("elf_silent_step")
            .effect(new SkillEffect.PassiveAttribute("max_health", 2.0))
            .position(1, 0)
            .build());

    static final Skill NIMBLE_FINGERS = SkillTrees.register(Skill.builder("elf_nimble_fingers", "Nimble Fingers")
            .tree(SkillTreeId.ELF_BOND).cost(2)
            .description("Deft hands for any household task.")
            .prerequisite("elf_silent_step")
            .effect(new SkillEffect.UnlockAbility("elf_nimble_fingers"))
            .position(1, 2)
            .build());

    static final Skill ELF_APPARITION = SkillTrees.register(Skill.builder("elf_apparition", "Elf-Apparition")
            .tree(SkillTreeId.ELF_BOND).cost(3)
            .description("Elf-magic slips past wards that bind wizards.")
            .prerequisite("elf_devotion")
            .effect(new SkillEffect.UnlockAbility("elf_apparition"))
            .position(2, 1)
            .build());

    static final Skill FREE_ELF = SkillTrees.register(Skill.builder("elf_free", "Free Elf")
            .tree(SkillTreeId.ELF_BOND).cost(4)
            .description("Unbound at last. Swift and unyielding.")
            .prerequisite("elf_apparition")
            .prerequisite("elf_nimble_fingers")
            .effect(new SkillEffect.PassiveAttribute("movement_speed", 0.01))
            .effect(new SkillEffect.UnlockAbility("free_elf"))
            .position(3, 1)
            .build());
}
