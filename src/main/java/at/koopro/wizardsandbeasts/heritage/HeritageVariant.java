package at.koopro.wizardsandbeasts.heritage;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public enum HeritageVariant {
    // Wizardkind
    PURE_BLOOD("pure_blood", Heritage.WIZARDKIND, "Pure-Blood",
            "Descended from families named in the Sacred Twenty-Eight, steeped in rites, registers, and the politics of blood status. "
                    + "Their children inherit not only vaults and portraits but centuries of whispered lore about dark curses survived and survived again.",
            0, 0, 0, 0xFFC9B8F2,
            Set.of("enhanced_bond", "dark_resistance")),
    HALF_BLOOD("half_blood", Heritage.WIZARDKIND, "Half-Blood",
            "Born of one magical parent and one without, they bridge two worlds and are often the quiet majority in any common room. "
                    + "Neither pedigree nor novelty defines them — only how they choose to stand when old prejudices flare.",
            0, 0, 0, 0xFFB0A0E6,
            Set.of("enhanced_bond")),
    MUGGLE_BORN("muggle_born", Heritage.WIZARDKIND, "Muggle-Born",
            "The first in their family to receive a Hogwarts letter, they grow up without moving photographs yet often outpace peers raised on borrowed prejudices. "
                    + "Their gift arrived unheralded — and sometimes proves the sharper for it.",
            0, 0.003, 0, 0xFFD8C6F5,
            Set.of()),
    SQUIB("squib", Heritage.WIZARDKIND, "Squib",
            "Raised among cauldrons and cantrips yet unable to kindle a single spark, they occupy a lonely margin of the magical world. "
                    + "Still, many see what wizards overlook — from kneazles in alleys to winged horses others insist cannot exist.",
            0, 0, 0, 0xFF8F7FA0,
            Set.of("no_wand", "nature_speech")),
    ADOPTED_MAGICAL("adopted_magical", Heritage.WIZARDKIND, "Wizard-Raised",
            "Their birth certificate may be Muggle, magical, or missing entirely — what matters is that cauldrons and bedtime stories were always wizarding. "
                    + "Hogwarts letters find them the same as any other child; only the parlour portraits argue about whose fault the nose is.",
            0, 0, 0, 0xFFC4D4F8,
            Set.of("enhanced_bond", "nature_speech")),

    // Werewolf
    WEREWOLF_BITTEN("bitten", Heritage.WEREWOLF, "Bitten",
            "Infected through a werewolf's bite, they did not choose the moon's verdict yet must answer it all the same. "
                    + "Remus Lupin's dignity in the face of stigma remains the measure of how such a life can still be lived with honour.",
            0, 0, 0, 0xFF7B3C1F,
            Set.of("moon_sensitive", "transformation")),
    WEREWOLF_BORN("born", Heritage.WEREWOLF, "Born",
            "Carried lycanthropy from the womb when a werewolf parent passed the condition on — rarer, stranger, and sometimes more acclimated to the wolf within. "
                    + "They still hear the moon, yet legends hint their transformations may answer to a different rhythm than a bitten victim's.",
            2, 0.005, 0, 0xFF9C5A36,
            Set.of("moon_sensitive", "transformation", "dark_resistance")),
    WEREWOLF_SAVAGE("savage_bite", Heritage.WEREWOLF, "Savage-Bitten",
            "Those deliberately torn by a predator who wanted the curse to spread — wounds deep, transformations vicious, survival a bruise-count. "
                    + "Greyback's pack left more than scars; they left a reputation that walks into every room before the victim does.",
            -1, 0, 0, 0xFF6A3018,
            Set.of("moon_sensitive", "transformation")),

    // Obscurial
    SUPPRESSED("suppressed", Heritage.OBSCURIAL, "Suppressed",
            "The Obscurus coils beneath the ribs like a held breath — present, hungry, but not yet tearing the world apart. "
                    + "Credence Barebone walked this knife-edge for years before New York learned his name.",
            0, 0, 0, 0xFF6B4879,
            Set.of("obscurus_form", "dark_resistance")),
    UNLEASHED("unleashed", Heritage.OBSCURIAL, "Unleashed",
            "The parasite and host have braided so tightly that windows shatter when they simply lose composure. "
                    + "Power on this scale seldom leaves room for old age — only ash, headlines, and the memory of a child who was told not to be magical.",
            -2, 0.008, 0, 0xFF3F244D,
            Set.of("obscurus_form", "transformation")),

    // Goblin
    GOBLIN_COMMON("common", Heritage.GOBLIN, "Common",
            "A clerk, artisan, or negotiator of the goblin nation — quick with ledgers, quicker with grudges, and never fooled by a smiling Ministry seal. "
                    + "They know where every galleon slept last night and who truly owns the wand in your hand.",
            0, 0, 0, 0xFF9A8962,
            Set.of("vault_access", "rune_affinity")),
    GOBLIN_WARRIOR("warrior", Heritage.GOBLIN, "Warrior",
            "Scarred by goblin rebellions or the skirmishes that followed, they favour short blades and shorter tempers toward wand-carriers. "
                    + "History remembers their wars; Gringotts remembers their price.",
            2, 0.005, 2, 0xFF735A38,
            Set.of("vault_access", "dark_resistance")),
    GOBLIN_RUNE("rune", Heritage.GOBLIN, "Rune",
            "A scholar of goblin-forged enchantment who reads spirals in silver the way wizards read Latin on parchment. "
                    + "When a curse-breaker needs a door that argues back, these are the hands that carved its temper.",
            -1, 0, 0, 0xFF807056,
            Set.of("vault_access", "rune_affinity", "enhanced_bond")),

    // House-Elf
    ELF_BOUND("bound", Heritage.HOUSE_ELF, "Bound",
            "Still sworn to hearth and master-cloak, their magic answers household need before their own — yet that bond can shield as fiercely as it binds. "
                    + "Kreacher's story proves how cruelty and kindness alike shape what such service becomes.",
            0, 0, 0, 0xFF9BBAD6,
            Set.of("innate_apparition", "dark_resistance")),
    ELF_FREE("free", Heritage.HOUSE_ELF, "Free",
            "Given a sock or a scrap of dignity, they step off the pantry stool into a life paid in choices rather than orders. "
                    + "Dobby's laughter in a wool jumper is the emblem of what freedom costs — and what it wins.",
            0, 0.005, 0, 0xFF739EC4,
            Set.of("innate_apparition", "nature_speech")),
    ELF_HALL("hall", Heritage.HOUSE_ELF, "Hall Elf",
            "Sworn to a great house or castle rather than a single bloodline — their magic stitches feasts, hearths, and hundred-year wards together. "
                    + "The Hogwarts kitchens proved such elves could defy even Death Eater masters when the castle itself needed defending.",
            0, 0, 0, 0xFF8AC8E0,
            Set.of("innate_apparition", "dark_resistance")),

    // Veela
    VEELA_FULL("full", Heritage.VEELA, "Full Veela",
            "A pure-blood of the species whose dance can empty minds and whose fury can sprout wings of flame. "
                    + "They do not carry wands; their bodies are the instrument, and the audience rarely forgets the recital.",
            0, 0.005, 0, 0xFFFFE0F5,
            Set.of("transformation", "no_wand")),
    VEELA_HALF("half", Heritage.VEELA, "Half-Veela",
            "One parent walked as legend and fire; the child walks corridors and classrooms with only a shimmer of that legacy. "
                    + "Fleur Delacour's grace in the Triwizard maze showed how quarter-blood can still outshine a crowd.",
            0, 0.003, 0, 0xFFFFB0DD,
            Set.of("transformation", "enhanced_bond")),
    VEELA_QUARTER("quarter", Heritage.VEELA, "Quarter-Veela",
            "A single Veela grandparent leaves little more than silvery hair and an unfair advantage at first impressions. "
                    + "They pass for ordinary witches or wizards until anger thins the mask — then the room remembers old stories.",
            0, 0, 0, 0xFFE5B8D0,
            Set.of("enhanced_bond")),

    // Giant
    GIANT_FULL("full_giant", Heritage.GIANT, "Full Giant",
            "Towering kin of Grawp's brutal cousins, bred in feuds and forgotten valleys where Ministry maps grow vague. "
                    + "Their footfalls shake trees; their laughter can be mistaken for weather.",
            2, 0, 2, 0xFF7A8C5C,
            Set.of()),
    GIANT_HALF("half_giant", Heritage.GIANT, "Half-Giant",
            "One human parent tempers size and temper alike, trading titanic reach for a door that sometimes opens in polite society. "
                    + "Hagrid and Madame Maxime proved such bloodlines can still gentle the wildest things — if the world lets them try.",
            -4, 0.01, -2, 0xFF556045,
            Set.of("enhanced_bond", "nature_speech")),
    GIANT_CLAN("clan_warden", Heritage.GIANT, "Clan Warden",
            "Neither raider nor exile — they remember old songs, burial cairns, and which passes stay open when snow seals the range. "
                    + "When giants feud, someone must count the dead; these are the fists that stop the next charge long enough for dawn.",
            0, -0.003, 1, 0xFF5E6A48,
            Set.of("nature_speech", "divination_sight")),

    // Centaur
    CENTAUR_FOREST("forest", Heritage.CENTAUR, "Forest",
            "Guardians of root and hoof-print who know every clearing thestrals use and every stream that tastes of iron. "
                    + "Firenze once left the herd to teach among humans — and paid for that choice in bruises and exile.",
            0, 0, 0, 0xFF9A784C,
            Set.of("nature_speech", "divination_sight")),
    CENTAUR_WAR("war", Heritage.CENTAUR, "War",
            "Bows tuned for inter-herd wars or for wizards who forget which side of the treeline is theirs. "
                    + "Their charges do not distinguish Ministry robes from Death Eater masks when both smell like arrogance.",
            2, 0.005, 2, 0xFF734A2A,
            Set.of("nature_speech", "dark_resistance")),
    CENTAUR_STARGAZER("stargazer", Heritage.CENTAUR, "Stargazer",
            "They chart comets the way others chart gossip, reading futures in light that left the sky before Hogwarts was stone. "
                    + "Divination, to them, is not crystal balls — it is mathematics written in fire across the dark.",
            0, -0.005, 0, 0xFFA38458,
            Set.of("divination_sight", "nature_speech", "enhanced_bond")),

    // Vampire
    VAMPIRE_TURNED("turned", Heritage.VAMPIRE, "Turned",
            "Once mortal, now bound to night-feeding and centuries of etiquette manuals no Ministry quite agrees on. "
                    + "Most vampires who sip politely at wizarding galas began with a bite they did not ask for.",
            0, 0, 0, 0xFF4A1A68,
            Set.of("sunlight_weakness", "blood_hunger", "transformation")),
    VAMPIRE_BORN("born_vampire", Heritage.VAMPIRE, "Born Vampire",
            "Rare as phoenix eggs — a child of the night who never knew sunlight on human skin, only hunger as heritage. "
                    + "Their elders whisper that born vampires taste power differently: cleaner, colder, and far harder to unlearn.",
            2, 0.005, 1, 0xFF220042,
            Set.of("sunlight_weakness", "blood_hunger", "dark_resistance")),
    VAMPIRE_DHAMPIR("dhampir", Heritage.VAMPIRE, "Dhampir",
            "Half legend, half boarding-school timetable — enough fang to feel the thirst, enough pulse to walk the diagon in daylight. "
                    + "Wizarding folktales disagree on whether they are omen or bridge; both seem to watch them anyway.",
            0, 0.003, 0, 0xFF5E3274,
            Set.of("blood_hunger", "enhanced_bond")),

    // Merpeople
    MERPEOPLE_MERROW("merrow", Heritage.MERPEOPLE, "Merrow",
            "Grey-green sentinels of cold lakes, with nets of bone and voices that carry through water clearer than any Sonorus. "
                    + "Dumbledore spoke Mermish to broker the second task; the chief still demanded a champion's breath as forfeit.",
            0, 0, 0, 0xFF3A9CBA,
            Set.of("water_breathing", "nature_speech")),
    MERPEOPLE_SELKIE("selkie", Heritage.MERPEOPLE, "Selkie",
            "Scottish and Irish shores tell of seals who shrug off skin to dance on land until the tide calls them home again. "
                    + "Their songs taste of brine and bargains — and of hearts left folded in a creel beside the boat.",
            0, 0.005, 0, 0xFF1A76A0,
            Set.of("water_breathing", "transformation", "nature_speech")),
    MERPEOPLE_SIREN("siren", Heritage.MERPEOPLE, "Siren",
            "Where Merrow negotiate, sirens hunt — weaving dissonance into hymn until the listener forgets which way is air. "
                    + "Ancient mariners blamed them for more wrecks than weather; modern Ministries still file them under 'highly dangerous'.",
            0, 0, 1, 0xFF2C8098,
            Set.of("water_breathing", "dark_resistance", "divination_sight"));

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
        return "subtype.WizardsAndBeastsMod." + parentHeritage.getId() + "." + id;
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
