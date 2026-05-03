package at.koopro.wizardsandbeasts.type.profession;

import at.koopro.wizardsandbeasts.type.WizType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public enum ProfessionNode {
    // Wizardkind
    WIZARD_APPRENTICE("wizard_apprentice", WizType.WIZARDKIND, "Apprentice",
            "Foundational training in practical wandcraft.", 1, Set.of(), Set.of("wandcraft")),
    WIZARD_WANDMAKER("wizard_wandmaker", WizType.WIZARDKIND, "Wandmaker",
            "Master artisan of cores, woods, and magical balance.", 2, Set.of("wizard_apprentice"), Set.of("crafting", "wand")),
    WIZARD_SEER("wizard_seer", WizType.WIZARDKIND, "Seer",
            "Reads arcane signs to guide magical outcomes.", 2, Set.of("wizard_apprentice"), Set.of("divination")),
    WIZARD_ARCHMAGE("wizard_archmage", WizType.WIZARDKIND, "Archmage",
            "Master of high wizarding disciplines.", 3, Set.of("wizard_wandmaker", "wizard_seer"), Set.of("mastery")),

    // Goblin
    GOBLIN_CLERK("goblin_clerk", WizType.GOBLIN, "Vault Clerk",
            "Keeper of ledgers, contracts, and secure accounts.", 1, Set.of(), Set.of("vault")),
    GOBLIN_METALSMITH("goblin_metalsmith", WizType.GOBLIN, "Metalsmith",
            "Refines enchanted metals and complex mechanisms.", 2, Set.of("goblin_clerk"), Set.of("crafting", "metal")),
    GOBLIN_BANKER("goblin_banker", WizType.GOBLIN, "Bank Steward",
            "Oversees major vault operations and treasury logistics.", 2, Set.of("goblin_clerk"), Set.of("economy", "vault")),
    GOBLIN_DIRECTOR("goblin_director", WizType.GOBLIN, "Gringotts Director",
            "Commands elite bank operations and high-security vault policy.", 3, Set.of("goblin_metalsmith", "goblin_banker"), Set.of("mastery")),

    // House-Elf
    ELF_STEWARD("elf_steward", WizType.HOUSE_ELF, "Steward",
            "Household operations and protective domestic magic.", 1, Set.of(), Set.of("service")),
    ELF_BINDER("elf_binder", WizType.HOUSE_ELF, "Ward Binder",
            "Strengthens magical wards tied to oaths and home.", 2, Set.of("elf_steward"), Set.of("wards")),
    ELF_LIBERATOR("elf_liberator", WizType.HOUSE_ELF, "Liberated Adept",
            "Channels independent house-elf magic traditions.", 3, Set.of("elf_binder"), Set.of("mastery")),

    // Generic chains for remaining types (full tree baseline)
    WEREWOLF_TRACKER("werewolf_tracker", WizType.WEREWOLF, "Tracker",
            "Follows scent trails and night movement patterns.", 1, Set.of(), Set.of("tracking")),
    WEREWOLF_ALPHA("werewolf_alpha", WizType.WEREWOLF, "Alpha",
            "Leads the pack through instinct and strength.", 3, Set.of("werewolf_tracker"), Set.of("mastery")),

    OBSCURIAL_CHANNELER("obscurial_channeler", WizType.OBSCURIAL, "Channeler",
            "Learns to direct volatile obscurus surges.", 1, Set.of(), Set.of("control")),
    OBSCURIAL_HARBINGER("obscurial_harbinger", WizType.OBSCURIAL, "Harbinger",
            "Harnesses unstable dark force with intent.", 3, Set.of("obscurial_channeler"), Set.of("mastery")),

    VEELA_CHARMER("veela_charmer", WizType.VEELA, "Charmer",
            "Refines allure and social influence magic.", 1, Set.of(), Set.of("allure")),
    VEELA_FLAME_DANCER("veela_flame_dancer", WizType.VEELA, "Flame Dancer",
            "Combines grace with destructive fire arts.", 3, Set.of("veela_charmer"), Set.of("mastery")),

    GIANT_BRUISER("giant_bruiser", WizType.GIANT, "Bruiser",
            "Uses raw strength to dominate close combat.", 1, Set.of(), Set.of("strength")),
    GIANT_WARLORD("giant_warlord", WizType.GIANT, "Warlord",
            "Commanding presence backed by colossal resilience.", 3, Set.of("giant_bruiser"), Set.of("mastery")),

    CENTAUR_RANGER("centaur_ranger", WizType.CENTAUR, "Ranger",
            "Master of woods, movement, and range control.", 1, Set.of(), Set.of("nature")),
    CENTAUR_ORACLE("centaur_oracle", WizType.CENTAUR, "Oracle",
            "Astrological foresight and omen interpretation.", 3, Set.of("centaur_ranger"), Set.of("mastery")),

    VAMPIRE_STALKER("vampire_stalker", WizType.VAMPIRE, "Stalker",
            "Predatory stealth and blood-fueled reflexes.", 1, Set.of(), Set.of("stealth")),
    VAMPIRE_NIGHTLORD("vampire_nightlord", WizType.VAMPIRE, "Nightlord",
            "Dominates moonlit battle with ancient instincts.", 3, Set.of("vampire_stalker"), Set.of("mastery")),

    MERPEOPLE_TIDEWARDEN("merpeople_tidewarden", WizType.MERPEOPLE, "Tidewarden",
            "Protector of currents, reefs, and submerged routes.", 1, Set.of(), Set.of("aquatic")),
    MERPEOPLE_SONGWEAVER("merpeople_songweaver", WizType.MERPEOPLE, "Songweaver",
            "Uses deep-water songcraft to influence foes.", 3, Set.of("merpeople_tidewarden"), Set.of("mastery"));

    private static final Map<String, ProfessionNode> BY_ID = new HashMap<>();

    static {
        for (ProfessionNode node : values()) {
            BY_ID.put(node.id, node);
        }
    }

    private final String id;
    private final WizType parentType;
    private final String displayName;
    private final String description;
    private final int pointCost;
    private final Set<String> prerequisites;
    private final Set<String> tags;

    ProfessionNode(String id, WizType parentType, String displayName, String description,
                   int pointCost, Set<String> prerequisites, Set<String> tags) {
        this.id = id;
        this.parentType = parentType;
        this.displayName = displayName;
        this.description = description;
        this.pointCost = pointCost;
        this.prerequisites = prerequisites;
        this.tags = tags;
    }

    public String getId() {
        return id;
    }

    public WizType getParentType() {
        return parentType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getPointCost() {
        return pointCost;
    }

    public Set<String> getPrerequisites() {
        return prerequisites;
    }

    public Set<String> getTags() {
        return tags;
    }

    public String getTranslationKey() {
        return "profession.wizards_and_beasts." + id;
    }

    public String getDescriptionTranslationKey() {
        return getTranslationKey() + ".desc";
    }

    public static List<ProfessionNode> byType(WizType type) {
        List<ProfessionNode> nodes = new ArrayList<>();
        for (ProfessionNode node : values()) {
            if (node.parentType == type) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    @Nullable
    public static ProfessionNode byId(String id) {
        return BY_ID.get(id);
    }
}
