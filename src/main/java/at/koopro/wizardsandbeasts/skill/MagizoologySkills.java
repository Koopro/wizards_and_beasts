package at.koopro.wizardsandbeasts.skill;

/**
 * Magizoology tree skill definitions.
 */
final class MagizoologySkills {

    private MagizoologySkills() {}

    static final Skill CREATURE_KNOWLEDGE = SkillTrees.register(Skill.builder("creature_knowledge", "Creature Knowledge")
            .tree(SkillTreeId.MAGIZOOLOGY).cost(1)
            .description("Begin studying magical creatures.")
            .effect(new SkillEffect.UnlockAbility("creature_knowledge"))
            .position(0, 1)
            .build());

    static final Skill NIFFLER_FRIEND = SkillTrees.register(Skill.builder("niffler_friend", "Niffler Friend")
            .tree(SkillTreeId.MAGIZOOLOGY).cost(2)
            .description("Nifflers no longer steal from you.")
            .prerequisite("creature_knowledge")
            .effect(new SkillEffect.UnlockAbility("niffler_friend"))
            .position(1, 0)
            .build());

    static final Skill BEAST_HANDLER = SkillTrees.register(Skill.builder("beast_handler", "Beast Handler")
            .tree(SkillTreeId.MAGIZOOLOGY).maxLevel(2).cost(2)
            .description("Creatures deal less damage to you. -10% per level.")
            .prerequisite("creature_knowledge")
            .effect(new SkillEffect.GameplayBonus(GameplayStat.BEAST_DAMAGE_RESISTANCE, 0.10f))
            .position(1, 2)
            .build());

    static final Skill CREATURE_BOND = SkillTrees.register(Skill.builder("creature_bond", "Creature Bond")
            .tree(SkillTreeId.MAGIZOOLOGY).maxLevel(2).cost(3)
            .description("+2 max health per level from creature companionship.")
            .prerequisite("niffler_friend")
            .prerequisite("beast_handler")
            .effect(new SkillEffect.PassiveAttribute("max_health", 2.0))
            .position(2, 1)
            .build());

    static final Skill DRAGON_TAMER = SkillTrees.register(Skill.builder("dragon_tamer", "Dragon Tamer")
            .tree(SkillTreeId.MAGIZOOLOGY).maxLevel(2).cost(3)
            .description("Hard-won composure around the largest beasts. -10% creature damage per level.")
            .prerequisite("beast_handler")
            .effect(new SkillEffect.GameplayBonus(GameplayStat.BEAST_DAMAGE_RESISTANCE, 0.10f))
            .position(2, 2)
            .build());

    static final Skill KEEPER_VIGOR = SkillTrees.register(Skill.builder("keeper_vigor", "Keeper's Hide")
            .tree(SkillTreeId.MAGIZOOLOGY).maxLevel(2).cost(2)
            .description("A handler's toughened hide. +1 armor per level.")
            .prerequisite("creature_bond")
            .effect(new SkillEffect.PassiveAttribute("armor", 1.0))
            .position(2, 0)
            .build());

    static final Skill ANIMAGUS_STUDY = SkillTrees.register(Skill.builder("animagus_study", "Animagus Study")
            .tree(SkillTreeId.MAGIZOOLOGY).cost(3)
            .description("Learn the Animagus discipline. Complete the mandrake-leaf ritual during a thunderstorm to assume a beast form.")
            .prerequisite("creature_bond")
            .effect(new SkillEffect.UnlockAbility("animagus"))
            .position(3, 1)
            .build());
}
