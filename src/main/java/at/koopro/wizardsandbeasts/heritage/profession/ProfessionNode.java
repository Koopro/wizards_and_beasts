package at.koopro.wizardsandbeasts.heritage.profession;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.skill.GameplayStat;
import at.koopro.wizardsandbeasts.skill.SkillEffect.GameplayBonus;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A heritage's own trades, bought with profession points (one per mastered spell, three at the
 * ceremony).
 *
 * <p>Each node pays in the currency its description names, through a {@link GameplayStat} the skill
 * web already reads at its point of use — a Wandmaker keeps a wand through a lost duel, a Ward Binder's
 * shields stand longer, a Stalker is noticed from less far away. These used to share one formula
 * whatever they said: a combat cooldown cut plus, for Wizardkind, utility damage and <b>+max health</b>
 * ("Seer: reads omens" gave +2 hearts). That made a heritage a stat multiplier, which is what
 * documentation/PROGRESSION_MAP.md §4 #4 ruled out. No node grants an attribute or a spell multiplier
 * now; {@code ProfessionNodeTest} holds that line.
 *
 * <p>Saves need no migration: unlocked ids are unchanged, and {@code SkillAttributeApplicator.removeAll}
 * strips every old {@code skill/profession/*} modifier the next time attributes are applied (login).
 */
public enum ProfessionNode {
    // Wizardkind
    WIZARD_APPRENTICE("wizard_apprentice", Heritage.WIZARDKIND, "Apprentice",
            "Foundational training in practical wandwork, shielding, and the habits that keep a tower standing.", 1, Set.of(),
            Set.of("wand_mastery")),
    WIZARD_WANDMAKER("wizard_wandmaker", Heritage.WIZARDKIND, "Wandmaker",
            "Artisan of cores, woods, and temper — the quiet science behind every allegiance duel ever fought.", 2, Set.of("wizard_apprentice"),
            Set.of("wand_mastery", "smithing")),
    WIZARD_SEER("wizard_seer", Heritage.WIZARDKIND, "Seer",
            "Reads omens in tea leaves, cards, and the uneasy gaps between what is taught and what is true.", 2, Set.of("wizard_apprentice"),
            Set.of("divination")),
    WIZARD_ARCHMAGE("wizard_archmage", Heritage.WIZARDKIND, "Archmage",
            "Unites wand-lore and foresight into high wizarding practice few ministries dare standardise.", 3,
            Set.of("wizard_wandmaker", "wizard_seer"),
            Set.of("wand_mastery", "divination")),

    // Goblin
    GOBLIN_CLERK("goblin_clerk", Heritage.GOBLIN, "Vault Clerk",
            "Ledgers, liens, and the patient arithmetic that keeps a dragon from boredom.", 1, Set.of(),
            Set.of("vault", "economy")),
    GOBLIN_METALSMITH("goblin_metalsmith", Heritage.GOBLIN, "Metalsmith",
            "Forges and refines the enchanted alloys that laugh at ordinary rust.", 2, Set.of("goblin_clerk"),
            Set.of("smithing", "vault")),
    GOBLIN_BANKER("goblin_banker", Heritage.GOBLIN, "Bank Steward",
            "Oversees vault rotations, curse-breaker contracts, and the politics of who may withdraw what.", 2, Set.of("goblin_clerk"),
            Set.of("economy", "vault")),
    GOBLIN_DIRECTOR("goblin_director", Heritage.GOBLIN, "Gringotts Director",
            "Commands policy where metal, magic, and Ministry memoranda collide beneath London.", 3,
            Set.of("goblin_metalsmith", "goblin_banker"),
            Set.of("vault", "economy", "smithing")),

    // House-Elf
    ELF_STEWARD("elf_steward", Heritage.HOUSE_ELF, "Steward",
            "Keeps hearths whole — mending, shielding, and anticipating need before it is spoken.", 1, Set.of(),
            Set.of("bond_magic", "healing")),
    ELF_BINDER("elf_binder", Heritage.HOUSE_ELF, "Ward Binder",
            "Weaves household wards into oaths older than any single wand line.", 2, Set.of("elf_steward"),
            Set.of("bond_magic")),
    ELF_LIBERATOR("elf_liberator", Heritage.HOUSE_ELF, "Liberated Adept",
            "A freed elf who has turned binding-magic inward — mastering depths polite wandcraft rarely touches.", 3, Set.of("elf_binder"),
            Set.of("bond_magic", "dark_arts")),

    // Veela
    VEELA_CHARMER("veela_charmer", Heritage.VEELA, "Charmer",
            "Refines allure into something almost civilised — until anger strips the mask.", 1, Set.of(),
            Set.of("bond_magic")),
    VEELA_SONGBIRD("veela_songbird", Heritage.VEELA, "Songbird",
            "Voice-work that bends attention the way sirens bend tides — beautiful, cruel, precise.", 2, Set.of("veela_charmer"),
            Set.of("bond_magic", "transformation")),
    VEELA_FLAME_DANCER("veela_flame_dancer", Heritage.VEELA, "Flame Dancer",
            "Harpy fury married to ballroom grace; fire answers the dance before the wand could.", 3, Set.of("veela_songbird"),
            Set.of("transformation", "combat", "bond_magic")),

    // Giant
    GIANT_BRUISER("giant_bruiser", Heritage.GIANT, "Bruiser",
            "Raw reach and weight turned into a craft of its own — the simplest kind, and the hardest to parry.", 1, Set.of(),
            Set.of("combat")),
    GIANT_RAVAGER("giant_ravager", Heritage.GIANT, "Ravager",
            "Learns to read fear in herds and mobs alike, turning panic into advantage.", 2, Set.of("giant_bruiser"),
            Set.of("combat", "beast_mastery")),
    GIANT_WARLORD("giant_warlord", Heritage.GIANT, "Warlord",
            "Commands broken ground and broken ranks with a shout that needs no translation.", 3, Set.of("giant_ravager"),
            Set.of("combat", "beast_mastery")),

    // Centaur
    CENTAUR_RANGER("centaur_ranger", Heritage.CENTAUR, "Ranger",
            "Archery, trailcraft, and the forest's own sense of trespass.", 1, Set.of(),
            Set.of("combat", "beast_mastery")),
    CENTAUR_STAR_READER_PATH("centaur_stargazer_path", Heritage.CENTAUR, "Star Reader",
            "Turns canopy gaps into observatories — reading futures in light older than wands.", 2, Set.of("centaur_ranger"),
            Set.of("astronomy", "divination")),
    CENTAUR_ORACLE("centaur_oracle", Heritage.CENTAUR, "Oracle",
            "Speaks what the stars insist upon, whether listeners prefer prophecy or politics.", 3, Set.of("centaur_stargazer_path"),
            Set.of("divination", "astronomy", "beast_mastery")),

    // Vampire
    VAMPIRE_STALKER("vampire_stalker", Heritage.VAMPIRE, "Stalker",
            "Night-hunter's patience — stillness, scent, and the geometry of ambush.", 1, Set.of(),
            Set.of("stealth", "combat")),
    VAMPIRE_WRAITH("vampire_wraith", Heritage.VAMPIRE, "Wraith",
            "Shadow clings where they walk; daylight stories fail to describe what witnesses saw.", 2, Set.of("vampire_stalker"),
            Set.of("stealth", "dark_arts")),
    VAMPIRE_NIGHTLORD("vampire_nightlord", Heritage.VAMPIRE, "Nightlord",
            "A title earned in bloodlines and battlefields alike — night bends toward them, not away.", 3, Set.of("vampire_wraith"),
            Set.of("dark_arts", "combat", "stealth")),

    // Merpeople
    MERPEOPLE_TIDEWARDEN("merpeople_tidewarden", Heritage.MERPEOPLE, "Tidewarden",
            "Guards reefs, wrecks, and the old treaties scratched on cave stone.", 1, Set.of(),
            Set.of("aquatic", "combat")),
    MERPEOPLE_DEEPCALLER("merpeople_deepcaller", Heritage.MERPEOPLE, "Deep Caller",
            "Song that carries farther through water than any surface horn could dream.", 2, Set.of("merpeople_tidewarden"),
            Set.of("aquatic", "song_magic")),
    MERPEOPLE_SONGWEAVER("merpeople_songweaver", Heritage.MERPEOPLE, "Songweaver",
            "Weaves pressure, melody, and command until the sea itself seems to take sides.", 3, Set.of("merpeople_deepcaller"),
            Set.of("song_magic", "aquatic", "beast_mastery"));

    private static final Map<String, ProfessionNode> BY_ID = new HashMap<>();

    static {
        for (ProfessionNode node : values()) {
            BY_ID.put(node.id, node);
        }
    }

    private final String id;
    private final Heritage parentHeritage;
    private final String displayName;
    private final String description;
    private final int pointCost;
    private final Set<String> prerequisites;
    private final Set<String> tags;
    private final List<GameplayBonus> bonuses;

    ProfessionNode(String id, Heritage parentHeritage, String displayName, String description,
                   int pointCost, Set<String> prerequisites, Set<String> tags) {
        this.id = id;
        this.parentHeritage = parentHeritage;
        this.displayName = displayName;
        this.description = description;
        this.pointCost = pointCost;
        this.prerequisites = prerequisites;
        this.tags = tags;
        this.bonuses = tradeBonuses(id);
    }

    public String getId() {
        return id;
    }

    public Heritage getParentHeritage() {
        return parentHeritage;
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

    /** What this trade pays, read through {@code SkillSystemAPI.getGameplayBonus}. */
    public List<GameplayBonus> getBonuses() {
        return bonuses;
    }

    /** Summed bonus of {@code stat} over a set of unlocked profession ids; unknown ids count nothing. */
    public static float bonus(Iterable<String> unlockedIds, GameplayStat stat) {
        float sum = 0f;
        for (String unlocked : unlockedIds) {
            ProfessionNode node = byId(unlocked);
            if (node == null) continue;
            for (GameplayBonus b : node.bonuses) {
                if (b.stat() == stat) sum += b.perLevel();
            }
        }
        return sum;
    }

    public String getTranslationKey() {
        return "profession.wizards_and_beasts." + id;
    }

    public String getDescriptionTranslationKey() {
        return getTranslationKey() + ".desc";
    }

    public static List<ProfessionNode> byHeritage(Heritage heritage) {
        List<ProfessionNode> nodes = new ArrayList<>();
        for (ProfessionNode node : values()) {
            if (node.parentHeritage == heritage) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    @Nullable
    public static ProfessionNode byId(String id) {
        return BY_ID.get(id);
    }

    /** One trade, one currency; the top of a line may pay in two of its line's. */
    private static List<GameplayBonus> tradeBonuses(String id) {
        return switch (id) {
            // Wizardkind: wandwork, wandlore, a sense for the unsaid, and the wards of high practice.
            case "wizard_apprentice" -> List.of(new GameplayBonus(GameplayStat.SPELL_MISFIRE_REDUCTION, 0.03f));
            case "wizard_wandmaker" -> List.of(new GameplayBonus(GameplayStat.ALLEGIANCE_GRIP, 1.0f));
            case "wizard_seer" -> List.of(new GameplayBonus(GameplayStat.OCCLUMENCY_SHIELD, 0.15f));
            case "wizard_archmage" -> List.of(new GameplayBonus(GameplayStat.WARD_INTEGRITY, 0.20f));
            // Goblin: Gringotts keeps its counsel, and a goblin-made thing belongs to its maker.
            case "goblin_clerk", "goblin_banker" -> List.of(new GameplayBonus(GameplayStat.TRACE_DISCRETION, 0.10f));
            case "goblin_metalsmith" -> List.of(new GameplayBonus(GameplayStat.ALLEGIANCE_GRIP, 1.0f));
            case "goblin_director" -> List.of(new GameplayBonus(GameplayStat.ALLEGIANCE_GRIP, 1.0f),
                    new GameplayBonus(GameplayStat.TRACE_DISCRETION, 0.10f));
            // House-elf: household wards, and binding-magic turned inward.
            case "elf_steward" -> List.of(new GameplayBonus(GameplayStat.WARD_INTEGRITY, 0.10f));
            case "elf_binder" -> List.of(new GameplayBonus(GameplayStat.WARD_LIFETIME, 0.20f));
            case "elf_liberator" -> List.of(new GameplayBonus(GameplayStat.CURSE_BACKLASH, 0.05f));
            // Veela: allure that creatures answer, a mind used to bending others, fire before the wand.
            case "veela_charmer" -> List.of(new GameplayBonus(GameplayStat.CREATURE_TRUST, 1.0f));
            case "veela_songbird" -> List.of(new GameplayBonus(GameplayStat.OCCLUMENCY_SHIELD, 0.15f));
            case "veela_flame_dancer" -> List.of(new GameplayBonus(GameplayStat.SPELL_MISFIRE_REDUCTION, 0.03f));
            // Giant: hide and weight against beasts.
            case "giant_bruiser", "giant_ravager" -> List.of(new GameplayBonus(GameplayStat.BEAST_DAMAGE_RESISTANCE, 0.05f));
            case "giant_warlord" -> List.of(new GameplayBonus(GameplayStat.BEAST_DAMAGE_RESISTANCE, 0.10f));
            // Centaur: the forest's trust, light older than wands, a mind the stars already read.
            case "centaur_ranger" -> List.of(new GameplayBonus(GameplayStat.CREATURE_TRUST, 1.0f));
            case "centaur_stargazer_path" -> List.of(new GameplayBonus(GameplayStat.LIGHT_REACH, 1.0f));
            case "centaur_oracle" -> List.of(new GameplayBonus(GameplayStat.OCCLUMENCY_SHIELD, 0.20f));
            // Vampire: unseen, then undescribed, then at ease with dark magic.
            case "vampire_stalker" -> List.of(new GameplayBonus(GameplayStat.TRACE_DISCRETION, 0.10f));
            case "vampire_wraith" -> List.of(new GameplayBonus(GameplayStat.TRACE_DISCRETION, 0.15f));
            case "vampire_nightlord" -> List.of(new GameplayBonus(GameplayStat.CURSE_BACKLASH, 0.05f));
            // Merpeople: reef guardians, callers of the deep, and what the sea gives up to them.
            case "merpeople_tidewarden" -> List.of(new GameplayBonus(GameplayStat.BEAST_DAMAGE_RESISTANCE, 0.05f));
            case "merpeople_deepcaller" -> List.of(new GameplayBonus(GameplayStat.CREATURE_TRUST, 1.0f));
            case "merpeople_songweaver" -> List.of(new GameplayBonus(GameplayStat.GENTLE_HARVEST, 0.25f));
            default -> throw new IllegalStateException("profession node without a trade: " + id);
        };
    }
}
