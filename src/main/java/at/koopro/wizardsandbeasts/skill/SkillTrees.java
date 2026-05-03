package at.koopro.wizardsandbeasts.skill;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Registry of all skills. Follows the {@link at.koopro.wizardsandbeasts.spell.Spells} pattern.
 * Call {@link #init()} during mod initialization to validate the graph.
 */
public final class SkillTrees {

    private static final Map<String, Skill> BY_ID = new LinkedHashMap<>();
    private static final Map<SkillTreeId, List<Skill>> BY_TREE = new EnumMap<>(SkillTreeId.class);

    // Spell Mastery
    public static final Skill BASIC_CASTING = SpellMasterySkills.BASIC_CASTING;
    public static final Skill STUPEFY_UNLOCK = SpellMasterySkills.STUPEFY_UNLOCK;
    public static final Skill LUMOS_UNLOCK = SpellMasterySkills.LUMOS_UNLOCK;
    public static final Skill NOX_UNLOCK = SpellMasterySkills.NOX_UNLOCK;
    public static final Skill PROTEGO_UNLOCK = SpellMasterySkills.PROTEGO_UNLOCK;
    public static final Skill STUPEFY_POWER = SpellMasterySkills.STUPEFY_POWER;
    public static final Skill EXPELLIARMUS_UNLOCK = SpellMasterySkills.EXPELLIARMUS_UNLOCK;
    public static final Skill ACCIO_UNLOCK = SpellMasterySkills.ACCIO_UNLOCK;
    public static final Skill INCENDIO_UNLOCK = SpellMasterySkills.INCENDIO_UNLOCK;
    public static final Skill FLIPENDO_UNLOCK = SpellMasterySkills.FLIPENDO_UNLOCK;
    public static final Skill REPARO_UNLOCK = SpellMasterySkills.REPARO_UNLOCK;
    public static final Skill COMBAT_FOCUS = SpellMasterySkills.COMBAT_FOCUS;
    public static final Skill UTILITY_MASTERY = SpellMasterySkills.UTILITY_MASTERY;
    public static final Skill WINGARDIUM_UNLOCK = SpellMasterySkills.WINGARDIUM_UNLOCK;
    public static final Skill BOMBARDA_UNLOCK = SpellMasterySkills.BOMBARDA_UNLOCK;
    public static final Skill ALOHOMORA_UNLOCK = SpellMasterySkills.ALOHOMORA_UNLOCK;
    public static final Skill EXPECTO_PATRONUM_UNLOCK = SpellMasterySkills.EXPECTO_PATRONUM_UNLOCK;

    // Dark Arts
    public static final Skill DARK_KNOWLEDGE = DarkArtsSkills.DARK_KNOWLEDGE;
    public static final Skill CRUCIO_UNLOCK = DarkArtsSkills.CRUCIO_UNLOCK;
    public static final Skill DARK_DAMAGE = DarkArtsSkills.DARK_DAMAGE;
    public static final Skill IMPERIO_UNLOCK = DarkArtsSkills.IMPERIO_UNLOCK;
    public static final Skill DARK_RESILIENCE = DarkArtsSkills.DARK_RESILIENCE;
    public static final Skill AVADA_KEDAVRA_UNLOCK = DarkArtsSkills.AVADA_KEDAVRA_UNLOCK;

    // Wandlore
    public static final Skill WAND_STUDY = WandloreSkills.WAND_STUDY;
    public static final Skill QUICK_CAST = WandloreSkills.QUICK_CAST;
    public static final Skill WAND_PRECISION = WandloreSkills.WAND_PRECISION;
    public static final Skill SPELL_EFFICIENCY = WandloreSkills.SPELL_EFFICIENCY;
    public static final Skill WAND_MASTERY = WandloreSkills.WAND_MASTERY;

    // Magizoology
    public static final Skill CREATURE_KNOWLEDGE = MagizoologySkills.CREATURE_KNOWLEDGE;
    public static final Skill NIFFLER_FRIEND = MagizoologySkills.NIFFLER_FRIEND;
    public static final Skill BEAST_HANDLER = MagizoologySkills.BEAST_HANDLER;
    public static final Skill CREATURE_BOND = MagizoologySkills.CREATURE_BOND;

    // Herbology
    public static final Skill GREEN_THUMB = HerbologySkills.GREEN_THUMB;
    public static final Skill POTION_POTENCY = HerbologySkills.POTION_POTENCY;
    public static final Skill HARVEST_BOUNTY = HerbologySkills.HARVEST_BOUNTY;
    public static final Skill NATURAL_REMEDY = HerbologySkills.NATURAL_REMEDY;

    private SkillTrees() {}

    static Skill register(Skill skill) {
        BY_ID.put(skill.getId(), skill);
        BY_TREE.computeIfAbsent(skill.getTree(), k -> new ArrayList<>()).add(skill);
        return skill;
    }

    /**
     * Validates the skill graph (all prerequisites exist). Called during mod init.
     */
    public static void init() {
        for (Skill skill : BY_ID.values()) {
            for (String prereqId : skill.getPrerequisites()) {
                if (!BY_ID.containsKey(prereqId)) {
                    throw new IllegalStateException(
                            "Skill '" + skill.getId() + "' has unknown prerequisite '" + prereqId + "'");
                }
            }
        }
    }

    @Nullable
    public static Skill byId(String id) {
        return BY_ID.get(id);
    }

    public static List<Skill> getTree(SkillTreeId tree) {
        return Collections.unmodifiableList(BY_TREE.getOrDefault(tree, Collections.emptyList()));
    }

    public static Collection<Skill> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    public static Collection<String> allIds() {
        return Collections.unmodifiableCollection(BY_ID.keySet());
    }

    public static int count() {
        return BY_ID.size();
    }
}
