package at.koopro.neo.type;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum WizType {
    WIZARDKIND("wizardkind", "Wizardkind",
            "Born with the gift of magic, wielders of wands and ancient knowledge.",
            true, MagicSource.WAND, 0, 0, 0, SizeCategory.NORMAL, 0xFFAA88FF),

    WEREWOLF("werewolf", "Werewolf",
            "Cursed or born with the lycanthropic condition, gaining supernatural strength.",
            true, MagicSource.HYBRID, 4, 0.01, 0, SizeCategory.NORMAL, 0xFF886644),

    OBSCURIAL("obscurial", "Obscurial",
            "A young wizard whose suppressed magic manifests as a dark, parasitic force.",
            true, MagicSource.INNATE, -2, 0, 0, SizeCategory.NORMAL, 0xFF220044),

    GOBLIN("goblin", "Goblin",
            "Ancient and cunning, masters of metalwork and the guardians of treasure.",
            false, MagicSource.INNATE, -4, 0.02, 0, SizeCategory.SMALL, 0xFFBB8833),

    HOUSE_ELF("house_elf", "House-Elf",
            "Possessing powerful innate magic, bound by ancient enchantments of servitude.",
            false, MagicSource.INNATE, -6, 0.02, 0, SizeCategory.SMALL, 0xFF88CC88),

    VEELA("veela", "Veela",
            "Beings of ethereal beauty with power over fire and charm.",
            true, MagicSource.HYBRID, 0, 0.01, 0, SizeCategory.NORMAL, 0xFFFFBBDD),

    GIANT("giant", "Giant",
            "Massive beings of immense strength, resistant to most forms of magic.",
            false, MagicSource.NONE, 20, -0.03, 4, SizeCategory.HUGE, 0xFF886655),

    CENTAUR("centaur", "Centaur",
            "Noble half-horse beings, wise in the ways of stars and nature.",
            false, MagicSource.INNATE, 6, 0.02, 2, SizeCategory.LARGE, 0xFF669944),

    VAMPIRE("vampire", "Vampire",
            "Immortal creatures of the night, sustained by blood and shadow.",
            true, MagicSource.INNATE, 4, 0.02, 0, SizeCategory.NORMAL, 0xFF880022),

    MERPEOPLE("merpeople", "Merpeople",
            "Aquatic beings dwelling in the depths, masters of water and song.",
            false, MagicSource.INNATE, 2, 0, 0, SizeCategory.NORMAL, 0xFF2288AA);

    private static final Map<String, WizType> BY_ID = new HashMap<>();

    static {
        for (WizType type : values()) {
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
    private List<WizSubtype> subtypes;

    WizType(String id, String displayName, String description,
            boolean canUseWand, MagicSource magicSource,
            double baseHealth, double baseSpeed, double baseArmor,
            SizeCategory sizeCategory, int color) {
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

    public String getTranslationKey() {
        return "type.neo." + id;
    }

    public List<WizSubtype> getSubtypes() {
        if (subtypes == null) {
            subtypes = new ArrayList<>();
            for (WizSubtype sub : WizSubtype.values()) {
                if (sub.getParentType() == this) {
                    subtypes.add(sub);
                }
            }
        }
        return subtypes;
    }

    @Nullable
    public static WizType byId(String id) {
        return BY_ID.get(id);
    }
}
