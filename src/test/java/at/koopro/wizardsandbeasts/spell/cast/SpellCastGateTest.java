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
        return new Inputs(true, true, true, false, true, false, false, false, false);
    }

    @Test
    void allClear_proceeds() {
        assertNull(SpellCastGate.evaluate(allClear()));
    }

    @Test
    void eachGateFiresAtItsPosition() {
        assertEquals(SpellCastGate.NO_ACTIVE_SPELL,
                SpellCastGate.evaluate(new Inputs(false, false, false, false, true, false, false, false, false)));
        assertEquals(SpellCastGate.UNKNOWN_SPELL,
                SpellCastGate.evaluate(new Inputs(true, false, false, false, true, false, false, false, false)));
        assertEquals(SpellCastGate.SPELL_NOT_KNOWN,
                SpellCastGate.evaluate(new Inputs(true, true, false, false, true, false, false, false, false)));
        assertEquals(SpellCastGate.OBSCURIAL_ABILITY_INPUT,
                SpellCastGate.evaluate(new Inputs(true, true, true, true, true, false, false, false, false)));
        assertEquals(SpellCastGate.REQUIREMENTS_UNMET,
                SpellCastGate.evaluate(new Inputs(true, true, true, false, false, false, false, false, false)));
        assertEquals(SpellCastGate.OBSCURIAL_DARK_ONLY,
                SpellCastGate.evaluate(new Inputs(true, true, true, false, true, true, false, false, false)));
        assertEquals(SpellCastGate.OBSCURIAL_DARK_RESTRICTED,
                SpellCastGate.evaluate(new Inputs(true, true, true, false, true, false, true, false, false)));
        assertEquals(SpellCastGate.ON_COOLDOWN,
                SpellCastGate.evaluate(new Inputs(true, true, true, false, true, false, false, true, false)));
        assertEquals(SpellCastGate.GLOBAL_COOLDOWN,
                SpellCastGate.evaluate(new Inputs(true, true, true, false, true, false, false, false, true)));
    }

    @Test
    void precedence_earlierGateWins() {
        // Not-known AND on cooldown -> reports "not known" (earlier), never "recharging".
        assertEquals(SpellCastGate.SPELL_NOT_KNOWN,
                SpellCastGate.evaluate(new Inputs(true, true, false, false, true, false, false, true, true)));
        // Unresolved id AND unmet requirement -> UNKNOWN_SPELL wins.
        assertEquals(SpellCastGate.UNKNOWN_SPELL,
                SpellCastGate.evaluate(new Inputs(true, false, false, false, false, false, false, false, false)));
        // Own cooldown AND global cooldown -> ON_COOLDOWN wins.
        assertEquals(SpellCastGate.ON_COOLDOWN,
                SpellCastGate.evaluate(new Inputs(true, true, true, false, true, false, false, true, true)));
    }
}
