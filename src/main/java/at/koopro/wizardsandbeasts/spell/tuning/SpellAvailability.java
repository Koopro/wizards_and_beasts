package at.koopro.wizardsandbeasts.spell.tuning;

import at.koopro.wizardsandbeasts.ministry.trace.LegalClass;
import at.koopro.wizardsandbeasts.ministry.trace.SpellLawRegistry;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import org.jspecify.annotations.NullMarked;

/**
 * Whether a server administrator permits a spell to be cast, and how the spell is classified for the Dark Arts
 * section. Server logic: the Unforgivable test reads the datapack spell law.
 *
 * <p>Two rules, both from {@link SpellTuning}: the spell's own enabled switch, and the global
 * "allow Unforgivable Curses" rule. Every cast path asks {@link #castAllowed} — the wand release gate
 * ({@code SpellCastGate.SPELL_DISABLED}, which the held-beam channel re-evaluates every tick) and the
 * Obscurial ability path. Learning is deliberately not gated: withdrawing a spell must not erase what players
 * have studied, so re-enabling it restores exactly what they had.
 */
@NullMarked
public final class SpellAvailability {

    private SpellAvailability() {}

    public static boolean castAllowed(Spell spell) {
        if (!SpellTuning.enabled(spell.getId())) {
            return false;
        }
        return SpellTuning.globals().allowUnforgivables() || !isUnforgivable(spell);
    }

    public static LegalClass legalClass(Spell spell) {
        return SpellLawRegistry.lawFor(spell.getId(), spell.getCategory()).legalClass();
    }

    public static boolean isUnforgivable(Spell spell) {
        return legalClass(spell) == LegalClass.UNFORGIVABLE;
    }

    /** In the Dark Arts section: the Dark Arts category, or classed as Dark or Unforgivable by the spell law. */
    public static boolean isDark(Spell spell) {
        LegalClass legal = legalClass(spell);
        return spell.getCategory() == SpellCategory.DARK_ARTS
                || legal == LegalClass.DARK || legal == LegalClass.UNFORGIVABLE;
    }
}
