package at.koopro.wizardsandbeasts.treasure;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The expedition chart's search: a spent tomb is walked past, the nearest unspent one is found, and the walk ends
 * when there is nothing to find. The original bug was a second expedition charted to the tomb the first had emptied.
 */
class SiteSearchTest {

    private static final int SPACING = 32;
    private static final List<SiteSearch.Grid> ONE_GRID = List.of(new SiteSearch.Grid(SPACING));

    /** A world with a tomb in every grid cell, its start in the cell's corner chunk. */
    private static SiteSearch.Probe everyCell() {
        return (grid, cellX, cellZ) -> new SiteSearch.Site("minecraft:desert_pyramid", cellX, cellZ,
                cellX * 16 + 8, cellZ * 16 + 8);
    }

    @Test
    void theNearestTombIsChartedWhenNothingIsSpent() {
        SiteSearch.Site site = SiteSearch.nearest(0, 0, ONE_GRID, 8, everyCell(), s -> false);
        assertNotNull(site);
        assertEquals(0, site.startX());
        assertEquals(0, site.startZ());
    }

    @Test
    void aSpentTombIsWalkedPastToTheNextNearest() {
        Set<String> spent = new HashSet<>();
        SiteSearch.Site first = SiteSearch.nearest(0, 0, ONE_GRID, 8, everyCell(), s -> spent.contains(s.key()));
        assertNotNull(first);
        spent.add(first.key());
        SiteSearch.Site second = SiteSearch.nearest(0, 0, ONE_GRID, 8, everyCell(), s -> spent.contains(s.key()));
        assertNotNull(second);
        assertTrue(!second.key().equals(first.key()), "the second chart went to the emptied tomb again");
        int ring = Math.max(Math.abs(second.startX()), Math.abs(second.startZ())) / SPACING;
        assertEquals(1, ring, "the next tomb should be on the first ring out, not further");
    }

    @Test
    void repeatedChartsNeverRepeatATombWhileUnspentOnesRemain() {
        Set<String> spent = new HashSet<>();
        for (int i = 0; i < 9; i++) {
            SiteSearch.Site site = SiteSearch.nearest(0, 0, ONE_GRID, 1, everyCell(), s -> spent.contains(s.key()));
            assertNotNull(site, "chart " + i + " found nothing while tombs remained");
            assertTrue(spent.add(site.key()), "chart " + i + " repeated " + site.key());
        }
        assertNull(SiteSearch.nearest(0, 0, ONE_GRID, 1, everyCell(), s -> spent.contains(s.key())),
                "with every tomb in range spent there must be no chart");
    }

    @Test
    void noTombsMeansNoChartAndTheWalkIsBounded() {
        AtomicInteger probes = new AtomicInteger();
        int radius = 5;
        SiteSearch.Site site = SiteSearch.nearest(3, -7, ONE_GRID, radius, (g, x, z) -> {
            probes.incrementAndGet();
            return null;
        }, s -> false);
        assertNull(site);
        assertEquals((2 * radius + 1) * (2 * radius + 1), probes.get(), "every cell probed exactly once");
    }

    @Test
    void theClosestOfSeveralGridsWins() {
        // Grid 0 (spacing 32) only has a far tomb; grid 1 (spacing 20) has one close by.
        List<SiteSearch.Grid> grids = List.of(new SiteSearch.Grid(32), new SiteSearch.Grid(20));
        Map<String, SiteSearch.Site> world = new HashMap<>();
        world.put("0:0,0", new SiteSearch.Site("a", 0, 0, 200, 200));
        world.put("1:0,0", new SiteSearch.Site("b", 1, 1, 24, 24));
        SiteSearch.Site site = SiteSearch.nearest(0, 0, grids, 3, (g, x, z) -> world.get(g + ":" + x + "," + z),
                s -> false);
        assertNotNull(site);
        assertEquals("b", site.structure());
    }

    @Test
    void theLedgerRoundTripsItsSpentSites() {
        TreasureSiteLedger ledger = new TreasureSiteLedger();
        assertTrue(ledger.spend("minecraft:desert_pyramid@4,-9"));
        assertTrue(!ledger.spend("minecraft:desert_pyramid@4,-9"), "spending twice is not new");
        var json = TreasureSiteLedger.CODEC.encodeStart(JsonOps.INSTANCE, ledger).getOrThrow();
        TreasureSiteLedger back = TreasureSiteLedger.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(back.isSpent("minecraft:desert_pyramid@4,-9"));
        assertEquals(1, back.size());
    }
}
