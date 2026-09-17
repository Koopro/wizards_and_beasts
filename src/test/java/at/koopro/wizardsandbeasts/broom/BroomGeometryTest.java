package at.koopro.wizardsandbeasts.broom;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds the broom's local frame to vanilla's facing.
 *
 * <p>The trail shipped seeded 2.5 blocks in front of the rider because {@code Vec3.yRot(-yaw)} maps local
 * {@code +z} to forward and the rig's {@code +z} is the bristles. Nothing threw; the rider flew through their
 * own slipstream. Every assertion here is made against vanilla's own facing vector, {@code (-sin, 0, cos)},
 * rather than against a hand-computed expected position, so a sign error cannot be copied into the test.
 */
class BroomGeometryTest {

    /**
     * A thousandth of a block. {@code Vec3.yRot} goes through {@code Mth.sin}/{@code cos}, a 65536-entry
     * lookup table, so an off-axis residue near 1e-4 is the table, not the geometry — at yaw 133 it measures
     * 1.09e-4. A pixel is 1/16 of a block at scale 1, which this is still sixty times finer than.
     */
    private static final double EPS = 1.0e-3;
    private static final float[] HEADINGS = {0f, 90f, 180f, 270f, -45f, 133f};

    /** The direction an entity with this yaw faces, as {@code Entity.getViewVector} computes it. */
    private static Vec3 forward(float yawDegrees) {
        double r = Math.toRadians(yawDegrees);
        return new Vec3(-Math.sin(r), 0.0, Math.cos(r));
    }

    @Test
    void theTailIsBehindTheRider_onTheBroomsOwnAxis_atEveryHeading() {
        for (float yaw : HEADINGS) {
            Vec3 tail = BroomGeometry.tailOffset(0.5, yaw);
            Vec3 flat = new Vec3(tail.x, 0.0, tail.z);
            double along = flat.dot(forward(yaw));
            assertEquals(-BroomGeometry.unitsToBlocks(40), along, EPS,
                    "the bristle tips must be behind the rider at yaw " + yaw);
            assertEquals(0.0, flat.subtract(forward(yaw).scale(along)).length(), EPS,
                    "the tail must sit on the broom's own axis, not off to one side, at yaw " + yaw);
        }
    }

    @Test
    void theTailRidesOnTheLiftedModel() {
        assertEquals(0.5 + BroomGeometry.unitsToBlocks(4), BroomGeometry.tailOffset(0.5, 37f).y, EPS);
    }

    @Test
    void positiveZ_meansTowardTheBristles() {
        for (float yaw : HEADINGS) {
            Vec3 offset = BroomGeometry.localToWorld(0.0, 0.0, 1.0, yaw);
            assertTrue(offset.dot(forward(yaw)) < 0.0,
                    "an authored +z must move a seat toward the bristles, not the nose, at yaw " + yaw);
        }
    }

    @Test
    void halfScaleMakesTheTailAboutOneAndAQuarterBlocksBack() {
        assertEquals(1.25, BroomGeometry.unitsToBlocks(40), EPS,
                "at MODEL_SCALE 0.5 the 40-unit fx_tail anchor is 1.25 blocks behind the rider");
    }
}
