package at.koopro.wizardsandbeasts.treasure;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The search a tick at a time finds exactly what the search in one go finds, and asks about each cell once. */
class SiteSearchWalkTest {

    private static final int SPACING = 40;
    private static final List<SiteSearch.Grid> ONE_GRID = List.of(new SiteSearch.Grid(SPACING));

    private static SiteSearch.Site site(int x, int z) {
        return new SiteSearch.Site("hamlet", x, z, x * 16 + 8, z * 16 + 8);
    }

    private static SiteSearch.Site run(SiteSearch.Walk walk) {
        int steps = 0;
        while (!walk.finished()) {
            walk.step();
            assertTrue(++steps < 100_000, "the walk never ended");
        }
        return walk.found();
    }

    @Test
    void itFindsWhatTheWholeSearchFinds() {
        Random random = new Random(11);
        for (int world = 0; world < 300; world++) {
            Set<Long> sites = new HashSet<>();
            int density = 1 + random.nextInt(30);
            for (int x = -6; x <= 6; x++) {
                for (int z = -6; z <= 6; z++) {
                    if (random.nextInt(density) == 0) {
                        sites.add(((long) x << 32) ^ (z & 0xFFFFFFFFL));
                    }
                }
            }
            SiteSearch.Probe probe = (grid, cellX, cellZ) -> {
                int x = Math.floorDiv(cellX, SPACING);
                int z = Math.floorDiv(cellZ, SPACING);
                // Each cell's site sits off the grid, as a real potential chunk does.
                return sites.contains(((long) x << 32) ^ (z & 0xFFFFFFFFL)) ? site(cellX + x, cellZ - z) : null;
            };
            SiteSearch.Site whole = SiteSearch.nearest(0, 0, ONE_GRID, 6, probe, s -> false);
            SiteSearch.Site walked = run(new SiteSearch.Walk(0, 0, SPACING, 6, probe));
            assertEquals(whole, walked, "world " + world);
        }
    }

    @Test
    void anEmptyWorldEndsWithNothingAfterEveryCellOnce() {
        Set<String> asked = new HashSet<>();
        int[] calls = {0};
        SiteSearch.Site found = run(new SiteSearch.Walk(0, 0, SPACING, 3, (grid, x, z) -> {
            calls[0]++;
            asked.add(x + "," + z);
            return null;
        }));
        assertNull(found);
        assertEquals(49, calls[0], "(2r+1)^2 cells");
        assertEquals(49, asked.size(), "a cell was asked about twice");
    }

    @Test
    void aSiteOnARingStopsTheWalkWhenThatRingEnds() {
        int[] calls = {0};
        SiteSearch.Walk walk = new SiteSearch.Walk(0, 0, SPACING, 8, (grid, x, z) -> {
            calls[0]++;
            return x == SPACING && z == 0 ? site(x, z) : null;
        });
        assertNotNull(run(walk));
        assertEquals(9, calls[0], "ring 0 and all of ring 1, and no further");
    }

    @Test
    void steppingAFinishedWalkAsksNothing() {
        int[] calls = {0};
        SiteSearch.Walk walk = new SiteSearch.Walk(0, 0, SPACING, 0, (grid, x, z) -> {
            calls[0]++;
            return null;
        });
        walk.step();
        assertTrue(walk.finished());
        walk.step();
        assertEquals(1, calls[0]);
        assertFalse(walk.found() != null);
    }
}
