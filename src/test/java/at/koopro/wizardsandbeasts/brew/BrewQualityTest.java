package at.koopro.wizardsandbeasts.brew;

import at.koopro.wizardsandbeasts.owl.OWLGrade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The brewer's quality band: 85..130, potency and the Potions O.W.L. adding up, nothing outside it. */
class BrewQualityTest {

    @Test
    void anUntrainedBrewerMakesThinBottles() {
        assertEquals(BrewQuality.MIN, BrewQuality.percent(0, null));
        assertEquals(BrewQuality.MIN, BrewQuality.percent(0, OWLGrade.T));
    }

    @Test
    void potencyAndGradeAddUp() {
        assertEquals(95, BrewQuality.percent(1, null));
        assertEquals(100, BrewQuality.percent(1, OWLGrade.A));
        assertEquals(115, BrewQuality.percent(3, null));
        assertEquals(125, BrewQuality.percent(3, OWLGrade.E));
    }

    @Test
    void theBandTopsOutAt130() {
        assertEquals(BrewQuality.MAX, BrewQuality.percent(3, OWLGrade.O));
        assertEquals(BrewQuality.MAX, BrewQuality.percent(99, OWLGrade.O));
        assertEquals(BrewQuality.MIN, BrewQuality.percent(-5, null));
    }
}
