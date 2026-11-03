package at.koopro.wizardsandbeasts.settlement;

import at.koopro.wizardsandbeasts.treasure.SiteSearch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A tick's share of a chart's search: it stops for its budget and for the world, and never waits on either. */
class HamletChartsTest {

    private static final int SPACING = 40;
    private static final long PLENTY = 60_000_000_000L;

    @Test
    void aWaitingCellEndsTheTicksShareAtOnce() {
        int[] asks = {0};
        SiteSearch.Walk walk = new SiteSearch.Walk(0, 0, SPACING, 5, (grid, x, z) -> {
            asks[0]++;
            return SiteSearch.PENDING;
        });
        for (int tick = 1; tick <= 20; tick++) {
            HamletCharts.advance(walk, System.nanoTime() + PLENTY);
            assertEquals(tick, asks[0], "a cell a tick while waiting");
            assertFalse(walk.finished());
        }
    }

    @Test
    void aSpentBudgetAsksNothing() {
        int[] asks = {0};
        SiteSearch.Walk walk = new SiteSearch.Walk(0, 0, SPACING, 5, (grid, x, z) -> {
            asks[0]++;
            return null;
        });
        HamletCharts.advance(walk, System.nanoTime() - 1);
        assertEquals(0, asks[0]);
    }

    @Test
    void cheapCellsAreWalkedInOneShareUntilTheWalkEnds() {
        int[] asks = {0};
        SiteSearch.Walk walk = new SiteSearch.Walk(0, 0, SPACING, 3, (grid, x, z) -> {
            asks[0]++;
            return null;
        });
        HamletCharts.advance(walk, System.nanoTime() + PLENTY);
        assertTrue(walk.finished());
        assertEquals(49, asks[0], "(2r+1)^2 cells");
    }
}
