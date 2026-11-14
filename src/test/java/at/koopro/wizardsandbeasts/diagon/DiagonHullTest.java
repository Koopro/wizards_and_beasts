package at.koopro.wizardsandbeasts.diagon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The hull keeps a player in the alley's box and under its roofs, and leaves one already inside alone. */
class DiagonHullTest {

    private static final AABB BOX = DiagonHull.box(new BlockPos(0, 58, 0), new Vec3i(100, 36, 50));

    @Test
    void theBoxSitsOneInsideTheTemplateAndCapsAboveTheStreet() {
        assertEquals(1, BOX.minX);
        assertEquals(99, BOX.maxX);
        assertEquals(1, BOX.minZ);
        assertEquals(49, BOX.maxZ);
        assertEquals(58 + DiagonHull.STREET + DiagonHull.CEILING, BOX.maxY);
    }

    @Test
    void aPlayerInsideStaysWhereTheyAre() {
        assertNull(DiagonHull.clamp(new Vec3(40.5, 64, 17.5), BOX));
    }

    @Test
    void aFlyerOverTheRoofsIsSetBackUnderThem() {
        assertEquals(new Vec3(40.5, BOX.maxY, 17.5), DiagonHull.clamp(new Vec3(40.5, 95, 17.5), BOX));
    }

    @Test
    void aPlayerPastTheEdgeIsSetBackOnIt() {
        assertEquals(new Vec3(99, 70, 0 + 1), DiagonHull.clamp(new Vec3(130, 70, -12), BOX));
    }
}
