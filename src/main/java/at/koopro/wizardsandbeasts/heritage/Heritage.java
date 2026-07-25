package at.koopro.wizardsandbeasts.heritage;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum Heritage {
    WIZARDKIND("wizardkind", "Wizardkind",
            "Born with the gift of magic, wielders of wands and ancient knowledge. "
                    + "The most numerous of the magical peoples, organised into ministries and governed by the International Confederation of Wizards.",
            true, MagicSource.WAND, 0, 0, 0, SizeCategory.NORMAL, 0xFFAA88FF, true),

    WEREWOLF("werewolf", "Werewolf",
            "Humans afflicted with lycanthropy, gaining predatory strength beneath the moon while wrestling with a condition the wizarding world often misunderstands. "
                    + "Wolfsbane Potion can restore their reason, yet the wolf still answers when the moon is full.",
            true, MagicSource.HYBRID, 4, 0.01, 1, SizeCategory.NORMAL, 0xFF884422, true),

    OBSCURIAL("obscurial", "Obscurial",
            "A witch or wizard whose magic was twisted by denial and suppression until it became a parasitic Obscurus — a writhing shadow that lashes out when the host breaks. "
                    + "Such lives are tragically short unless the creature can somehow be separated from them.",
            false, MagicSource.INNATE, 2, 0.02, 0, SizeCategory.NORMAL, 0xFF553366, true),

    GOBLIN("goblin", "Goblin",
            "An ancient people of mettle and mathematics, reviled by outdated wand law yet indispensable to wizarding commerce. "
                    + "They remember every slight of wand legislation and forge some of the subtlest magical metalwork in Europe.",
            false, MagicSource.INNATE, -2, -0.005, 2, SizeCategory.SMALL, 0xFF887744, false),

    HOUSE_ELF("house_elf", "House-Elf",
            "A magical species bound by generations-old compulsions until given proper clothing; their domestic magic is so potent it can slip wards that stop adult wizards cold. "
                    + "A freed elf such as Dobby proved how fiercely loyal — and dangerous — independence can be.",
            false, MagicSource.INNATE, -4, 0.015, 0, SizeCategory.SMALL, 0xFF88AACC, false),

    VEELA("veela", "Veela",
            "Golden-haired beings whose dance can bewitch the unwary and whose anger may yield blazing, birdlike fury. "
                    + "Diplomatic treaties count their allure among the hazards of international tournaments.",
            true, MagicSource.HYBRID, 0, 0.02, 0, SizeCategory.NORMAL, 0xFFFFCCEE, false),

    GIANT("giant", "Giant",
            "Colossal near-human folk once common across northern Europe, diminished by Muggle warfare and bloody feuds of their own. "
                    + "Their thick hides blunt many spells that would drop a wizard outright.",
            false, MagicSource.NONE, 20, -0.02, 6, SizeCategory.LARGE, 0xFF667744, false),

    CENTAUR("centaur", "Centaur",
            "Proud half-equine forest-dwellers who scorn being servants of humans yet read futures in the wheeling stars. "
                    + "The centaurs of the Forbidden Forest kept their own chief, their own law, and answered insult with arrows.",
            false, MagicSource.INNATE, 6, 0.03, 2, SizeCategory.LARGE, 0xFF886633, false),

    VAMPIRE("vampire", "Vampire",
            "Undead predators spoken of in earnest Ministry pamphlets alongside more theatrical Muggle tales; they move among polite society only where dusk and diplomacy allow. "
                    + "Their strength is matched by traditions of daylight and superstitions they cannot wholly dismiss.",
            true, MagicSource.HYBRID, 4, 0.025, 2, SizeCategory.NORMAL, 0xFF330055, false),

    MERPEOPLE("merpeople", "Merpeople",
            "Merrows, selkies, and stranger branches share a love of deep water and a disdain for surface Ministry classification. "
                    + "Hogwarts' lake merpeople kept their own chief, their own law, and a song that almost drowned a champion.",
            false, MagicSource.INNATE, 2, -0.01, 1, SizeCategory.NORMAL, 0xFF2288AA, false);

    private static final Map<String, Heritage> BY_ID = new HashMap<>();

    static {
        for (Heritage type : values()) {
            BY_ID.put(type.id, type);
        }
    }

    private final String id;
    private final String displayName;
    private final String description;
    private final boolean canUseWand;
    private final MagicSource magicSource;
    private final double baseHealth;
    private final double baseSpeed;
    private final double baseArmor;
    private final SizeCategory sizeCategory;
    private final int color;
    private final boolean alphaAvailable;
    private List<HeritageVariant> subtypes;

    Heritage(String id, String displayName, String description,
            boolean canUseWand, MagicSource magicSource,
            double baseHealth, double baseSpeed, double baseArmor,
            SizeCategory sizeCategory, int color, boolean alphaAvailable) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.canUseWand = canUseWand;
        this.magicSource = magicSource;
        this.baseHealth = baseHealth;
        this.baseSpeed = baseSpeed;
        this.baseArmor = baseArmor;
        this.sizeCategory = sizeCategory;
        this.color = color;
        this.alphaAvailable = alphaAvailable;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public boolean canUseWand() { return canUseWand; }
    public MagicSource getMagicSource() { return magicSource; }
    public double getBaseHealth() { return baseHealth; }
    public double getBaseSpeed() { return baseSpeed; }
    public double getBaseArmor() { return baseArmor; }
    public SizeCategory getSizeCategory() { return sizeCategory; }
    public int getColor() { return color; }
    public boolean isAlphaAvailable() { return alphaAvailable; }

    public String getTranslationKey() {
        return "type.WizardsAndBeastsMod." + id;
    }

    public List<HeritageVariant> getSubtypes() {
        if (subtypes == null) {
            subtypes = new ArrayList<>();
            for (HeritageVariant sub : HeritageVariant.values()) {
                if (sub.getParentHeritage() == this) {
                    subtypes.add(sub);
                }
            }
        }
        return subtypes;
    }

    @Nullable
    public static Heritage byId(String id) {
        return BY_ID.get(id);
    }
}
