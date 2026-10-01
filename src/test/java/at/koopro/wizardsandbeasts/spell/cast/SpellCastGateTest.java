package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.cast.SpellCastGate.Inputs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Locks the deterministic cast-reject precedence extracted from {@code SpellCastService}. Each case
 * fails exactly one gate (earlier gates passing) and asserts that gate wins; the precedence cases prove
 * an earlier failure short-circuits a later one, and the all-clear case proves a valid cast proceeds.
 */
class SpellCastGateTest {

    /** All gates satisfied — a castable spell off cooldown. */
    private static Inputs allClear() {
        return new Inputs(true, true, true, true, false, true, false, false, false, false, false);
    }

    @Test
    void allClear_proceeds() {
        assertNull(SpellCastGate.evaluate(allClear()));
    }

    /** The 12-argument form, with {@code spellEnabled} as the fourth input. */
    private static Inputs withEnabled(boolean implemented, boolean enabled, boolean known, boolean requirementMet,
                                      boolean onCooldown) {
        return new Inputs(true, true, implemented, enabled, known, false, requirementMet, false, false, false,
                onCooldown, false);
    }

    @Test
    void aDisabledSpellIsRefusedBeforeAnythingAboutTheCaster() {
        assertEquals(SpellCastGate.SPELL_DISABLED, SpellCastGate.evaluate(withEnabled(true, false, true, true, false)));
        // Outranks the caster-side gates: unknown, requirement, cooldown.
        assertEquals(SpellCastGate.SPELL_DISABLED, SpellCastGate.evaluate(withEnabled(true, false, false, false, true)));
        // …but not the other spell-side fact that comes first: an unwritten spell.
        assertEquals(SpellCastGate.SPELL_NOT_IMPLEMENTED, SpellCastGate.evaluate(withEnabled(false, false, true, true, false)));
        assertNull(SpellCastGate.evaluate(withEnabled(true, true, true, true, false)));
    }

    @Test
    void theOldElevenArgumentFormMeansEnabled() {
        assertEquals(allClear(), withEnabled(true, true, true, true, false));
    }

    @Test
    void eachGateFiresAtItsPosition() {
        assertEquals(SpellCastGate.NO_ACTIVE_SPELL,
                SpellCastGate.evaluate(new Inputs(false, false, true, false, false, true, false, false, false, false, false)));
        assertEquals(SpellCastGate.UNKNOWN_SPELL,
                SpellCastGate.evaluate(new Inputs(true, false, true, false, false, true, false, false, false, false, false)));
        assertEquals(SpellCastGate.SPELL_NOT_IMPLEMENTED,
                SpellCastGate.evaluate(new Inputs(true, true, false, false, false, true, false, false, false, false, false)));
        assertEquals(SpellCastGate.SPELL_NOT_KNOWN,
                SpellCastGate.evaluate(new Inputs(true, true, true, false, false, true, false, false, false, false, false)));
        assertEquals(SpellCastGate.OBSCURIAL_ABILITY_INPUT,
                SpellCastGate.evaluate(new Inputs(true, true, true, true, true, true, false, false, false, false, false)));
        assertEquals(SpellCastGate.REQUIREMENTS_UNMET,
                SpellCastGate.evaluate(new Inputs(true, true, true, true, false, false, false, false, false, false, false)));
        assertEquals(SpellCastGate.OBSCURIAL_DARK_ONLY,
                SpellCastGate.evaluate(new Inputs(true, true, true, true, false, true, true, false, false, false, false)));
        assertEquals(SpellCastGate.OBSCURIAL_DARK_RESTRICTED,
                SpellCastGate.evaluate(new Inputs(true, true, true, true, false, true, false, true, false, false, false)));
        assertEquals(SpellCastGate.ON_COOLDOWN,
                SpellCastGate.evaluate(new Inputs(true, true, true, true, false, true, false, false, false, true, false)));
        assertEquals(SpellCastGate.GLOBAL_COOLDOWN,
                SpellCastGate.evaluate(new Inputs(true, true, true, true, false, true, false, false, false, false, true)));
    }

    @Test
    void precedence_earlierGateWins() {
        // Not-known AND on cooldown -> reports "not known" (earlier), never "recharging".
        assertEquals(SpellCastGate.SPELL_NOT_KNOWN,
                SpellCastGate.evaluate(new Inputs(true, true, true, false, false, true, false, false, false, true, true)));
        // Unresolved id AND unmet requirement -> UNKNOWN_SPELL wins.
        assertEquals(SpellCastGate.UNKNOWN_SPELL,
                SpellCastGate.evaluate(new Inputs(true, false, true, false, false, false, false, false, false, false, false)));
        // Own cooldown AND global cooldown -> ON_COOLDOWN wins.
        assertEquals(SpellCastGate.ON_COOLDOWN,
                SpellCastGate.evaluate(new Inputs(true, true, true, true, false, true, false, false, false, true, true)));
        // COMING_SOON AND not known -> SPELL_NOT_IMPLEMENTED wins. A player told to go learn a spell
        // that nobody can cast would be sent on an errand with no end.
        assertEquals(SpellCastGate.SPELL_NOT_IMPLEMENTED,
                SpellCastGate.evaluate(new Inputs(true, true, false, false, false, true, false, false, false, false, false)));
        // COMING_SOON is a property of the spell, so an unresolved id still wins ahead of it.
        assertEquals(SpellCastGate.UNKNOWN_SPELL,
                SpellCastGate.evaluate(new Inputs(true, false, false, false, false, true, false, false, false, false, false)));
    }
}
