package at.koopro.wizardsandbeasts.client.render.outline;

import at.koopro.wizardsandbeasts.render.outline.OutlineEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The client map's semantics, and the box tables the renderer trusts blindly. */
class BlockOutlineClientTest {

    private static final BlockPos A = new BlockPos(0, 64, 0);
    private static final BlockPos B = new BlockPos(1, 64, 0);
    private static final BlockPos C = new BlockPos(2, 64, 0);

    private static final OutlineEntry RED_UNTIL_200 = new OutlineEntry(0xFFFF0000, 200);
    private static final OutlineEntry BLUE_UNTIL_100 = new OutlineEntry(0xFF0000FF, 100);

    @BeforeEach
    void reset() {
        ClientBlockOutlineState.clear();
    }

    // ── client state ──

    @Test
    void laterPagesOfOneHighlightAppendRatherThanReplace() {
        ClientBlockOutlineState.add(1, RED_UNTIL_200, List.of(A));
        ClientBlockOutlineState.add(1, RED_UNTIL_200, List.of(B));

        assertEquals(1, ClientBlockOutlineState.highlightCount());
        assertEquals(Set.of(A, B), ClientBlockOutlineState.blocks().keySet());
    }

    @Test
    void whereHighlightsOverlapTheNewerColourShowsTheLaterExpiryHoldsAndTheBlockIsDrawnOnce() {
        // The newer cast is shorter: it must not bring the fade forward, because the older highlight is still
        // live underneath and the block stays marked until it ends.
        ClientBlockOutlineState.add(1, RED_UNTIL_200, List.of(A, B));
        ClientBlockOutlineState.add(2, BLUE_UNTIL_100, List.of(B, C));

        Map<BlockPos, OutlineEntry> blocks = ClientBlockOutlineState.blocks();
        assertEquals(3, blocks.size());
        assertEquals(RED_UNTIL_200, blocks.get(A));
        assertEquals(new OutlineEntry(0xFF0000FF, 200), blocks.get(B));
        assertEquals(BLUE_UNTIL_100, blocks.get(C));
    }

    @Test
    void removingOneHighlightUncoversTheOneBeneathIt() {
        ClientBlockOutlineState.add(1, RED_UNTIL_200, List.of(A, B));
        ClientBlockOutlineState.add(2, BLUE_UNTIL_100, List.of(B));
        assertEquals(0xFF0000FF, ClientBlockOutlineState.blocks().get(B).argb());

        ClientBlockOutlineState.remove(2);

        // Also proves the cached merge was invalidated rather than served stale.
        assertEquals(RED_UNTIL_200, ClientBlockOutlineState.blocks().get(B));
    }

    @Test
    void clearLeavesNothingToDraw() {
        ClientBlockOutlineState.add(1, RED_UNTIL_200, List.of(A));
        ClientBlockOutlineState.clear();

        assertTrue(ClientBlockOutlineState.isEmpty());
        assertTrue(ClientBlockOutlineState.blocks().isEmpty());
    }

    // ── geometry tables ──

    @Test
    void everyFaceIsWoundCounterClockwiseFromOutside() {
        // The fill pipeline culls back faces. A reversed face is not drawn dark or inside-out; it is
        // simply missing, which on screen looks like a transparency setting.
        AABB unit = new AABB(0, 0, 0, 1, 1, 1);
        for (int[] face : BlockOutlineGeometry.FACES) {
            double[] v0 = corner(unit, face[0]);
            double[] v1 = corner(unit, face[1]);
            double[] v2 = corner(unit, face[2]);
            double[] normal = cross(sub(v1, v0), sub(v2, v0));

            double[] centre = new double[3];
            for (int c : face) {
                double[] v = corner(unit, c);
                for (int i = 0; i < 3; i++) centre[i] += v[i] / 4;
            }
            double[] outward = sub(centre, new double[]{0.5, 0.5, 0.5});
            assertTrue(dot(normal, outward) > 0,
                    () -> "face " + java.util.Arrays.toString(face) + " faces inward");
        }
    }

    @Test
    void theSixFacesCoverTheSixSides() {
        Set<String> sides = new HashSet<>();
        AABB unit = new AABB(0, 0, 0, 1, 1, 1);
        for (int[] face : BlockOutlineGeometry.FACES) {
            double[] v0 = corner(unit, face[0]);
            double[] normal = cross(sub(corner(unit, face[1]), v0), sub(corner(unit, face[2]), v0));
            sides.add(Math.round(normal[0]) + "," + Math.round(normal[1]) + "," + Math.round(normal[2]));
        }
        assertEquals(6, sides.size());
    }

    @Test
    void theTwelveEdgesAreDistinctAndEachRunsAlongOneAxis() {
        Set<String> seen = new HashSet<>();
        for (int[] edge : BlockOutlineGeometry.EDGES) {
            int axis = edge[0] ^ edge[1];
            assertTrue(axis == 1 || axis == 2 || axis == 4, "an edge must differ in exactly one axis");
            seen.add(Math.min(edge[0], edge[1]) + "-" + Math.max(edge[0], edge[1]));
        }
        assertEquals(12, seen.size());
    }

    @Test
    void cornersAreEmittedRelativeToTheCamera() {
        AABB box = new AABB(10, 64, -5, 11, 65, -4);
        assertEquals(1.0f, BlockOutlineGeometry.x(box, 1, 10.0));
        assertEquals(-0.5f, BlockOutlineGeometry.y(box, 0, 64.5));
        assertEquals(-4.0f, BlockOutlineGeometry.z(box, 4, 0.0));
    }

    private static double[] corner(AABB box, int c) {
        return new double[]{BlockOutlineGeometry.x(box, c, 0), BlockOutlineGeometry.y(box, c, 0), BlockOutlineGeometry.z(box, c, 0)};
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
