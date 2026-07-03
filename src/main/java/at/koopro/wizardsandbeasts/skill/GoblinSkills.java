package at.koopro.wizardsandbeasts.skill;

/**
 * Goblin "Guild Ledger" tree — Gringotts guild craft.
 *
 * <p>Starter set. {@link SkillEffect.PassiveAttribute} nodes apply immediately via the shared bonus
 * pipeline; {@link SkillEffect.UnlockAbility} nodes only set a queryable flag — their gameplay
 * consumers live in event.skill.GoblinAbilityHandler.
 */
final class GoblinSkills {

    private GoblinSkills() {}

    static final Skill APPRAISAL = SkillTrees.register(Skill.builder("goblin_appraisal", "Appraisal")
            .tree(SkillTreeId.GOBLIN_CRAFT).cost(1)
            .description("Read the true worth of coin and ore at a glance.")
            .effect(new SkillEffect.UnlockAbility("goblin_appraisal"))
            .position(0, 1)
            .build());

    static final Skill FORGE_SENSE = SkillTrees.register(Skill.builder("goblin_forge_sense", "Forge-Hardened")
            .tree(SkillTreeId.GOBLIN_CRAFT).maxLevel(2).cost(2)
            .description("Goblin metalcraft toughens your hide. +1 armor per level.")
            .prerequisite("goblin_appraisal")
            .effect(new SkillEffect.PassiveAttribute("armor", 1.0))
            .position(1, 0)
            .build());

    static final Skill VAULT_STEP = SkillTrees.register(Skill.builder("goblin_vault_step", "Vault-Step")
            .tree(SkillTreeId.GOBLIN_CRAFT).maxLevel(2).cost(2)
            .description("Sure-footed in the deep tunnels. +movement speed per level.")
            .prerequisite("goblin_appraisal")
            .effect(new SkillEffect.PassiveAttribute("movement_speed", 0.01))
            .position(1, 2)
            .build());

    static final Skill LEDGER = SkillTrees.register(Skill.builder("goblin_ledger", "Guild Ledger")
            .tree(SkillTreeId.GOBLIN_CRAFT).cost(3)
            .description("Bind your dealings in goblin contract-law.")
            .prerequisite("goblin_forge_sense")
            .effect(new SkillEffect.UnlockAbility("goblin_ledger"))
            .position(2, 1)
            .build());

    static final Skill STEELHEART = SkillTrees.register(Skill.builder("goblin_steelheart", "Steelheart")
            .tree(SkillTreeId.GOBLIN_CRAFT).cost(4)
            .description("The resilience of the old guildmasters. +1 heart and goblin-steel resolve.")
            .prerequisite("goblin_ledger")
            .prerequisite("goblin_vault_step")
            .effect(new SkillEffect.PassiveAttribute("max_health", 2.0))
            .effect(new SkillEffect.UnlockAbility("goblin_steelheart"))
            .position(3, 1)
            .build());
}
