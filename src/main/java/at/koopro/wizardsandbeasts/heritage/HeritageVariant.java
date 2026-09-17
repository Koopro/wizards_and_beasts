package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A lineage within a heritage: the family, culture or circumstance a character comes from.
 *
 * <p>For Wizardkind the lineages are blood status — pure-blood, half-blood, Muggle-born, Squib, wizard-raised — and blood
 * status is <b>culture and social standing, never magical strength</b>. Canon is explicit that blood purity is a
 * prejudice (Hermione Granger); a Squib is the one real difference, because a Squib has no magic.
 *
 * <p>The numbers left are zero for every lineage except the half-giant, whose body is smaller than a giant's. Tags are
 * traits with names in {@link HeritageTraits}.
 */
public enum HeritageVariant {
    // Wizardkind
    PURE_BLOOD("pure_blood", Heritage.WIZARDKIND, "Pure-Blood",
            "Descended from families named in the Sacred Twenty-Eight, steeped in rites, registers, and the politics of blood status. "
                    + "Their children inherit not only vaults and portraits but centuries of whispered lore about dark curses survived and survived again.",
            0, 0, 0, 0xFFC9B8F2,
            Set.of("old_family")),
    HALF_BLOOD("half_blood", Heritage.WIZARDKIND, "Half-Blood",
            "Born of one magical parent and one without, they bridge two worlds and are often the quiet majority in any common room. "
                    + "Neither pedigree nor novelty defines them — only how they choose to stand when old prejudices flare.",
            0, 0, 0, 0xFFB0A0E6,
            Set.of("two_worlds")),
    MUGGLE_BORN("muggle_born", Heritage.WIZARDKIND, "Muggle-Born",
            "The first in their family to receive a Hogwarts letter, they grow up without moving photographs yet often outpace peers raised on borrowed prejudices. "
                    + "Their gift arrived unheralded — and sometimes proves the sharper for it.",
            0, 0, 0, 0xFFD8C6F5,
            Set.of("muggle_raised")),
    SQUIB("squib", Heritage.WIZARDKIND, "Squib",
            "Raised among cauldrons and cantrips yet unable to kindle a single spark, they occupy a lonely margin of the magical world. "
                    + "Still, many see what wizards overlook — from kneazles in alleys to winged horses others insist cannot exist.",
            0, 0, 0, 0xFF8F7FA0,
            Set.of("no_wand", "no_casting", "creature_kinship")),
    ADOPTED_MAGICAL("adopted_magical", Heritage.WIZARDKIND, "Wizard-Raised",
            "Their birth certificate may be Muggle, magical, or missing entirely — what matters is that cauldrons and bedtime stories were always wizarding. "
                    + "Hogwarts letters find them the same as any other child; only the parlour portraits argue about whose fault the nose is.",
            0, 0, 0, 0xFFC4D4F8,
            Set.of("wizard_raised")),

    // Goblin
    GOBLIN_COMMON("common", Heritage.GOBLIN, "Common",
            "A clerk, artisan, or negotiator of the goblin nation — quick with ledgers, quicker with grudges, and never fooled by a smiling Ministry seal. "
                    + "They know where every galleon slept last night and who truly owns the wand in your hand.",
            0, 0, 0, 0xFF9A8962,
            Set.of("goblin_craft", "goblin_property")),
    GOBLIN_WARRIOR("warrior", Heritage.GOBLIN, "Warrior",
            "Scarred by goblin rebellions or the skirmishes that followed, they favour short blades and shorter tempers toward wand-carriers. "
                    + "History remembers their wars; Gringotts remembers their price.",
            0, 0, 0, 0xFF735A38,
            Set.of("goblin_property", "goblin_rebellions")),
    GOBLIN_RUNE("rune", Heritage.GOBLIN, "Rune",
            "A scholar of goblin-forged enchantment who reads spirals in silver the way wizards read Latin on parchment. "
                    + "When a curse-breaker needs a door that argues back, these are the hands that carved its temper.",
            0, 0, 0, 0xFF807056,
            Set.of("goblin_craft", "goblin_property", "rune_affinity")),

