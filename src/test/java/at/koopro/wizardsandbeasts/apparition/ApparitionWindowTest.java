package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.apparition.charge.ApparitionWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deliberation window: how wide it is, when it opens, and what missing it costs. */
class ApparitionWindowTest {

    @Test
    void theWindowWidensFromTwelveTicksToNineteenAcrossTheCurve() {
        assertEquals(12, ApparitionWindow.windowTicks(0.0f));
        assertEquals(19, ApparitionWindow.windowTicks(1.0f));
    }

    @Test
    void theNoviceWindowClearsHumanReactionTime() {
        // Five ticks was a quarter of a second, at or below visual reaction latency, so the novice window
        // could not be hit by reacting to the ring at all. This is the guard on that regression.
        assertTrue(ApparitionWindow.windowTicks(0.0f) >= 10,
                "a window under ~0.5s is unhittable by reaction and every honest attempt discharges");
    }

    @Test
    void proficiencyIsClampedRatherThanTrusted() {
        assertEquals(12, ApparitionWindow.windowTicks(-3.0f));
        assertEquals(19, ApparitionWindow.windowTicks(9.0f));
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
        int close = ApparitionWindow.windowClose(ApparitionTier.BLINK, 12);

        assertEquals(0, ApparitionWindow.missTicks(open, open, close));
        assertEquals(0, ApparitionWindow.missTicks(close, open, close));
        assertEquals(0, ApparitionWindow.missTicks(open + 2, open, close));
    }

    @Test
    void releasingEarlyMissesByExactlyHowEarly() {
        int open = ApparitionWindow.windowOpen(ApparitionTier.BLINK);
        int close = ApparitionWindow.windowClose(ApparitionTier.BLINK, 12);

        assertEquals(1, ApparitionWindow.missTicks(open - 1, open, close));
        assertEquals(10, ApparitionWindow.missTicks(0, open, close));
    }

    @Test
    void releasingLateMissesByExactlyHowLate() {
        int open = ApparitionWindow.windowOpen(ApparitionTier.BLINK);
        int close = ApparitionWindow.windowClose(ApparitionTier.BLINK, 12);

        assertEquals(1, ApparitionWindow.missTicks(close + 1, open, close));
        assertEquals(9, ApparitionWindow.missTicks(close + 9, open, close));
    }

    @Test
    void aLateReleaseIsAMissAndNotADischarge() {
        int open = ApparitionWindow.windowOpen(ApparitionTier.BLINK);
        int close = ApparitionWindow.windowClose(ApparitionTier.BLINK, 12);

        // One tick late used to be punished identically to walking away mid-cast.
        assertFalse(ApparitionWindow.isForcedDischarge(
                ApparitionWindow.missTicks(close + 1, open, close)));
        assertFalse(ApparitionWindow.isForcedDischarge(
                ApparitionWindow.missTicks(close, open, close)));
    }

    @Test
    void theHardCapSitsWellPastTheWindow() {
        int close = ApparitionWindow.windowClose(ApparitionTier.BLINK, 12);
        assertEquals(close + ApparitionWindow.HARD_CAP_TICKS,
                ApparitionWindow.hardCap(ApparitionTier.BLINK, 12));
        assertTrue(ApparitionWindow.hardCap(ApparitionTier.BLINK, 12) > close + 30,
                "the backstop must be far enough out that deliberating cannot reach it");
    }

    @Test
    void missIsSymmetricAboutTheWindow() {
        int open = ApparitionWindow.windowOpen(ApparitionTier.BLINK);
        int close = ApparitionWindow.windowClose(ApparitionTier.BLINK, 12);

        assertEquals(ApparitionWindow.missTicks(open - 4, open, close),
                ApparitionWindow.missTicks(close + 4, open, close),
                "four ticks early and four ticks late must cost the same");
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
