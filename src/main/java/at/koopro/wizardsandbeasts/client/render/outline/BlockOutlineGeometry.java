package at.koopro.wizardsandbeasts.client.render.outline;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NullMarked;

/**
 * A box as twelve edges and six faces, emitted relative to the camera.
 *
 * <p>Both are tables of corner indices rather than hand-written vertex runs. A corner index is three bits —
 * {@code 1} = max x, {@code 2} = max y, {@code 4} = max z — so a table can be checked mechanically, and
 * {@code BlockOutlineGeometryTest} does: the fill pipeline culls back faces, and a face wound the wrong way
 * does not look wrong, it simply is not there.
 */
@NullMarked
public final class BlockOutlineGeometry {

    /** Corner pairs, one per edge: four along x, four along y, four along z. */
    static final int[][] EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},
            {0, 2}, {1, 3}, {4, 6}, {5, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7},
    };

    /** Four corners per face, counter-clockwise seen from outside: down, up, north, south, west, east. */
    static final int[][] FACES = {
            {0, 1, 5, 4},
            {2, 6, 7, 3},
            {0, 2, 3, 1},
            {4, 5, 7, 6},
            {0, 4, 6, 2},
            {1, 3, 7, 5},
    };

    private BlockOutlineGeometry() {}

    /** For {@code RenderTypes.linesTranslucent()}: position, colour, normal (the edge direction), line width. */
    public static void edges(VertexConsumer consumer, PoseStack.Pose pose, AABB box,
                             double camX, double camY, double camZ, int argb, float lineWidth) {
        for (int[] edge : EDGES) {
            float x0 = x(box, edge[0], camX), y0 = y(box, edge[0], camY), z0 = z(box, edge[0], camZ);
            float x1 = x(box, edge[1], camX), y1 = y(box, edge[1], camY), z1 = z(box, edge[1], camZ);
            // Each edge runs along exactly one axis, so its normalised direction is that unit axis.
            int axis = edge[0] ^ edge[1];
            float nx = axis == 1 ? 1f : 0f, ny = axis == 2 ? 1f : 0f, nz = axis == 4 ? 1f : 0f;
            consumer.addVertex(pose, x0, y0, z0).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
            consumer.addVertex(pose, x1, y1, z1).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
        }
    }

    /** For {@code RenderTypes.debugFilledBox()}: position and colour, as quads. */
    public static void faces(VertexConsumer consumer, PoseStack.Pose pose, AABB box,
                             double camX, double camY, double camZ, int argb) {
        for (int[] face : FACES) {
            for (int corner : face) {
                consumer.addVertex(pose, x(box, corner, camX), y(box, corner, camY), z(box, corner, camZ))
                        .setColor(argb);
            }
        }
    }

    static float x(AABB box, int corner, double cam) {
        return (float) (((corner & 1) != 0 ? box.maxX : box.minX) - cam);
    }

    static float y(AABB box, int corner, double cam) {
        return (float) (((corner & 2) != 0 ? box.maxY : box.minY) - cam);
    }

    static float z(AABB box, int corner, double cam) {
        return (float) (((corner & 4) != 0 ? box.maxZ : box.minZ) - cam);
    }
}
