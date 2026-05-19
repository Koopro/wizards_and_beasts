package at.koopro.wizardsandbeasts.skill;

/**
 * Herbology tree skill definitions.
 */
final class HerbologySkills {

    private HerbologySkills() {}

    static final Skill GREEN_THUMB = SkillTrees.register(Skill.builder("green_thumb", "Green Thumb")
            .tree(SkillTreeId.HERBOLOGY).cost(1)
            .description("Begin studying magical plants.")
            .effect(new SkillEffect.UnlockAbility("green_thumb"))
            .position(0, 1)
            .build());

    static final Skill POTION_POTENCY = SkillTrees.register(Skill.builder("potion_potency", "Potion Potency")
            .tree(SkillTreeId.HERBOLOGY).maxLevel(3).cost(2)
            .description("+10% potion duration per level.")
            .prerequisite("green_thumb")
            .effect(new SkillEffect.UnlockAbility("potion_potency"))
            .position(1, 0)
            .build());

    static final Skill HARVEST_BOUNTY = SkillTrees.register(Skill.builder("harvest_bounty", "Harvest Bounty")
            .tree(SkillTreeId.HERBOLOGY).maxLevel(2).cost(2)
            .description("Chance for bonus crop drops per level.")
            .prerequisite("green_thumb")
            .effect(new SkillEffect.UnlockAbility("harvest_bounty"))
            .position(1, 2)
            .build());

    static final Skill NATURAL_REMEDY = SkillTrees.register(Skill.builder("natural_remedy", "Natural Remedy")
            .tree(SkillTreeId.HERBOLOGY).cost(3)
            .description("Gain natural regeneration from your herbal knowledge.")
            .prerequisite("potion_potency")
            .prerequisite("harvest_bounty")
            .effect(new SkillEffect.UnlockAbility("natural_remedy"))
            .position(2, 1)
            .build());
}
