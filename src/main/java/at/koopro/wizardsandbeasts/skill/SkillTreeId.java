package at.koopro.wizardsandbeasts.skill;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public enum SkillTreeId {
    SPELL_MASTERY("spell_mastery", "Spell Mastery", 0xFFAA88FF),
    DARK_ARTS("dark_arts", "Dark Arts", 0xFF8B00FF),
    MAGIZOOLOGY("magizoology", "Magizoology", 0xFF44CC88),
    WANDLORE("wandlore", "Wandlore", 0xFFBB8833),
    HERBOLOGY("herbology", "Herbology", 0xFF44BB44);

    private static final Map<String, SkillTreeId> BY_ID = new HashMap<>();

    static {
        for (SkillTreeId tree : values()) {
            BY_ID.put(tree.id, tree);
        }
    }

    private final String id;
    private final String displayName;
    private final int color;

    SkillTreeId(String id, String displayName, int color) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getColor() { return color; }

    @Nullable
    public static SkillTreeId byId(String id) {
        return BY_ID.get(id);
    }
}
