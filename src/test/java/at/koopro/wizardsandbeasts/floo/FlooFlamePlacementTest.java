package at.koopro.wizardsandbeasts.floo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a hearth's green fire stands, from both ends.
 *
 * <p>Two halves of the Floo system look this up from opposite directions — the powder knows the
 * hearth and needs the flames, the flames know themselves and need the hearth — and a one-block
 * disagreement between them is invisible in code review and catastrophic in play: powder lights a
 * fire the flames block then decides is an orphan and deletes on the next neighbour update. These
 * tests exist to pin the round trip rather than either direction on its own.
 */
class FlooFlamePlacementTest {

    private static final BlockPos HEARTH = new BlockPos(10, 64, -3);

    // ── the rule itself ──

    @Test
    void flamesSitOneBlockInFrontOfTheOpening() {
        assertEquals(new BlockPos(10, 64, -4), FlooFlamePlacement.flamePos(HEARTH, Direction.NORTH));
        assertEquals(new BlockPos(10, 64, -2), FlooFlamePlacement.flamePos(HEARTH, Direction.SOUTH));
        assertEquals(new BlockPos(11, 64, -3), FlooFlamePlacement.flamePos(HEARTH, Direction.EAST));
        assertEquals(new BlockPos(9, 64, -3), FlooFlamePlacement.flamePos(HEARTH, Direction.WEST));
    }

    @Test
    void flamesAreNeverInsideTheHearthAndNeverAboveIt() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos flames = FlooFlamePlacement.flamePos(HEARTH, facing);
            assertNotEquals(HEARTH, flames, "flames must be somewhere a player can stand");
            assertEquals(HEARTH.getY(), flames.getY(), "flames belong beside the hearth, not up the chimney");
        }
    }

    // ── the round trip ──

    @Test
    void eachDirectionUndoesTheOther() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos flames = FlooFlamePlacement.flamePos(HEARTH, facing);
            assertEquals(HEARTH, FlooFlamePlacement.fireplacePos(flames, facing),
                    "flames at " + flames + " must find their hearth back");
        }
    }

    @Test
    void flamesOfOneHearthAreNotFlamesOfItsNeighbour() {
        // Two hearths back to back share no flame position, which is what stops one hearth's
        // departure from spending the other's charges.
        BlockPos behind = HEARTH.relative(Direction.SOUTH);
        assertNotEquals(FlooFlamePlacement.flamePos(HEARTH, Direction.NORTH),
                FlooFlamePlacement.flamePos(behind, Direction.SOUTH));
    }

    // ── the guard ──

    @Test
    void verticalFacingFallsBackToHorizontalInsteadOfPlacingFireUpAChimney() {
        // A vertical direction cannot come from either block's HORIZONTAL_FACING property, but it can
        // come from a caller that passed a hit face. North is the documented fallback; what matters is
        // that the result stays on the horizontal plane and stays reversible.
        for (Direction vertical : new Direction[]{Direction.UP, Direction.DOWN}) {
            BlockPos flames = FlooFlamePlacement.flamePos(HEARTH, vertical);
            assertEquals(HEARTH.getY(), flames.getY());
            assertEquals(FlooFlamePlacement.flamePos(HEARTH, Direction.NORTH), flames);
            assertEquals(HEARTH, FlooFlamePlacement.fireplacePos(flames, vertical));
        }
    }

    // ── the predicate ──

    @Test
    void isFlamePosForAgreesWithFlamePos() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos flames = FlooFlamePlacement.flamePos(HEARTH, facing);
            assertTrue(FlooFlamePlacement.isFlamePosFor(flames, HEARTH, facing));
            assertFalse(FlooFlamePlacement.isFlamePosFor(flames.above(), HEARTH, facing));
            assertFalse(FlooFlamePlacement.isFlamePosFor(HEARTH, HEARTH, facing));
        }
    }
}
