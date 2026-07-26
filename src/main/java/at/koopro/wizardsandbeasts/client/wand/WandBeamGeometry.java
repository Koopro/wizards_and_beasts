package at.koopro.wizardsandbeasts.client.wand;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jspecify.annotations.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared beam body: builds a procedural jagged path between two points and draws it as three
 * nested, emissive, additive layers. Every visual decision is supplied by a {@link BeamStyle} — this
 * class owns technique, not identity.
 *
 * <h2>Path</h2>
 * The path is deliberately <em>not</em> a smoothed random walk. A cumulative walk correlates
 * neighbouring nodes, which produces gentle S-curves — a wobbling noodle, not a bolt. Each interior
 * node gets an <em>independent</em> offset inside an envelope that pins both ends to the ray, so
 * consecutive segments meet at hard corners, plus one whole-arc bow so the bolt leans as a unit.
 *
 * <h2>Shape frames</h2>
 * The caller supplies two seeds and a blend factor. Both shapes are generated in lockstep from two
 * random streams and the node offsets are interpolated, so a style can sit anywhere between hard
 * snapping (electrical crackle: the discontinuity <em>is</em> the effect) and continuous morphing
 * (a curse writhing, or water flowing). Nothing is random per frame — the shape is a pure function
 * of the seeds, so it never swims between render passes.
 *
 * <h2>Geometry</h2>
 * A layer is either a camera-facing billboard strip (soft halo, no silhouette from any angle) or a
 * closed prism swept along the path with a parallel-transported frame, which gives the inner layers
 * real volume and stops the faces twisting between segments.
 */
final class WandBeamGeometry {

    /** Fullbright packed light for unlit beam strips. */
    private static final int FULL_BRIGHT = 0xF000F0;

    /**
     * Alpha/width ramp at each end, in blocks. An older fade was a fraction of beam length, so a
     * 30-block beam spent nearly two metres fading in at each end and never looked anchored.
     */
    private static final float ENDPOINT_FADE_BLOCKS = 0.22f;

    /**
     * Camera-proximity fade, in blocks. In first person the wand tip sits about 0.7 blocks from the
     * eye, so without this the first metre of beam — a 20 cm tube at arm's length — covers half the
     * screen as a saturated additive slab, and its prism silhouette is close enough to read as flat
     * polygons. Geometry inside {@link #NEAR_FADE_START} is gone entirely; it ramps to full strength
     * at {@link #NEAR_FADE_END}. Third person is unaffected, since the camera sits behind the caster.
     */
    private static final float NEAR_FADE_START = 0.55f;
    private static final float NEAR_FADE_END = 2.10f;

    /** Width is faded less aggressively than alpha, so the beam thins toward the wand rather than pinching off. */
    private static final float NEAR_FADE_MIN_WIDTH = 0.40f;

    /** Faces around a prism segment. Six reads as round; four shows its box corners at close range. */
    private static final int PRISM_BLADES = 6;

    private static final float[] RING_COS = new float[PRISM_BLADES + 1];
    private static final float[] RING_SIN = new float[PRISM_BLADES + 1];

