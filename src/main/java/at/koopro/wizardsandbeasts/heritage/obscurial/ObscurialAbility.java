package at.koopro.wizardsandbeasts.heritage.obscurial;

import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;

import org.jspecify.annotations.Nullable;

public enum ObscurialAbility {
    SURGE("obscurus_surge", "Surge"),
    GRASP("obscurus_grasp", "Grasp");

    private final String spellId;
    private final String displayName;

    ObscurialAbility(String spellId, String displayName) {
        this.spellId = spellId;
        this.displayName = displayName;
    }

    public String spellId() {
        return spellId;
    }

    public String displayName() {
        return displayName;
    }

    @Nullable
    public Spell spell() {
        return Spells.byId(spellId);
    }

    @Nullable
    public static ObscurialAbility bySpellId(String spellId) {
        if (spellId == null || spellId.isBlank()) return null;
        String normalized = spellId.contains(":") ? spellId.substring(spellId.indexOf(':') + 1) : spellId;
        for (ObscurialAbility ability : values()) {
            if (ability.spellId.equals(normalized)) {
                return ability;
            }
        }
        return null;
    }
}
