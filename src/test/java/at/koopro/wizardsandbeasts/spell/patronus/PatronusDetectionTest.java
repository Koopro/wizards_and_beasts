package at.koopro.wizardsandbeasts.spell.patronus;

import at.koopro.wizardsandbeasts.entity.spell.PatronusEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Patronus's reach follows its strength: a mist shields, a corporeal form clears, and neither is endless. */
class PatronusDetectionTest {

    @Test
    void aMistOnlyShieldsCloseWhateverItsPower() {
        assertEquals(PatronusDetection.MIST_REPEL_RADIUS, PatronusDetection.repelRadius(false, 0f));
        assertEquals(PatronusDetection.MIST_REPEL_RADIUS, PatronusDetection.repelRadius(false, 39.9f));
    }

    @Test
    void aCorporealPatronusReachesFurtherTheStrongerItIs() {
        double at = PatronusDetection.repelRadius(true, PatronusEntity.CORPOREAL_POWER);
        double stronger = PatronusDetection.repelRadius(true, PatronusEntity.CORPOREAL_POWER + 30);
        assertTrue(at > PatronusDetection.MIST_REPEL_RADIUS, "a corporeal Patronus reaches no further than a mist");
        assertTrue(stronger > at, "power made no difference");
        assertEquals(PatronusDetection.MAX_REPEL_RADIUS, PatronusDetection.repelRadius(true, 10_000f), "no cap");
    }
}
