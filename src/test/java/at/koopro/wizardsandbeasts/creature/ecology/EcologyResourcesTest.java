package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.creature.ecology.EcologyResources.Cell;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyResources.Find;
import at.koopro.wizardsandbeasts.creature.schedule.AiMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The water and food search on fake terrain: where it stands, what it never reads, and what it costs. */
class EcologyResourcesTest {

    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);

    /** Flat ground: solid at y ≤ 63, open above. */
    private static Cell flat(int x, int y, int z) {
        return y <= 63 ? Cell.SOLID : Cell.OPEN;
    }

    @Test
    void itFindsALakeAndStandsDryOnItsShore() {
        // A lake filling x 3..14 at the ground's level.
        EcologyResources.Probe lake = (x, y, z) -> y == 63 && x >= 3 && x <= 14 ? Cell.WATER : flat(x, y, z);
        int found = 0;
        for (long seed = 0; seed < 20; seed++) {
            Find find = EcologyResources.findWater(ORIGIN, RandomSource.create(seed), lake);
            if (find == null) {
                continue;
            }
            found++;
            assertEquals(Cell.WATER, lake.at(find.target().getX(), find.target().getY(), find.target().getZ()));
            assertTrue(EcologyResources.standable(find.stand().getX(), find.stand().getY(), find.stand().getZ(), lake),
                    "stands on something, with room for its head: " + find);
            assertTrue(find.stand().distManhattan(find.target()) <= 2, "beside the water: " + find);
        }
        assertTrue(found >= 18, "a lake that big is found nearly every time: " + found + "/20");
    }

    @Test
    void aGrazerStandsOnItsGrassAndABrowserBesideItsTree() {
        EcologyResources.Probe meadow = (x, y, z) -> y == 63 ? Cell.FOOD_GROUND : flat(x, y, z);
        Find grass = EcologyResources.findFood(ORIGIN, RandomSource.create(1), meadow);
        assertNotNull(grass);
        assertEquals(grass.target().above(), grass.stand(), "grazes standing on the grass block");

        EcologyResources.Probe orchard = (x, y, z) -> Math.floorMod(x, 3) == 0 && Math.floorMod(z, 3) == 0
                && y >= 64 && y <= 66 ? Cell.FOOD_SOLID : flat(x, y, z);
        Find leaves = null;
        for (long seed = 0; seed < 10 && leaves == null; seed++) { // one column in nine is a trunk
            leaves = EcologyResources.findFood(ORIGIN, RandomSource.create(seed), orchard);
        }
        assertNotNull(leaves);
        assertEquals(1, leaves.stand().distManhattan(new BlockPos(leaves.target().getX(), leaves.stand().getY(),
                leaves.target().getZ())), "stands next to the trunk: " + leaves);
    }

    @Test
    void aDesertYieldsNothingAndAnUnloadedEdgeIsNeverRead() {
        assertNull(EcologyResources.findWater(ORIGIN, RandomSource.create(3), EcologyResourcesTest::flat));
        AtomicInteger reads = new AtomicInteger();
        EcologyResources.Probe unloaded = (x, y, z) -> {
            reads.incrementAndGet();
            return Cell.UNLOADED;
        };
        assertNull(EcologyResources.findWater(ORIGIN, RandomSource.create(4), unloaded));
        assertEquals(EcologyResources.COLUMNS, reads.get(), "one read per column, then it gives the column up");
    }

    @Test
    void aSearchIsBoundedAndCounted() {
        AtomicInteger reads = new AtomicInteger();
        EcologyResources.Probe counted = (x, y, z) -> {
            reads.incrementAndGet();
            return flat(x, y, z);
        };
        long before = AiMetrics.get(AiMetrics.Metric.RESOURCE_SEARCH);
        assertNull(EcologyResources.findWater(ORIGIN, RandomSource.create(5), counted));
        assertEquals(EcologyResources.COLUMNS * EcologyResources.DEPTH, reads.get());
        assertEquals(before + 1, AiMetrics.get(AiMetrics.Metric.RESOURCE_SEARCH));
    }

    @Test
    void aSmallPondIsNeverMissedAndTheNearerOfTwoWins() {
        // A pond four blocks across, six blocks off, and a far one ten blocks the other way.
        EcologyResources.Probe ponds = (x, y, z) -> y == 63
                && ((x >= 6 && x <= 9 && z >= -1 && z <= 2) || (x <= -10 && x >= -14)) ? Cell.WATER : flat(x, y, z);
        for (long seed = 0; seed < 50; seed++) {
            Find find = EcologyResources.findWater(ORIGIN, RandomSource.create(seed), ponds);
            assertNotNull(find, "seed " + seed + " missed the pond");
            assertTrue(find.target().getX() > 0, "went for the far pond: " + find);
        }
    }

    @Test
    void anOpenFoodBlockIsEatenWhereItGrows() {
        EcologyResources.Probe flowers = (x, y, z) -> y == 64 ? Cell.FOOD_OPEN : flat(x, y, z);
        Find find = EcologyResources.findFood(ORIGIN, RandomSource.create(6), flowers);
        assertNotNull(find);
        assertEquals(find.target(), find.stand());
    }
}
