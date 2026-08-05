package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.apparition.charge.ApparitionWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deliberation window: how wide it is, when it opens, and what missing it costs. */
class ApparitionWindowTest {

    @Test
    void theWindowWidensFromFiveTicksToTwelveAcrossTheCurve() {
        assertEquals(5, ApparitionWindow.windowTicks(0.0f));
        assertEquals(12, ApparitionWindow.windowTicks(1.0f));
    }

    @Test
    void proficiencyIsClampedRatherThanTrusted() {
        assertEquals(5, ApparitionWindow.windowTicks(-3.0f));
        assertEquals(12, ApparitionWindow.windowTicks(9.0f));
    }

    @Test
    void aSkillNodeCanRaiseTheFloorWithoutRaisingTheCeilingContribution() {
        assertEquals(8, ApparitionWindow.windowTicks(0.0f, 8));
        assertEquals(15, ApparitionWindow.windowTicks(1.0f, 8));
    }

    @Test
    void theWindowOpensWhenTheChargeCompletes() {
        assertEquals(10, ApparitionWindow.windowOpen(ApparitionTier.BLINK));
        assertEquals(70, ApparitionWindow.windowOpen(ApparitionTier.ANCHORED));
    }

    @Test
    void releasingInsideTheWindowMissesByNothing() {
        int open = ApparitionWindow.windowOpen(ApparitionTier.BLINK);
        int close = ApparitionWindow.windowClose(ApparitionTier.BLINK, 5);

        assertEquals(0, ApparitionWindow.missTicks(open, open, close));
        assertEquals(0, ApparitionWindow.missTicks(close, open, close));
        assertEquals(0, ApparitionWindow.missTicks(open + 2, open, close));
    }

    @Test
    void releasingEarlyMissesByExactlyHowEarly() {
        int open = ApparitionWindow.windowOpen(ApparitionTier.BLINK);
        int close = ApparitionWindow.windowClose(ApparitionTier.BLINK, 5);

        assertEquals(1, ApparitionWindow.missTicks(open - 1, open, close));
        assertEquals(10, ApparitionWindow.missTicks(0, open, close));
    }

    @Test
    void holdingPastTheWindowDischarges() {
        int open = ApparitionWindow.windowOpen(ApparitionTier.BLINK);
        int close = ApparitionWindow.windowClose(ApparitionTier.BLINK, 5);

        assertTrue(ApparitionWindow.isForcedDischarge(
                ApparitionWindow.missTicks(close + 1, open, close)));
        assertFalse(ApparitionWindow.isForcedDischarge(
                ApparitionWindow.missTicks(close, open, close)));
    }

    @Test
    void blinkRangeScalesWithProficiencyAndAnchoredIsUnbounded() {
        assertEquals(12.0, ApparitionTier.BLINK.rangeBlocks(0.0f), 1e-9);
        assertEquals(30.0, ApparitionTier.BLINK.rangeBlocks(1.0f), 1e-9);
        assertFalse(ApparitionTier.ANCHORED.hasRangeLimit());
    }

    @Test
    void onlyAnchoredAbortsOnDamage() {
        assertFalse(ApparitionTier.BLINK.abortsOnDamage());
        assertTrue(ApparitionTier.ANCHORED.abortsOnDamage());
    }
}