    // House-Elf
    ELF_BOUND("bound", Heritage.HOUSE_ELF, "Bound",
            "Still sworn to hearth and master-cloak, their magic answers household need before their own — yet that bond can shield as fiercely as it binds. "
                    + "Kreacher's story proves how cruelty and kindness alike shape what such service becomes.",
            0, 0, 0, 0xFF9BBAD6,
            Set.of("innate_apparition", "household_magic", "bound_service")),
    ELF_FREE("free", Heritage.HOUSE_ELF, "Free",
            "Given a sock or a scrap of dignity, they step off the pantry stool into a life paid in choices rather than orders. "
                    + "Dobby's laughter in a wool jumper is the emblem of what freedom costs — and what it wins.",
            0, 0, 0, 0xFF739EC4,
            Set.of("innate_apparition", "household_magic")),
    ELF_HALL("hall", Heritage.HOUSE_ELF, "Hall Elf",
            "Sworn to a great house or castle rather than a single bloodline — their magic stitches feasts, hearths, and hundred-year wards together. "
                    + "The Hogwarts kitchens proved such elves could defy even Death Eater masters when the castle itself needed defending.",
            0, 0, 0, 0xFF8AC8E0,
            Set.of("innate_apparition", "household_magic", "bound_service")),

    // Veela
    VEELA_FULL("full", Heritage.VEELA, "Full Veela",
            "A pure-blood of the species whose dance can empty minds and whose fury can sprout wings of flame. "
                    + "They do not carry wands; their bodies are the instrument, and the audience rarely forgets the recital.",
            0, 0, 0, 0xFFFFE0F5,
            Set.of("transformation", "no_wand", "allure")),
    VEELA_HALF("half", Heritage.VEELA, "Half-Veela",
            "One parent walked as legend and fire; the child walks corridors and classrooms with only a shimmer of that legacy. "
                    + "Fleur Delacour's grace in the Triwizard maze showed how quarter-blood can still outshine a crowd.",
            0, 0, 0, 0xFFFFB0DD,
            Set.of("transformation", "allure")),
    VEELA_QUARTER("quarter", Heritage.VEELA, "Quarter-Veela",
            "A single Veela grandparent leaves little more than silvery hair and an unfair advantage at first impressions. "
                    + "They pass for ordinary witches or wizards until anger thins the mask — then the room remembers old stories.",
            0, 0, 0, 0xFFE5B8D0,
            Set.of("allure")),

    // Giant
    GIANT_FULL("full_giant", Heritage.GIANT, "Full Giant",
            "Towering kin of Grawp's brutal cousins, bred in feuds and forgotten valleys where Ministry maps grow vague. "
                    + "Their footfalls shake trees; their laughter can be mistaken for weather.",
            0, 0, 0, 0xFF7A8C5C,
            Set.of("spell_resistant_hide")),
    GIANT_HALF("half_giant", Heritage.GIANT, "Half-Giant",
            "One human parent tempers size and temper alike, trading titanic reach for a door that sometimes opens in polite society. "
                    + "Hagrid and Madame Maxime proved such bloodlines can still gentle the wildest things — if the world lets them try.",
            -4, 0, -2, 0xFF556045,
            Set.of("spell_resistant_hide", "creature_kinship")),
    GIANT_CLAN("clan_warden", Heritage.GIANT, "Clan Warden",
            "Neither raider nor exile — they remember old songs, burial cairns, and which passes stay open when snow seals the range. "
                    + "When giants feud, someone must count the dead; these are the fists that stop the next charge long enough for dawn.",
            0, 0, 0, 0xFF5E6A48,
            Set.of("spell_resistant_hide")),

    // Centaur
    CENTAUR_FOREST("forest", Heritage.CENTAUR, "Forest",
            "Guardians of root and hoof-print who know every clearing thestrals use and every stream that tastes of iron. "
                    + "Firenze once left the herd to teach among humans — and paid for that choice in bruises and exile.",
            0, 0, 0, 0xFF9A784C,
            Set.of("star_reading", "forest_law")),
    CENTAUR_WAR("war", Heritage.CENTAUR, "War",
            "Bows tuned for inter-herd wars or for wizards who forget which side of the treeline is theirs. "
                    + "Their charges do not distinguish Ministry robes from Death Eater masks when both smell like arrogance.",
            0, 0, 0, 0xFF734A2A,
            Set.of("forest_law")),
    CENTAUR_STARGAZER("stargazer", Heritage.CENTAUR, "Stargazer",
            "They chart comets the way others chart gossip, reading futures in light that left the sky before Hogwarts was stone. "
                    + "Divination, to them, is not crystal balls — it is mathematics written in fire across the dark.",
            0, 0, 0, 0xFFA38458,
            Set.of("star_reading", "forest_law")),

