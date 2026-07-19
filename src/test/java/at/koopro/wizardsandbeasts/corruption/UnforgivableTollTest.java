package at.koopro.wizardsandbeasts.corruption;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks which spells carry an Unforgivable's corruption toll and their relative weight. The toll table is a
 * lore statement — exactly three curses, weighted by what they do — so it is worth pinning against drift.
 */
class UnforgivableTollTest {

    @Test
    void exactlyTheThreeUnforgivablesCarryAToll() {
        assertTrue(UnforgivableToll.isUnforgivable("avada_kedavra"));
        assertTrue(UnforgivableToll.isUnforgivable("crucio"));
        assertTrue(UnforgivableToll.isUnforgivable("imperio"));

        for (String innocent : new String[] {"lumos", "stupefy", "incendio", "expelliarmus",
                "confringo", "sectumsempra", "obscurus_surge", "expecto_patronum"}) {
            assertFalse(UnforgivableToll.isUnforgivable(innocent), innocent + " must not be tolled");
            assertEquals(0.0f, UnforgivableToll.tollFor(innocent), 1e-6, innocent);
        }
    }

    @Test
    void namespacedIdsAreRecognised() {
        assertTrue(UnforgivableToll.isUnforgivable("wizards_and_beasts:crucio"),
                "spell ids reach this both bare and namespaced");
        assertEquals(UnforgivableToll.tollFor("crucio"),
                UnforgivableToll.tollFor("wizards_and_beasts:crucio"), 1e-6);
    }

    @Test
    void killingCostsMostAndControlLeast() {
        float avada = UnforgivableToll.tollFor("avada_kedavra");
        float crucio = UnforgivableToll.tollFor("crucio");
        float imperio = UnforgivableToll.tollFor("imperio");

        assertTrue(avada > crucio, "killing outright must cost more than torture");
        assertTrue(crucio > imperio, "torture must cost more than control");
        assertTrue(imperio > 0.0f);
    }

    @Test
    void unknownAndBlankIdsAreHarmless() {
        assertFalse(UnforgivableToll.isUnforgivable(""));
        assertFalse(UnforgivableToll.isUnforgivable("not_a_spell"));
        assertEquals(0.0f, UnforgivableToll.tollFor(""), 1e-6);
    }
}