    static {
        for (int i = 0; i <= PRISM_BLADES; i++) {
            float angle = Mth.TWO_PI * i / PRISM_BLADES;
            RING_COS[i] = Mth.cos(angle);
            RING_SIN[i] = Mth.sin(angle);
        }
    }

    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);

    private WandBeamGeometry() {}

    /** One shape frame: the main arc plus its fork branches. */
    record Bolt(Vec3[] core, List<Vec3[]> forks) {}

    /**
     * Builds the bolt for one frame.
     *
     * @param seedA seed of the shape keyframe currently being left
     * @param seedB seed of the keyframe being approached
     * @param blend how far between them, already scaled by the style's morph factor
     */
    static @Nullable Bolt buildBolt(Vec3 start, Vec3 end, long seedA, long seedB, float blend,
                                    BeamStyle style) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.08) {
            return null;
        }

        Vec3 dir = delta.scale(1.0 / length);

        // Pick the reference axis the beam direction is *least* aligned with, so the perpendicular
        // basis stays continuous instead of snapping through 90° as the aim passes vertical.
        Vec3 reference = Math.abs(dir.y) < 0.9 ? UP : EAST;
        Vec3 perp1 = dir.cross(reference);
        if (perp1.lengthSqr() < 1e-8) {
            perp1 = dir.cross(SOUTH);
        }
        perp1 = perp1.normalize();
        Vec3 perp2 = dir.cross(perp1).normalize();

        BeamStyle.Path path = style.path();
        int segCount = Mth.clamp(
                (int) Math.ceil(length * BeamSettings.segmentsPerUnit * path.segmentDensity() / 2.0),
                BeamSettings.minPathSegments,
                BeamSettings.maxPathSegments);

        boolean morphing = blend > 1e-4f;
        RandomSource randomA = RandomSource.create(seedA);
        RandomSource randomB = morphing ? RandomSource.create(seedB) : null;

        Vec3[] core = morphedPath(start, end, perp1, perp2, segCount,
                path.jag(), path.bow(), randomA, randomB, blend, true);
        List<Vec3[]> forks = path.maxForks() <= 0
                ? List.of()
                : buildForks(core, dir, perp1, perp2, length, path, randomA);
        return new Bolt(core, forks);
    }

    /**
     * Generates the jagged polyline. When {@code randomB} is present the same node offsets are drawn
     * from both streams and interpolated, which is what turns a snapping arc into a writhing one.
     */
    private static Vec3[] morphedPath(Vec3 start, Vec3 end, Vec3 perp1, Vec3 perp2, int segCount,
                                      float jag, float bowFraction,
                                      RandomSource randomA, @Nullable RandomSource randomB,
                                      float blend, boolean pinEnd) {
        int n = segCount + 1;
        Vec3 delta = end.subtract(start);

        // One smooth bow across the whole span. Without it the bolt jitters along a ruler; with it the
        // arc as a whole leans, which is what makes each shape frame read as a different discharge.
        Vec3 bowA = bowVector(randomA, perp1, perp2, jag * bowFraction);
        Vec3 bowB = randomB == null ? Vec3.ZERO : bowVector(randomB, perp1, perp2, jag * bowFraction);

        Vec3[] points = new Vec3[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            Vec3 base = start.add(delta.scale(t));
            if (i == 0 || (pinEnd && i == n - 1)) {
                points[i] = base;
                continue;
            }
            float env = envelope(t, pinEnd);
            Vec3 offset = nodeOffset(randomA, perp1, perp2, jag * env).add(bowA.scale(env));
            if (randomB != null) {
                Vec3 offsetB = nodeOffset(randomB, perp1, perp2, jag * env).add(bowB.scale(env));
                offset = offset.add(offsetB.subtract(offset).scale(blend));
            }
            points[i] = base.add(offset);
        }
        return points;
    }

    private static Vec3 bowVector(RandomSource random, Vec3 perp1, Vec3 perp2, float amp) {
        float angle = random.nextFloat() * Mth.TWO_PI;
        float scale = amp * (0.45f + 0.55f * random.nextFloat());
        return perp1.scale(Mth.cos(angle) * scale).add(perp2.scale(Mth.sin(angle) * scale));
    }

    private static Vec3 nodeOffset(RandomSource random, Vec3 perp1, Vec3 perp2, float amp) {
        float angle = random.nextFloat() * Mth.TWO_PI;
        float radius = amp * (0.35f + 0.65f * random.nextFloat());
        return perp1.scale(Mth.cos(angle) * radius).add(perp2.scale(Mth.sin(angle) * radius));
    }

    /** Pinned arcs bulge in the middle and touch down at both ends; forks ramp up from their root only. */
    private static float envelope(float t, boolean pinEnd) {
        if (!pinEnd) {
            return Math.min(1f, t * 2.2f);
        }
        return (float) Math.pow(Mth.sin((float) (Math.PI * t)), 0.55);
    }

    /**
     * Short branches off the main arc. They hang off the already-blended core nodes, so a morphing
     * style keeps its branches attached while they themselves snap on every re-seed — which is what a
     * discharge does.
     */
    private static List<Vec3[]> buildForks(Vec3[] core, Vec3 dir, Vec3 perp1, Vec3 perp2,
                                           double beamLength, BeamStyle.Path path, RandomSource random) {
        List<Vec3[]> forks = new ArrayList<>(path.maxForks());
        int n = core.length;
        for (int i = 2; i < n - 2 && forks.size() < path.maxForks(); i++) {
            if (random.nextFloat() > path.forkChance()) {
                continue;
            }
            Vec3 root = core[i];

            float angle = random.nextFloat() * Mth.TWO_PI;
            Vec3 lateral = perp1.scale(Mth.cos(angle)).add(perp2.scale(Mth.sin(angle)));
            // Lean the branch downrange so it trails the beam instead of sticking out at a right angle.
            Vec3 branchDir = dir.scale(0.45 + 0.40 * random.nextFloat()).add(lateral).normalize();

            float len = Mth.clamp((float) (beamLength * path.forkLength() * (0.6 + 0.8 * random.nextFloat())),
                    0.35f, 3.0f);
            Vec3 tip = root.add(branchDir.scale(len));

            Vec3 forkPerp1 = branchDir.cross(lateral).normalize();
            Vec3 forkPerp2 = branchDir.cross(forkPerp1).normalize();

            forks.add(morphedPath(root, tip, forkPerp1, forkPerp2, 2 + random.nextInt(3),
                    path.jag() * 0.7f, path.bow(), random, null, 0f, false));
            i += 1; // never fork twice off adjacent nodes; that reads as a fan, not a branch
        }
        return forks;
    }

    /**
     * Draws all three layers of the bolt, plus the inner two layers on every fork. Forks skip the wide
     * outer halo: at branch width the halo is all you would see, and three additive layers on every
     * branch washes the main arc out.
     */
    static void renderBolt(Matrix4f matrix, VertexConsumer consumer, Vec3 camWorldPos, Bolt bolt,
                           BeamStyle style, float flicker, float scrollU) {
        Vec3[] core = bolt.core();
        float[] cumDist = cumulativeDistances(core);
        float totalLen = cumDist[core.length - 1];

        renderLayer(matrix, consumer, camWorldPos, core, cumDist, totalLen, style, style.outer(),
                flicker, scrollU, 1f, true);
        renderLayer(matrix, consumer, camWorldPos, core, cumDist, totalLen, style, style.mid(),
                flicker, scrollU, 1f, true);
        renderLayer(matrix, consumer, camWorldPos, core, cumDist, totalLen, style, style.core(),
                flicker, scrollU, 1f, true);

        for (Vec3[] fork : bolt.forks()) {
            float[] forkDist = cumulativeDistances(fork);
            float forkLen = forkDist[fork.length - 1];
            renderLayer(matrix, consumer, camWorldPos, fork, forkDist, forkLen, style, style.mid(),
                    flicker, scrollU, 0.6f, false);
            renderLayer(matrix, consumer, camWorldPos, fork, forkDist, forkLen, style, style.core(),
                    flicker, scrollU, 0.6f, false);
        }
    }

    private static void renderLayer(Matrix4f matrix, VertexConsumer consumer, Vec3 camWorldPos,
                                    Vec3[] path, float[] cumDist, float totalLen,
                                    BeamStyle style, BeamStyle.Layer layer,
                                    float flicker, float scrollU, float scale, boolean pinEnd) {
        if (layer.geometry() == BeamStyle.Geometry.PRISM) {
            renderPrism(matrix, consumer, camWorldPos, path, cumDist, totalLen, style, layer, flicker,
                    scrollU, scale, pinEnd);
        } else {
            renderBillboard(matrix, consumer, camWorldPos, path, cumDist, totalLen, style, layer,
                    flicker, scrollU, scale, pinEnd);
        }
    }

    /**
     * Bright bloom where the bolt leaves the wand tip.
     *
     * <p>Proximity-faded like the beam body, and for the same reason: in first person the tip is about
     * 0.7 blocks from the eye, so an unfaded white-hot bloom there is not a muzzle flash, it is a white
     * rectangle over a quarter of the screen. It effectively only shows in third person, which is the
     * only view it reads as a flash from anyway.
     */
    static void renderMuzzleFlash(Matrix4f matrix, VertexConsumer glowConsumer, Vec3 camWorldPos,
                                  Vec3 centerWorld, float radius, float r, float g, float b, float a) {
        float near = nearFade(camWorldPos, centerWorld);
        if (near <= 0f) {
            return;
        }
        float alpha = a * near;
        renderGlowQuad(matrix, glowConsumer, camWorldPos, centerWorld, radius, r, g, b, alpha * 0.75f);
        renderGlowQuad(matrix, glowConsumer, camWorldPos, centerWorld, radius * 0.45f, 1f, 1f, 1f, alpha);
    }

    /** Bloom plus scattered sparks where the bolt lands. */
    static void renderImpactFlash(Matrix4f matrix, VertexConsumer glowConsumer, Vec3 camWorldPos,
                                  Vec3 impactWorld, float radius, float r, float g, float b, float a,
                                  boolean entityHit, int sparkCount, long seed) {
        float near = nearFade(camWorldPos, impactWorld);
        if (near <= 0f) {
            return;
        }
        float rad = entityHit ? radius * 1.35f : radius;
        float alpha = Mth.clamp(a * (entityHit ? 1.25f : 1.0f) * near, 0f, 1f);

        renderGlowQuad(matrix, glowConsumer, camWorldPos, impactWorld, rad, r, g, b, alpha * 0.8f);
        renderGlowQuad(matrix, glowConsumer, camWorldPos, impactWorld, rad * 0.4f, 1f, 1f, 1f, alpha);

        if (sparkCount <= 0) {
            return;
        }
        RandomSource random = RandomSource.create(seed);
        int sparks = entityHit ? sparkCount : Math.max(1, sparkCount * 2 / 3);
        for (int i = 0; i < sparks; i++) {
            Vec3 offset = new Vec3(
                    random.nextDouble() * 2 - 1,
                    random.nextDouble() * 2 - 1,
                    random.nextDouble() * 2 - 1);
            if (offset.lengthSqr() < 1e-6) {
                continue;
            }
            offset = offset.normalize().scale(rad * (0.6 + 0.9 * random.nextDouble()));
            float sparkRad = rad * (0.16f + 0.20f * random.nextFloat());
            renderGlowQuad(matrix, glowConsumer, camWorldPos, impactWorld.add(offset), sparkRad,
                    r, g, b, alpha * (0.45f + 0.4f * random.nextFloat()));
        }
    }

    private static float[] cumulativeDistances(Vec3[] path) {
        float[] cum = new float[path.length];
        for (int i = 1; i < path.length; i++) {
            cum[i] = cum[i - 1] + (float) path[i].distanceTo(path[i - 1]);
        }
        return cum;
    }

    /**
     * One camera-facing strip along the polyline. A single billboard, not a crossed pair: under
     * additive blending a second quad simply doubles the brightness of the whole layer, which blows
     * the beam out to a white slab. The muzzle bloom covers the degenerate head-on view instead.
     */
    private static void renderBillboard(Matrix4f matrix, VertexConsumer consumer, Vec3 camWorldPos,
                                        Vec3[] path, float[] cumDist, float totalLen,
                                        BeamStyle style, BeamStyle.Layer layer,
                                        float flicker, float scrollU, float scale, boolean pinEnd) {
        float uvScale = 1f / Math.max(0.05f, style.pulse().uvBlocksPerTile());
        float alphaScale = layer.geometry().alphaScale();
        int max = path.length - 1;
        for (int i = 0; i < max; i++) {
            Vec3 p1 = path[i];
            Vec3 p2 = path[i + 1];
            Vec3 tangent = p2.subtract(p1);
            double tLen = tangent.length();
            if (tLen < 1e-6) {
                continue;
            }
            tangent = tangent.scale(1.0 / tLen);

            float n1 = nearFade(camWorldPos, p1);
            float n2 = nearFade(camWorldPos, p2);
            if (n1 <= 0f && n2 <= 0f) {
                continue;
            }
            float f1 = endFade(cumDist[i], totalLen, pinEnd);
            float f2 = endFade(cumDist[i + 1], totalLen, pinEnd);

            float w1 = layer.width() * scale * f1 * widthFade(n1);
            float w2 = layer.width() * scale * f2 * widthFade(n2);
            float a1 = Mth.clamp(layer.alpha() * alphaScale * flicker * f1 * n1, 0f, 1f);
            float a2 = Mth.clamp(layer.alpha() * alphaScale * flicker * f2 * n2, 0f, 1f);

            float u1 = scrollU + cumDist[i] * uvScale;
            float u2 = scrollU + cumDist[i + 1] * uvScale;

            Vec3 mid = p1.add(p2).scale(0.5);
            Vec3 toCam = camWorldPos.subtract(mid);
            if (toCam.lengthSqr() < 1e-8) {
                toCam = tangent.cross(UP);
            }
            toCam = toCam.normalize();

            Vec3 side = tangent.cross(toCam);
            if (side.lengthSqr() < 1e-8) {
                side = tangent.cross(UP);
            }
            side = side.normalize();

            Vec3 off1 = side.scale(w1);
            Vec3 off2 = side.scale(w2);

            // U runs along the beam (REPEAT, scrolled) and V across its width (CLAMP, soft edge) —
            // which is the way wand_beam.png is authored: 128 tileable pixels of crackle detail
            // along its length, 32 pixels of alpha falloff across it.
            quad(matrix, consumer,
                    p1.add(off1), p2.add(off2), p2.subtract(off2), p1.subtract(off1),
                    layer.r(), layer.g(), layer.b(), a1, a2, a2, a1, u1, u2, 0f, 1f,
                    (float) toCam.x, (float) toCam.y, (float) toCam.z);
        }
    }

    /**
     * Sweeps a closed prism along the polyline. The cross-section frame is <em>parallel-transported</em>
     * from segment to segment — rotated by the minimal rotation that takes the previous tangent onto
     * the current one — rather than rebuilt from a world axis each time. Rebuilding it per segment
     * makes the faces twist at every corner and the seams strobe as the beam swings; transporting it
     * keeps neighbouring rings aligned, so the tube stays a tube.
     */
    private static void renderPrism(Matrix4f matrix, VertexConsumer consumer, Vec3 camWorldPos,
                                    Vec3[] path, float[] cumDist, float totalLen,
                                    BeamStyle style, BeamStyle.Layer layer,
                                    float flicker, float scrollU, float scale, boolean pinEnd) {
        float uvScale = 1f / Math.max(0.05f, style.pulse().uvBlocksPerTile());
        float alphaScale = layer.geometry().alphaScale();
        int max = path.length - 1;

        Vec3 prevTangent = null;
        Vec3 frameU = null;
        // Ring offsets are rebuilt per segment and read four times each; keeping them as primitives in
        // one scratch array is what stops this loop allocating a Vec3 per blade per vertex, which at
        // twenty simultaneous beams is tens of megabytes a second of garbage.
        double[] ring = new double[(PRISM_BLADES + 1) * 3];

        for (int i = 0; i < max; i++) {
            Vec3 p1 = path[i];
            Vec3 p2 = path[i + 1];
            Vec3 tangent = p2.subtract(p1);
            double tLen = tangent.length();
            if (tLen < 1e-6) {
                continue;
            }
            tangent = tangent.scale(1.0 / tLen);

            if (frameU == null) {
                frameU = orthogonalTo(tangent);
            } else {
                frameU = orthonormalize(transport(frameU, prevTangent, tangent), tangent);
            }
            prevTangent = tangent;
            Vec3 frameV = tangent.cross(frameU).normalize();

            // The frame has to be transported through every segment even when one is invisible, or the
            // ring would twist across the gap — so this test comes after the transport, not before it.
            float n1 = nearFade(camWorldPos, p1);
            float n2 = nearFade(camWorldPos, p2);
            if (n1 <= 0f && n2 <= 0f) {
                continue;
            }
            float f1 = endFade(cumDist[i], totalLen, pinEnd);
            float f2 = endFade(cumDist[i + 1], totalLen, pinEnd);

            float w1 = layer.width() * scale * f1 * widthFade(n1);
            float w2 = layer.width() * scale * f2 * widthFade(n2);
            float a1 = Mth.clamp(layer.alpha() * alphaScale * flicker * f1 * n1, 0f, 1f);
            float a2 = Mth.clamp(layer.alpha() * alphaScale * flicker * f2 * n2, 0f, 1f);

            float u1 = scrollU + cumDist[i] * uvScale;
            float u2 = scrollU + cumDist[i + 1] * uvScale;

            for (int k = 0; k <= PRISM_BLADES; k++) {
                ring[k * 3] = frameU.x * RING_COS[k] + frameV.x * RING_SIN[k];
                ring[k * 3 + 1] = frameU.y * RING_COS[k] + frameV.y * RING_SIN[k];
                ring[k * 3 + 2] = frameU.z * RING_COS[k] + frameV.z * RING_SIN[k];
            }

            for (int blade = 0; blade < PRISM_BLADES; blade++) {
                int o0 = blade * 3;
                int o1 = o0 + 3;

                double nx = ring[o0] + ring[o1];
                double ny = ring[o0 + 1] + ring[o1 + 1];
                double nz = ring[o0 + 2] + ring[o1 + 2];
                double nLen = Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nLen < 1e-8) {
                    nx = ring[o0];
                    ny = ring[o0 + 1];
                    nz = ring[o0 + 2];
                } else {
                    nx /= nLen;
                    ny /= nLen;
                    nz /= nLen;
                }

                // Same UV convention as the billboard: U along the beam, V across the face, so each
                // blade of the prism carries the texture's soft edge on its silhouette. Corners run
                // (p1,ring0) -> (p2,ring0) -> (p2,ring1) -> (p1,ring1).
                quadRaw(matrix, consumer,
                        p1.x + ring[o0] * w1, p1.y + ring[o0 + 1] * w1, p1.z + ring[o0 + 2] * w1,
                        p2.x + ring[o0] * w2, p2.y + ring[o0 + 1] * w2, p2.z + ring[o0 + 2] * w2,
                        p2.x + ring[o1] * w2, p2.y + ring[o1 + 1] * w2, p2.z + ring[o1 + 2] * w2,
                        p1.x + ring[o1] * w1, p1.y + ring[o1 + 1] * w1, p1.z + ring[o1 + 2] * w1,
                        layer.r(), layer.g(), layer.b(), a1, a2, a2, a1, u1, u2, 0f, 1f,
                        (float) nx, (float) ny, (float) nz);
            }
        }
    }

    /** Any unit vector perpendicular to {@code dir}, chosen from the axis it is least aligned with. */
    private static Vec3 orthogonalTo(Vec3 dir) {
        Vec3 reference = Math.abs(dir.y) < 0.9 ? UP : EAST;
        Vec3 perp = dir.cross(reference);
        if (perp.lengthSqr() < 1e-8) {
            perp = dir.cross(SOUTH);
        }
        return perp.normalize();
    }

    /** Rodrigues rotation of {@code v} by the minimal rotation carrying {@code from} onto {@code to}. */
    private static Vec3 transport(Vec3 v, Vec3 from, Vec3 to) {
        Vec3 axis = from.cross(to);
        double sin = axis.length();
        if (sin < 1e-7) {
            return v;
        }
        axis = axis.scale(1.0 / sin);
        double cos = Mth.clamp(from.dot(to), -1.0, 1.0);
        double angle = Math.atan2(sin, cos);
        double ca = Math.cos(angle);
        double sa = Math.sin(angle);
        return v.scale(ca)
                .add(axis.cross(v).scale(sa))
                .add(axis.scale(axis.dot(v) * (1.0 - ca)));
    }

    /** Re-projects a transported frame vector back onto the plane normal to the tangent. */
    private static Vec3 orthonormalize(Vec3 v, Vec3 tangent) {
        Vec3 projected = v.subtract(tangent.scale(v.dot(tangent)));
        if (projected.lengthSqr() < 1e-8) {
            return orthogonalTo(tangent);
        }
        return projected.normalize();
    }

    /**
     * How much of a point's brightness survives its distance from the camera. Zero inside
     * {@link #NEAR_FADE_START}, full beyond {@link #NEAR_FADE_END}, eased between.
     */
    private static float nearFade(Vec3 camWorldPos, Vec3 point) {
        double dist = camWorldPos.distanceTo(point);
        if (dist <= NEAR_FADE_START) {
            return 0f;
        }
        if (dist >= NEAR_FADE_END) {
            return 1f;
        }
        float t = (float) ((dist - NEAR_FADE_START) / (NEAR_FADE_END - NEAR_FADE_START));
        return t * t * (3f - 2f * t);
    }

    /** Width follows the proximity fade only partly, so the beam tapers toward the wand instead of pinching off. */
    private static float widthFade(float nearFade) {
        return NEAR_FADE_MIN_WIDTH + (1f - NEAR_FADE_MIN_WIDTH) * nearFade;
    }

    /** Ramps in over a fixed distance at the root; pinned arcs also ramp out, forks taper to nothing. */
    private static float endFade(float dist, float totalLen, boolean pinEnd) {
        float head = Mth.clamp(dist / ENDPOINT_FADE_BLOCKS, 0f, 1f);
        if (!pinEnd) {
            return head * Mth.clamp((totalLen - dist) / Math.max(0.05f, totalLen * 0.7f), 0f, 1f);
        }
        return head * Mth.clamp((totalLen - dist) / ENDPOINT_FADE_BLOCKS, 0f, 1f);
    }

    private static void renderGlowQuad(Matrix4f matrix, VertexConsumer consumer, Vec3 camWorldPos,
                                       Vec3 center, float radius,
                                       float r, float g, float b, float a) {
        Vec3 toCam = camWorldPos.subtract(center);
        if (toCam.lengthSqr() < 1e-8) {
            return;
        }
        toCam = toCam.normalize();

        Vec3 right = toCam.cross(UP);
        if (right.lengthSqr() < 1e-8) {
            right = EAST.cross(toCam);
        }
        right = right.normalize();
        Vec3 up = right.cross(toCam).normalize();

        Vec3 v0 = center.add(right.scale(-radius)).add(up.scale(radius));
        Vec3 v1 = center.add(right.scale(radius)).add(up.scale(radius));
        Vec3 v2 = center.add(right.scale(radius)).subtract(up.scale(radius));
        Vec3 v3 = center.add(right.scale(-radius)).subtract(up.scale(radius));

        quad(matrix, consumer, v0, v1, v2, v3, r, g, b, a, a, a, a, 0f, 1f, 0f, 1f,
                (float) toCam.x, (float) toCam.y, (float) toCam.z);
    }

    private static void quad(Matrix4f matrix, VertexConsumer consumer,
                             Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3,
                             float r, float g, float b,
                             float a0, float a1, float a2, float a3,
                             float uLo, float uHi, float vLo, float vHi,
                             float nx, float ny, float nz) {
        quadRaw(matrix, consumer, v0.x, v0.y, v0.z, v1.x, v1.y, v1.z,
                v2.x, v2.y, v2.z, v3.x, v3.y, v3.z,
                r, g, b, a0, a1, a2, a3, uLo, uHi, vLo, vHi, nx, ny, nz);
    }

    /** Corner order is (uLo,vLo) → (uHi,vLo) → (uHi,vHi) → (uLo,vHi). */
    private static void quadRaw(Matrix4f matrix, VertexConsumer consumer,
                                double x0, double y0, double z0,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                double x3, double y3, double z3,
                                float r, float g, float b,
                                float a0, float a1, float a2, float a3,
                                float uLo, float uHi, float vLo, float vHi,
                                float nx, float ny, float nz) {
        vertex(matrix, consumer, x0, y0, z0, r, g, b, a0, uLo, vLo, nx, ny, nz);
        vertex(matrix, consumer, x1, y1, z1, r, g, b, a1, uHi, vLo, nx, ny, nz);
        vertex(matrix, consumer, x2, y2, z2, r, g, b, a2, uHi, vHi, nx, ny, nz);
        vertex(matrix, consumer, x3, y3, z3, r, g, b, a3, uLo, vHi, nx, ny, nz);
    }

    private static void vertex(Matrix4f matrix, VertexConsumer c,
                               double x, double y, double z, float r, float g, float b, float a,
                               float u, float v, float nx, float ny, float nz) {
        c.addVertex(matrix, (float) x, (float) y, (float) z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(nx, ny, nz);
    }
}
