package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.heritage.Heritage;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public enum SkillTreeId {
    // Wizard trees — shown to wand-using heritages.
    SPELL_MASTERY("spell_mastery", "Spell Mastery", 0xFFAA88FF, Audience.WIZARD),
    DARK_ARTS("dark_arts", "Dark Arts", 0xFF8B00FF, Audience.WIZARD),
    MAGIZOOLOGY("magizoology", "Magizoology", 0xFF44CC88, Audience.WIZARD),
    WANDLORE("wandlore", "Wandlore", 0xFFBB8833, Audience.WIZARD),
    HERBOLOGY("herbology", "Herbology", 0xFF44BB44, Audience.WIZARD),
    ALCHEMY("alchemy", "Alchemy", 0xFF66CCCC, Audience.WIZARD),
    // Goblin tree — Gringotts guild craft.
    GOBLIN_CRAFT("goblin_craft", "Guild Ledger", 0xFFD4AF37, Audience.GOBLIN),
    // House-elf tree — bound elf-magic.
    ELF_BOND("elf_bond", "Binding Threads", 0xFFB57EDC, Audience.HOUSE_ELF);

    /** Which heritage group a tree belongs to; drives both UI tab visibility and server unlock gating. */
    public enum Audience { WIZARD, GOBLIN, HOUSE_ELF }

    private static final Map<String, SkillTreeId> BY_ID = new HashMap<>();

    static {
        for (SkillTreeId tree : values()) {
            BY_ID.put(tree.id, tree);
        }
    }

    private final String id;
    private final String displayName;
    private final int color;
    private final Audience audience;

    SkillTreeId(String id, String displayName, int color, Audience audience) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.audience = audience;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getColor() { return color; }
    public Audience getAudience() { return audience; }

    @Nullable
    public static SkillTreeId byId(String id) {
        return BY_ID.get(id);
    }

    /** The tree audience a given heritage may access. Null/unknown heritage defaults to wizard. */
    public static Audience audienceForHeritage(@Nullable Heritage heritage) {
        if (heritage == Heritage.GOBLIN) {
            return Audience.GOBLIN;
        }
        if (heritage == Heritage.HOUSE_ELF) {
            return Audience.HOUSE_ELF;
        }
        return Audience.WIZARD;
    }
}
