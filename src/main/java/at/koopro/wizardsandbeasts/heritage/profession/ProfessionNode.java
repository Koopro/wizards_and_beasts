package at.koopro.wizardsandbeasts.heritage.profession;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.skill.SkillNodeEffect;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // Werewolf
    WEREWOLF_TRACKER("werewolf_tracker", Heritage.WEREWOLF, "Tracker",
            "Heightened senses and fieldcraft learned under moons that do not forgive mistakes.", 1, Set.of(),
            Set.of("combat", "beast_mastery")),
    WEREWOLF_PACKMASTER("werewolf_packmaster", Heritage.WEREWOLF, "Pack Master",
            "Begins to steer the transformation instead of only surviving it — instinct shaped into strategy.", 2, Set.of("werewolf_tracker"),
            Set.of("combat", "transformation")),
    WEREWOLF_ALPHA("werewolf_alpha", Heritage.WEREWOLF, "Alpha",
            "Commands the night with a predator's certainty; lesser wolves answer before they think.", 3, Set.of("werewolf_packmaster"),
            Set.of("combat", "transformation", "beast_mastery")),

    // Obscurial
    OBSCURIAL_CHANNELER("obscurial_channeler", Heritage.OBSCURIAL, "Channeler",
            "First tentative reins on a force that would rather consume than obey.", 1, Set.of(),
            Set.of("dark_arts", "bond_magic")),
    OBSCURIAL_CONDUIT("obscurial_conduit", Heritage.OBSCURIAL, "Conduit",
            "Channels the Obscurus with intent, trading serenity for bursts of annihilating pressure.", 2, Set.of("obscurial_channeler"),
            Set.of("dark_arts", "transformation")),
    OBSCURIAL_HARBINGER("obscurial_harbinger", Heritage.OBSCURIAL, "Harbinger",
            "Walks the edge where host and parasite blur; cities remember names like this in ash.", 3, Set.of("obscurial_conduit"),
            Set.of("dark_arts", "transformation", "bond_magic")),

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
    private final List<SkillNodeEffect> effects;

    ProfessionNode(String id, Heritage parentHeritage, String displayName, String description,
                   int pointCost, Set<String> prerequisites, Set<String> tags) {
        this.id = id;
        this.parentHeritage = parentHeritage;
        this.displayName = displayName;
        this.description = description;
        this.pointCost = pointCost;
        this.prerequisites = prerequisites;
        this.tags = tags;
        this.effects = defaultEffects(id, parentHeritage, pointCost);
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

    public List<SkillNodeEffect> getEffects() {
        return effects;
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

    private static List<SkillNodeEffect> defaultEffects(String id, Heritage heritage, int pointCost) {
        List<SkillNodeEffect> result = new ArrayList<>();
        float tierScale = 1.0f - (pointCost * 0.05f);
        result.add(new SkillNodeEffect.SpellCooldownMultiplier(SpellCategory.COMBAT, Math.max(0.65f, tierScale)));
        switch (heritage) {
            case WIZARDKIND -> {
                result.add(new SkillNodeEffect.SpellDamageMultiplier(SpellCategory.UTILITY, 1.0f + (pointCost * 0.04f)));
                result.add(new SkillNodeEffect.AttributeBoost(Attributes.MAX_HEALTH, pointCost, AttributeModifier.Operation.ADD_VALUE,
                        skillModifierId(id, "max_health")));
            }
            case WEREWOLF, GIANT -> result.add(new SkillNodeEffect.AttributeBoost(Attributes.ARMOR, pointCost * 0.5d,
                    AttributeModifier.Operation.ADD_VALUE, skillModifierId(id, "armor")));
            case OBSCURIAL, VAMPIRE -> result.add(new SkillNodeEffect.SpellDamageMultiplier(SpellCategory.DARK_ARTS, 1.0f + (pointCost * 0.06f)));
            case HOUSE_ELF -> result.add(new SkillNodeEffect.SpellCooldownMultiplier(SpellCategory.DEFENSE, 1.0f - (pointCost * 0.06f)));
            case VEELA, MERPEOPLE -> result.add(new SkillNodeEffect.SpellCooldownMultiplier(SpellCategory.UTILITY, 1.0f - (pointCost * 0.05f)));
            case GOBLIN, CENTAUR -> result.add(new SkillNodeEffect.AttributeBoost(Attributes.MOVEMENT_SPEED, pointCost * 0.01d,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, skillModifierId(id, "movement_speed")));
            default -> {
            }
        }
        return List.copyOf(result);
    }

    private static Identifier skillModifierId(String nodeId, String attributePath) {
        return Identifier.fromNamespaceAndPath("wizards_and_beasts", "skill/profession/" + nodeId + "/" + attributePath);
    }
}
