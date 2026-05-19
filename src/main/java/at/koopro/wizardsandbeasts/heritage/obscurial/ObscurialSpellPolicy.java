package at.koopro.wizardsandbeasts.heritage.obscurial;

import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;

import java.util.Set;

final class ObscurialSpellPolicy {
    private ObscurialSpellPolicy() {}

    static boolean isSpellAllowedInDarkForm(Spell spell, Set<String> darkFormAllowedIds) {
        if (spell == null) return false;
        String id = spell.getId();
        if (darkFormAllowedIds.contains(id)) return true;
        return spell.getCategory() == SpellCategory.DARK_ARTS;
    }

    static boolean isDarkFormOnlySpell(Spell spell, Set<String> darkFormOnlySpellIds) {
        return spell != null && darkFormOnlySpellIds.contains(spell.getId());
    }

    static boolean isObscurialOnlySpell(Spell spell) {
        return spell != null && spell.getId().startsWith("obscurus_");
    }

    static boolean isObscurialAbility(Spell spell) {
        return spell != null && isObscurialAbilityId(spell.getId());
    }

    static boolean isObscurialAbilityId(String spellId) {
        return spellId != null && spellId.startsWith("obscurus_");
    }
}
