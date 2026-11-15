package at.koopro.wizardsandbeasts.client.beam;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws <strong>one</strong> straight beam segment as an oriented box with concentric bloom
 * layers. A {@link BeamShape} calls this once (laser) or many times (lightning). Nothing here
 * knows about the beam's overall shape — that is the shape's job.
 *
 * <p>Because {@code SubmitNodeCollector.submitCustomGeometry} hands back an immutable
 * {@link com.mojang.blaze3d.vertex.PoseStack.Pose} rather than a push/poppable {@code PoseStack},
 * orientation is applied to a scratch {@link Matrix4f} set from the base pose per segment instead of
 * {@code poseStack.mulPose(...)}. The rotation sequence — and therefore the math — is identical.
 */
public final class BeamGeometry {

    /** Reused per segment; see {@link #segment}. */
    private static final Matrix4f SCRATCH = new Matrix4f();

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
        // One scratch matrix and in-place rotations: this runs per segment per frame (a lightning chain is several
        // segments, a clash several chains), and the copy plus four quaternions were all garbage. Render thread only,
        // and segments never nest, so sharing the scratch is safe.
        Matrix4f m = SCRATCH.set(basePose);
        m.translate((float) from.x, (float) from.y, (float) from.z);
        m.rotateY((float) Math.toRadians(-yaw));
        m.rotateX((float) Math.toRadians(pitch));
        m.rotateX((float) (Math.PI / 2.0));
        if (style.spin() != 0f) {
            m.rotateY((float) Math.toRadians(((ticks + partialTick) * style.spin()) % 360f));
        }

        // Everything below sits on the texel grid (VISUAL_STYLE_GUIDE §2, §7): whole-pixel sizes,
        // flat tones, no sub-pixel falloff. The first renderer grew each bloom shell by half a pixel
        // at half the alpha, which drew a smooth anti-aliased glow — the one soft VFX left beside
        // the mod's crisp particles. Three layers now, each a stepped read:
        int wPx = Math.max(1, Math.round(style.width()));
        int hPx = Math.max(1, Math.round(style.height()));
        float halfW = wPx * PX * 0.5f;
        float halfH = hPx * PX * 0.5f;

        // CORE — the solid rod, plus a one-pixel spine in the lit tone when the rod is wide enough
        // to carry one, so the centre reads as structure and not as a flat bar.
        if (style.coreOpacity() > 0f) {
            emitBox(m, consumer, -halfW, 0, -halfH, halfW, length, halfH, style.coreColor(), style.coreOpacity());
            if (wPx >= 2 && hPx >= 2) {
                emitBox(m, consumer, -PX * 0.5f, 0, -PX * 0.5f, PX * 0.5f, length, PX * 0.5f,
                        mix(style.coreColor(), 0xFFFFFF, 0.6f), style.coreOpacity());
            }
        }

        // INNER GLOW — shells one whole pixel apart, each a flat step of a ramp that runs from the
        // core tone out to the glow tone, with stepped (not halving) opacity.
        int shells = Math.min(style.bloomLayers(), SHELL_STEPS.length);
        for (int i = 1; i <= shells; i++) {
            float t = shells == 1 ? 1f : (i - 1) / (float) (shells - 1);
            int tone = mix(mix(style.coreColor(), style.glowColor(), 0.5f), style.glowColor(), t);
            float d = i * PX;
            emitBox(m, consumer, -halfW - d, -d, -halfH - d, halfW + d, length + d, halfH + d,
                    tone, style.glowOpacity() * SHELL_STEPS[i - 1]);
        }

        // OUTER GLOW — sparse single pixels just outside the shells, on the four faces of the grid,
        // re-rolled every two ticks so they crawl along the beam. Transparency here is sparseness,
        // not a gradient: each spark is a whole pixel at one opacity. The share lit is the style's
        // sparkDensity out of 256 (0.375 → 96, the value this renderer shipped with).
        int sparkChance = Math.round(Mth.clamp(style.sparkDensity(), 0f, 1f) * 256f);
        if (style.glowOpacity() > 0f && sparkChance > 0) {
            float reach = (Math.max(wPx, hPx) * 0.5f + shells + 1) * PX;
            int seed = Float.floatToIntBits((float) from.x) * 31 + Float.floatToIntBits((float) from.z) * 17
                    + Float.floatToIntBits((float) from.y);
            int bucket = ticks / 2;
            int steps = (int) (length / (SPARK_SPACING * PX));
            for (int k = 0; k < steps; k++) {
                int h = hash(seed, k, bucket);
                if ((h & 0xFF) >= sparkChance) {
                    continue;
                }
                float y = (k * SPARK_SPACING + ((h >>> 8) % SPARK_SPACING)) * PX;
                float side = ((h >>> 12) & 1) == 0 ? reach : -reach;
                boolean onX = ((h >>> 13) & 1) == 0;
                float x = onX ? side : 0f;
                float z = onX ? 0f : side;
                emitBox(m, consumer, x - PX * 0.5f, y, z - PX * 0.5f, x + PX * 0.5f, y + PX, z + PX * 0.5f,
                        mix(style.glowColor(), 0xFFFFFF, 0.35f), style.glowOpacity() * SPARK_OPACITY);
            }
        }
    }

    /**
     * Inner-glow opacity per shell, stepped: a bright band, then a faint edge. Two, not three: on
     * whole pixels a third shell grew a laser to half a block thick.
     */
    private static final float[] SHELL_STEPS = {0.65f, 0.28f};
    /** One spark candidate per this many pixels of beam. */
    private static final int SPARK_SPACING = 3;
    private static final float SPARK_OPACITY = 1.0f;

    private static int hash(int seed, int k, int bucket) {
        int h = seed * 0x9E3779B1 ^ k * 0x85EBCA77 ^ bucket * 0xC2B2AE3D;
        h ^= h >>> 15;
        h *= 0x2545F491;
        h ^= h >>> 13;
        return h;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * Draws all 6 faces of the box. The caps matter under additive blending: without them a segment
     * end is the one place where light does not accumulate, so segment ends read darker than the
     * middle — visible as a dark notch at every joint of a {@link Lightning} chain and as a blunt,
     * unlit stub at the muzzle and impact point of a {@link Laser}.
     */
    private static void emitBox(Matrix4f m, VertexConsumer c, float x0, float y0, float z0,
                                float x1, float y1, float z1, int rgb, float alpha) {
        float a = Mth.clamp(alpha, 0f, 1f);
        if (a <= 0f) {
            return;
        }
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float bl = (rgb & 0xFF) / 255f;

        // Outward-CCW winding. The additive pipeline draws both sides (see BeamRenderTypes), so the
        // far wall of the tube adds light too; the translucent one still culls back faces.
        quad(m, c, r, g, bl, a, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1); // +X
        quad(m, c, r, g, bl, a, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0); // -X
        quad(m, c, r, g, bl, a, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1); // +Z
        quad(m, c, r, g, bl, a, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0); // -Z
        // Caps. Local +Y is the beam direction, so these are the far and near ends of the segment.
        quad(m, c, r, g, bl, a, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0); // +Y
        quad(m, c, r, g, bl, a, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1); // -Y
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
