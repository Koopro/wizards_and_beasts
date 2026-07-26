package at.koopro.wizardsandbeasts.client.beam;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws <strong>one</strong> straight beam segment as an oriented box with concentric bloom
 * layers. A {@link BeamShape} calls this once (laser) or many times (lightning). Nothing here
 * knows about the beam's overall shape — that is the shape's job.
 *
 * <p>Because {@code SubmitNodeCollector.submitCustomGeometry} hands back an immutable
 * {@link com.mojang.blaze3d.vertex.PoseStack.Pose} rather than a push/poppable {@code PoseStack},
 * orientation is applied to a fresh {@link Matrix4f} copy of the base pose per segment instead of
 * {@code poseStack.mulPose(...)}. The rotation sequence — and therefore the math — is identical.
 */
public final class BeamGeometry {

    /** One pixel in blocks. Style sizes are authored in pixels to match Blockbench models. */
    private static final float PX = 1f / 16f;

    private BeamGeometry() {}

    /**
     * @param from   segment start, in the same space as the base pose (i.e. entity-relative)
     * @param to     segment end
     * @param progress 0..1 fraction of the {@code from→to} length that has grown in
     * @param basePose the matrix the collector's callback handed us (entity → camera)
     */
    static void segment(BeamStyle style, Vec3 from, Vec3 to, float progress,
                        Matrix4f basePose, VertexConsumer consumer,
                        int ticks, float partialTick) {
        if (progress <= 0f) {
            return;
        }
        float length = (float) (from.distanceTo(to) * progress);
        if (length < 1e-4f) {
            return;
        }

        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        // Copy the base pose, translate to the segment origin, then orient. The final +90° about X
        // turns "box grows up (+Y)" into "box grows along the beam direction", which lets the box
        // build stupidly along local +Y below without knowing anything about the aim.
        Matrix4f m = new Matrix4f(basePose);
        m.translate((float) from.x, (float) from.y, (float) from.z);
        m.rotate(Axis.YP.rotationDegrees(-yaw));
        m.rotate(Axis.XP.rotationDegrees(pitch));
        m.rotate(Axis.XP.rotationDegrees(90f));
        if (style.spin() != 0f) {
            m.rotate(Axis.YP.rotationDegrees(((ticks + partialTick) * style.spin()) % 360f));
        }

        float halfW = style.width() * PX * 0.5f;
        float halfH = style.height() * PX * 0.5f;
        AABB box = new AABB(-halfW, 0, -halfH, halfW, length, halfH);

        if (style.coreOpacity() > 0f) {
            emitBox(m, consumer, box, style.coreColor(), style.coreOpacity());
        }
        // Start at 1, never 0: Palladium's 1F/i/2 at i=0 is Float.Infinity, which clamps to a fully
        // opaque first glow shell. Each layer is 0.5px wider and half as bright as the previous.
        for (int i = 1; i <= style.bloomLayers(); i++) {
            emitBox(m, consumer, box.inflate(i * 0.5f * PX), style.glowColor(),
                    style.glowOpacity() / (i * 2f));
        }
    }

    /** Draws only the 4 lateral faces of the box — the two end caps are never visible on a beam. */
    private static void emitBox(Matrix4f m, VertexConsumer c, AABB b, int rgb, float alpha) {
        float a = Mth.clamp(alpha, 0f, 1f);
        if (a <= 0f) {
            return;
        }
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float bl = (rgb & 0xFF) / 255f;

        float x0 = (float) b.minX;
        float y0 = (float) b.minY;
        float z0 = (float) b.minZ;
        float x1 = (float) b.maxX;
        float y1 = (float) b.maxY;
        float z1 = (float) b.maxZ;

        // Outward-CCW winding (default pipeline culls back faces): near faces draw, far faces cull.
        quad(m, c, r, g, bl, a, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1); // +X
        quad(m, c, r, g, bl, a, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0); // -X
        quad(m, c, r, g, bl, a, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1); // +Z
        quad(m, c, r, g, bl, a, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0); // -Z
    }

    private static void quad(Matrix4f m, VertexConsumer c, float r, float g, float b, float a,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        c.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
        c.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        c.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        c.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
    }
}
