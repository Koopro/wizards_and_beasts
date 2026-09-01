package at.koopro.wizardsandbeasts.client.spell.wheel;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two halves of the wheel that can be wrong without a client: what it offers, and what the cursor
 * is pointing at.
 *
 * <p>The bounds cases matter more than the happy path. A wizard who knows nothing, a cursor resting in
 * the centre, and the seam at due-north where the angle wraps from just-under-2pi to zero are each a
 * way to assign the wrong spell — or to index past the end of the list — in the one situation where
 * the player is least able to notice: mid-fight, on a key release.
 */
class SpellWheelModelTest {

    private static Set<String> known(String... ids) {
        return new LinkedHashSet<>(List.of(ids));
    }

    // ── entries ────────────────────────────────────────────────────────────────────────────────

    @Test
    void emptyRoster_yieldsNoEntries() {
        assertTrue(SpellWheelModel.entries(Set.of(), id -> true).isEmpty());
    }

    @Test
    void filterRemovesUnusableSpells() {
        List<String> entries = SpellWheelModel.entries(
                known("lumos", "obscurus_surge", "stupefy"),
                id -> !id.startsWith("obscurus_"));
        assertEquals(List.of("lumos", "stupefy"), entries);
    }

    @Test
    void everythingFiltered_yieldsNoEntries_ratherThanThrowing() {
        assertTrue(SpellWheelModel.entries(known("lumos", "stupefy"), id -> false).isEmpty());
    }

    @Test
    void orderIsByIdAndStable_soMuscleMemorySurvivesReopening() {
        List<String> first = SpellWheelModel.entries(known("stupefy", "accio", "lumos"), id -> true);
        List<String> second = SpellWheelModel.entries(known("lumos", "stupefy", "accio"), id -> true);
        assertEquals(List.of("accio", "lumos", "stupefy"), first);
        assertEquals(first, second);
    }

    @Test
    void blankIdsAreDropped_soAnEmptyLoadoutSlotCannotBecomeASector() {
        assertEquals(List.of("lumos"), SpellWheelModel.entries(known("", "  ", "lumos"), id -> true));
    }

    // ── hover ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void emptyWheel_hoversNothing() {
        assertEquals(SpellWheelModel.NONE, SpellWheelModel.hoveredIndex(0, -80, 0));
        assertEquals(SpellWheelModel.NONE, SpellWheelModel.hoveredIndex(50, 50, 0));
    }

    @Test
    void deadzone_hoversNothing_soAReleaseInTheCentreCancels() {
        assertEquals(SpellWheelModel.NONE, SpellWheelModel.hoveredIndex(0, 0, 6));
        assertEquals(SpellWheelModel.NONE, SpellWheelModel.hoveredIndex(5, 5, 6));
        // Just outside the deadzone is a real pick again.
        assertEquals(0, SpellWheelModel.hoveredIndex(0, -SpellWheelModel.DEADZONE_PX - 1, 6));
    }

    @Test
    void cardinalDirectionsMapToTheExpectedSectors_clockwiseFromTop() {
        int n = 4;
        assertEquals(0, SpellWheelModel.hoveredIndex(0, -80, n));   // up
        assertEquals(1, SpellWheelModel.hoveredIndex(80, 0, n));    // right
        assertEquals(2, SpellWheelModel.hoveredIndex(0, 80, n));    // down
        assertEquals(3, SpellWheelModel.hoveredIndex(-80, 0, n));   // left
    }

    @Test
    void theSeamAtDueNorthWrapsToZero_notPastTheEndOfTheList() {
        int n = 5;
        // A hair anticlockwise of straight up: the raw angle is just under a full turn.
        assertEquals(0, SpellWheelModel.hoveredIndex(-1, -80, n));
        assertEquals(0, SpellWheelModel.hoveredIndex(1, -80, n));
    }

    @Test
    void everyDirectionYieldsAnInBoundsIndex_atEveryWheelSize() {
        for (int n = 1; n <= 24; n++) {
            for (int degrees = 0; degrees < 360; degrees++) {
                double radians = Math.toRadians(degrees);
                double dx = Math.sin(radians) * 80;
                double dy = -Math.cos(radians) * 80;
                int index = SpellWheelModel.hoveredIndex(dx, dy, n);
                assertTrue(index >= 0 && index < n,
                        "n=" + n + " deg=" + degrees + " produced out-of-bounds index " + index);
            }
        }
    }

    @Test
    void singleEntryWheel_alwaysHoversThatEntry() {
        for (int degrees = 0; degrees < 360; degrees += 15) {
            double radians = Math.toRadians(degrees);
            assertEquals(0, SpellWheelModel.hoveredIndex(
                    Math.sin(radians) * 60, -Math.cos(radians) * 60, 1));
        }
    }

    @Test
    void sectorCentresLineUpWithTheAnglesTheWheelDrawsAt() {
        int n = 7;
        for (int i = 0; i < n; i++) {
            double angle = SpellWheelModel.angleOf(i, n);
            double dx = Math.cos(angle) * 80;
            double dy = Math.sin(angle) * 80;
            assertEquals(i, SpellWheelModel.hoveredIndex(dx, dy, n),
                    "the icon drawn for entry " + i + " is not the entry hovering it selects");
        }
    }
}