    // Vampire
    VAMPIRE_TURNED("turned", Heritage.VAMPIRE, "Turned",
            "Once mortal, now bound to night-feeding and centuries of etiquette manuals no Ministry quite agrees on. "
                    + "Most vampires who sip politely at wizarding galas began with a bite they did not ask for.",
            0, 0, 0, 0xFF4A1A68,
            Set.of("sunlight_weakness", "blood_hunger", "transformation")),
    VAMPIRE_BORN("born_vampire", Heritage.VAMPIRE, "Born Vampire",
            "Rare as phoenix eggs — a child of the night who never knew sunlight on human skin, only hunger as heritage. "
                    + "Their elders whisper that born vampires taste power differently: cleaner, colder, and far harder to unlearn.",
            0, 0, 0, 0xFF220042,
            Set.of("sunlight_weakness", "blood_hunger")),
    VAMPIRE_DHAMPIR("dhampir", Heritage.VAMPIRE, "Dhampir",
            "Half legend, half boarding-school timetable — enough fang to feel the thirst, enough pulse to walk the diagon in daylight. "
                    + "Wizarding folktales disagree on whether they are omen or bridge; both seem to watch them anyway.",
            0, 0, 0, 0xFF5E3274,
            Set.of("blood_hunger")),

    // Merpeople
    MERPEOPLE_MERROW("merrow", Heritage.MERPEOPLE, "Merrow",
            "Grey-green sentinels of cold lakes, with nets of bone and voices that carry through water clearer than any Sonorus. "
                    + "Dumbledore spoke Mermish to broker the second task; the chief still demanded a champion's breath as forfeit.",
            0, 0, 0, 0xFF3A9CBA,
            Set.of("water_dwelling", "mermish")),
    MERPEOPLE_SELKIE("selkie", Heritage.MERPEOPLE, "Selkie",
            "Scottish and Irish shores tell of seals who shrug off skin to dance on land until the tide calls them home again. "
                    + "Their songs taste of brine and bargains — and of hearts left folded in a creel beside the boat.",
            0, 0, 0, 0xFF1A76A0,
            Set.of("water_dwelling", "transformation", "mermish")),
    MERPEOPLE_SIREN("siren", Heritage.MERPEOPLE, "Siren",
            "Where Merrow negotiate, sirens hunt — weaving dissonance into hymn until the listener forgets which way is air. "
                    + "Ancient mariners blamed them for more wrecks than weather; modern Ministries still file them under 'highly dangerous'.",
            0, 0, 0, 0xFF2C8098,
            Set.of("water_dwelling", "mermish"));

    private static final Map<String, HeritageVariant> BY_ID = new HashMap<>();

    static {
        for (HeritageVariant sub : values()) {
            BY_ID.put(sub.id, sub);
        }
    }

    private final String id;
    private final Heritage parentHeritage;
    private final String displayName;
    private final String description;
    private final double healthMod;
    private final double speedMod;
    private final double armorMod;
    private final int uiColor;
    private final Set<String> tags;

    HeritageVariant(String id, Heritage parentHeritage, String displayName, String description,
               double healthMod, double speedMod, double armorMod, int uiColor, Set<String> tags) {
        this.id = id;
        this.parentHeritage = parentHeritage;
        this.displayName = displayName;
        this.description = description;
        this.healthMod = healthMod;
        this.speedMod = speedMod;
        this.armorMod = armorMod;
        this.uiColor = uiColor;
        this.tags = tags;
    }

    public String getId() { return id; }
    public Heritage getParentHeritage() { return parentHeritage; }
    public String getDisplayName() { return displayName; }

    /**
     * Authoring-time English prose. This is the source {@link
     * at.koopro.wizardsandbeasts.datagen.ModLanguageProvider} emits as the value of
     * {@link #getDescriptionTranslationKey()}; runtime consumers must resolve the key instead so
     * the text stays translatable. Mirrors
     * {@link at.koopro.wizardsandbeasts.heritage.profession.ProfessionNode#getDescription()}.
     */
    public String getDescription() { return description; }

    public double getHealthMod() { return healthMod; }
    public double getSpeedMod() { return speedMod; }
    public double getArmorMod() { return armorMod; }
    public int getUiColor() { return uiColor; }
    public Set<String> getTags() { return tags; }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public String getTranslationKey() {
        return "subtype." + WizardsAndBeastsMod.MODID + "." + parentHeritage.getId() + "." + id;
    }

    /** Lang key holding {@link #getDescription()}; same {@code .desc} shape as {@code ProfessionNode}. */
    public String getDescriptionTranslationKey() {
        return getTranslationKey() + ".desc";
    }

    public double getTotalHealth() {
        return parentHeritage.getBaseHealth() + healthMod;
    }

    public double getTotalSpeed() {
        return parentHeritage.getBaseSpeed() + speedMod;
    }

    public double getTotalArmor() {
        return parentHeritage.getBaseArmor() + armorMod;
    }

    @Nullable
    public static HeritageVariant byId(String id) {
        return BY_ID.get(id);
    }
}
