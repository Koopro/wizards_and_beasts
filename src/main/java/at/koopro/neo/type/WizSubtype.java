package at.koopro.neo.type;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public enum WizSubtype {
    // Wizardkind
    PURE_BLOOD("pure_blood", WizType.WIZARDKIND, "Pure-Blood",
            "Magic runs deep through generations of wizarding heritage.",
            0, 0.005, 0, Set.of()),
    HALF_BLOOD("half_blood", WizType.WIZARDKIND, "Half-Blood",
            "Born of both magical and non-magical parentage.",
            2, 0, 0, Set.of()),
    MUGGLE_BORN("muggle_born", WizType.WIZARDKIND, "Muggle-Born",
            "First in their line to manifest magical ability.",
            4, 0, 0, Set.of("determined")),
    SQUIB("squib", WizType.WIZARDKIND, "Squib",
            "Born to magical parents but lacking magical ability.",
            2, 0, 2, Set.of("no_wand", "magic_resistant")),

    // Werewolf
    WEREWOLF_BITTEN("bitten", WizType.WEREWOLF, "Bitten",
            "Infected through the bite of another werewolf.",
            2, 0, 0, Set.of("transformation", "nocturnal", "moon_sensitive")),
    WEREWOLF_BORN("born", WizType.WEREWOLF, "Born",
            "Lycanthropy inherited from a werewolf parent.",
            0, 0.01, 0, Set.of("transformation", "nocturnal", "moon_sensitive", "enhanced_senses")),

    // Obscurial
    SUPPRESSED("suppressed", WizType.OBSCURIAL, "Suppressed",
            "The Obscurus is contained, but the struggle is constant.",
            0, 0, 2, Set.of("unstable_magic", "transformation")),
    UNLEASHED("unleashed", WizType.OBSCURIAL, "Unleashed",
            "The dark force has broken free, granting terrible power.",
            4, 0.01, 0, Set.of("unstable_magic", "transformation", "volatile")),

    // Goblin
    GOBLIN_COMMON("common", WizType.GOBLIN, "Common",
            "A skilled craftsman and keeper of ancient traditions.",
            0, 0, 0, Set.of("crafting_bonus", "treasure_sense")),
    GOBLIN_WARRIOR("warrior", WizType.GOBLIN, "Warrior",
            "Trained in the ways of goblin warfare and rebellion.",
            4, 0, 4, Set.of("crafting_bonus", "battle_hardened")),
    GOBLIN_RUNE("rune", WizType.GOBLIN, "Rune",
            "A master of goblin rune magic and enchantment.",
            2, 0, 0, Set.of("crafting_bonus", "rune_master", "treasure_sense")),

    // House-Elf
    ELF_BOUND("bound", WizType.HOUSE_ELF, "Bound",
            "Sworn to serve a wizarding family through ancient magic.",
            0, 0, 2, Set.of("teleport", "obedient")),
    ELF_FREE("free", WizType.HOUSE_ELF, "Free",
            "Unbound and independent, wielding magic for their own will.",
            0, 0.01, 0, Set.of("teleport", "independent")),

    // Veela
    VEELA_FULL("full", WizType.VEELA, "Full Veela",
            "A pure-blooded Veela of overwhelming beauty and power.",
            0, 0, 0, Set.of("allure", "transformation", "fire_affinity")),
    VEELA_HALF("half", WizType.VEELA, "Half-Veela",
            "Half-human, inheriting much of the Veela's allure.",
            2, 0, 0, Set.of("allure", "charm_resistant")),
    VEELA_QUARTER("quarter", WizType.VEELA, "Quarter-Veela",
            "A trace of Veela blood grants subtle charm.",
            2, 0, 0, Set.of("charm_resistant")),

    // Giant
    GIANT_FULL("full_giant", WizType.GIANT, "Full Giant",
            "A true giant of immense size and raw strength.",
            10, 0, 2, Set.of("magic_resistant", "crushing_blow")),
    GIANT_HALF("half_giant", WizType.GIANT, "Half-Giant",
            "Half-human heritage tempers giant strength with versatility.",
            0, 0.01, 0, Set.of("magic_resistant")),

    // Centaur
    CENTAUR_FOREST("forest", WizType.CENTAUR, "Forest",
            "A guardian of the woodland, skilled in herbalism and nature.",
            0, 0, 0, Set.of("nature_affinity", "herbalism")),
    CENTAUR_WAR("war", WizType.CENTAUR, "War",
            "A battle-hardened centaur, fierce and unyielding.",
            4, 0, 2, Set.of("battle_charge", "trampling")),
    CENTAUR_STARGAZER("stargazer", WizType.CENTAUR, "Stargazer",
            "A reader of celestial patterns, gifted in divination.",
            0, 0, 0, Set.of("divination", "star_reader")),

    // Vampire
    VAMPIRE_TURNED("turned", WizType.VAMPIRE, "Turned",
            "Transformed by the bite of a vampire, cursed with immortality.",
            0, 0, 0, Set.of("nocturnal", "blood_thirst", "undead", "sun_weakness")),
    VAMPIRE_BORN("born_vampire", WizType.VAMPIRE, "Born",
            "A pure-blood vampire, born into the dark gift.",
            2, 0.01, 0, Set.of("nocturnal", "blood_thirst", "undead", "sun_weakness", "enhanced_senses")),
    VAMPIRE_DHAMPIR("dhampir", WizType.VAMPIRE, "Dhampir",
            "Half-vampire, possessing some vampiric traits without full weakness.",
            0, 0, 0, Set.of("nocturnal", "blood_thirst", "sun_resistant")),

    // Merpeople
    MERPEOPLE_MERROW("merrow", WizType.MERPEOPLE, "Merrow",
            "An Irish water-dweller, sturdy and territorial.",
            2, 0, 0, Set.of("aquatic", "underwater_breathing")),
    MERPEOPLE_SELKIE("selkie", WizType.MERPEOPLE, "Selkie",
            "A shape-shifter between human and aquatic forms.",
            0, 0.01, 0, Set.of("aquatic", "underwater_breathing", "transformation")),
    MERPEOPLE_SIREN("siren", WizType.MERPEOPLE, "Siren",
            "Wielder of an enchanting voice that bends the will.",
            0, 0, 0, Set.of("aquatic", "underwater_breathing", "allure", "charm_voice"));

    private static final Map<String, WizSubtype> BY_ID = new HashMap<>();

    static {
        for (WizSubtype sub : values()) {
            BY_ID.put(sub.id, sub);
        }
    }

    private final String id;
    private final WizType parentType;
    private final String displayName;
    private final String description;
    private final double healthMod;
    private final double speedMod;
    private final double armorMod;
    private final Set<String> tags;

    WizSubtype(String id, WizType parentType, String displayName, String description,
               double healthMod, double speedMod, double armorMod, Set<String> tags) {
        this.id = id;
        this.parentType = parentType;
        this.displayName = displayName;
        this.description = description;
        this.healthMod = healthMod;
        this.speedMod = speedMod;
        this.armorMod = armorMod;
        this.tags = tags;
    }

    public String getId() { return id; }
    public WizType getParentType() { return parentType; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public double getHealthMod() { return healthMod; }
    public double getSpeedMod() { return speedMod; }
    public double getArmorMod() { return armorMod; }
    public Set<String> getTags() { return tags; }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public String getTranslationKey() {
        return "subtype.neo." + parentType.getId() + "." + id;
    }

    public double getTotalHealth() {
        return parentType.getBaseHealth() + healthMod;
    }

    public double getTotalSpeed() {
        return parentType.getBaseSpeed() + speedMod;
    }

    public double getTotalArmor() {
        return parentType.getBaseArmor() + armorMod;
    }

    @Nullable
    public static WizSubtype byId(String id) {
        return BY_ID.get(id);
    }
}
