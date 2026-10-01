package at.koopro.wizardsandbeasts.spell.resistance;

import at.koopro.wizardsandbeasts.spell.resistance.MagicResistanceRules.Strain;
import org.junit.jupiter.api.Test;

import static at.koopro.wizardsandbeasts.spell.resistance.MagicResistanceRules.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hide asks for several spells at once, never for luck. */
class MagicResistanceRulesTest {

    @Test
    void theHidesInTheModAskForTheseManySpellsAtOnce() {
        assertEquals(1, spellsToOvercome(0.0f), "no hide, no resistance");
        assertEquals(1, spellsToOvercome(-1.0f));
        assertEquals(1, spellsToOvercome(Float.NaN));
        assertEquals(2, spellsToOvercome(0.4f), "troll, giant, manticore, quintaped");
        assertEquals(2, spellsToOvercome(0.5f), "half-giant, basilisk — exactly two, not float noise rounded to three");
        assertEquals(3, spellsToOvercome(0.6f));
        assertEquals(4, spellsToOvercome(0.75f), "blast-ended skrewt");
        assertEquals(6, spellsToOvercome(0.83f), "dragons: about half a dozen wizards (Goblet of Fire ch. 19)");
        assertEquals(8, spellsToOvercome(0.86f), "graphorn: repels spells better than dragon hide (Fantastic Beasts)");
        assertEquals(10, spellsToOvercome(1.0f), "capped: a hide is never immunity");
    }

    @Test
    void spellsCountOnlyInsideTheWindow() {
        Strain first = afterSpell(Strain.NONE, 1000L);
        assertEquals(new Strain(1, 1000L), first);
        Strain second = afterSpell(first, 1000L + WINDOW_TICKS);
        assertEquals(2, second.spells(), "the last tick of the window still counts");
        assertTrue(overcome(second, 2));
        Strain late = afterSpell(first, 1001L + WINDOW_TICKS);
        assertEquals(new Strain(1, 1001L + WINDOW_TICKS), late, "a spell after the window starts a new count");
        assertFalse(overcome(late, 2));
    }

    @Test
    void timeRunningBackwardsStartsAFreshCount() {
        // A dimension change can move game time; a count must never get stuck because of it.
        Strain first = afterSpell(Strain.NONE, 5000L);
        assertEquals(new Strain(1, 10L), afterSpell(first, 10L));
    }
}
