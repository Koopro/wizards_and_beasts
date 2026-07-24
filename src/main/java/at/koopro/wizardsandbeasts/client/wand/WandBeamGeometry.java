package at.koopro.wizardsandbeasts.client.wand;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jspecify.annotations.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class WandBeamGeometry {

    /** Fullbright packed light for unlit beam strips. */
    private static final int FULL_BRIGHT = 0xF000F0;

    private WandBeamGeometry() {}

    /** Lateral wobble per unit of {@code noiseAmp}, in blocks. Absolute so the bolt does not scale with reach. */
    private static final float LATERAL_NOISE_BLOCKS = 0.06f;

    static Vec3[] buildBeamPath(Vec3 start, Vec3 end, long seed, float noiseAmp) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.08) {
            return null;
        }

        Vec3 dir = delta.scale(1.0 / length);

        // Pick the reference axis the beam direction is *least* aligned with, so the perpendicular basis
        // stays continuous instead of snapping through 90° as the aim passes vertical — that snap was a
        // large part of why the bolt appeared to thrash while looking up or down.
        Vec3 reference = Math.abs(dir.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 perp1 = dir.cross(reference);
        if (perp1.lengthSqr() < 1e-8) {
            perp1 = dir.cross(new Vec3(0, 0, 1));
        }
        perp1 = perp1.normalize();
        Vec3 perp2 = dir.cross(perp1).normalize();

        int segCount = Mth.clamp(
                (int) Math.ceil(length * BeamSettings.segmentsPerUnit / 4.0),
                BeamSettings.minPathSegments,
                BeamSettings.maxPathSegments);
        int n = segCount + 1;

        RandomSource random = RandomSource.create(seed);
        float[] ox = new float[n];
        float[] oy = new float[n];
        fillDetrendedWalk(random, n, ox, oy);

        // Fixed width in blocks, NOT proportional to the live beam length: the beam grows toward its target
        // and its length changes with every aim twitch, so a length-proportional amplitude made the whole
        // bolt breathe and swim frame to frame even though the random walk itself is seed-stable.
        float lateralScale = noiseAmp * LATERAL_NOISE_BLOCKS;

        Vec3[] points = new Vec3[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            Vec3 base = start.add(delta.scale(t));
            points[i] = base.add(perp1.scale(ox[i] * lateralScale)).add(perp2.scale(oy[i] * lateralScale));
        }
        return points;
    }

    /**
     * Either legacy lightning tube ({@code textured == false}) or twin billboard strips per segment.
     */
    static void renderLayers(Matrix4f matrix,
                             @Nullable VertexConsumer tubeConsumer,
                             @Nullable VertexConsumer stripConsumer,
                             Vec3 camWorldPos,
                             Vec3[] path,
                             float flicker,
                             float beamScrollU,
                             boolean textured) {
        float[] cumDist = cumulativeDistances(path);
        float totalLen = cumDist[path.length - 1];
        float uvScale = totalLen > 1e-4f ? (5.0f / totalLen) : 1.0f;

        for (int li = 0; li < BeamSettings.layers.length; li++) {
            BeamSettings.LayerSettings layer = BeamSettings.layers[li];
            if (textured && stripConsumer != null) {
                renderStripCrossLayers(matrix, stripConsumer, camWorldPos, path, layer, flicker, li,
                        beamScrollU, cumDist, uvScale);
            } else if (tubeConsumer != null) {
                renderTubeLayer(matrix, tubeConsumer, path, layer, flicker, li);
            }
        }
    }

    static void renderMuzzleFlash(Matrix4f matrix, VertexConsumer stripConsumer, Vec3 camWorldPos,
                                  Vec3 centerWorld, float radius, float r, float g, float b, float a,
                                  float scrollU) {
        renderBillboardQuad(matrix, stripConsumer, camWorldPos, centerWorld, radius, r, g, b, a, scrollU);
    }

    static void renderImpactFlash(Matrix4f matrix, VertexConsumer stripConsumer, Vec3 camWorldPos,
                                  Vec3 impactWorld, float radius, float r, float g, float b, float a,
                                  float scrollU, boolean entityHit) {
        float rad = entityHit ? radius * 1.35f : radius;
        float alpha = Mth.clamp(a * (entityHit ? 1.28f : 1.05f), 0f, 1f);
        renderBillboardQuad(matrix, stripConsumer, camWorldPos, impactWorld, rad, r, g, b, alpha,
                scrollU + 0.41f);
    }

    private static float[] cumulativeDistances(Vec3[] path) {
        float[] cum = new float[path.length];
        for (int i = 1; i < path.length; i++) {
            cum[i] = cum[i - 1] + (float) path[i].distanceTo(path[i - 1]);
        }
        return cum;
    }

    private static void renderStripCrossLayers(Matrix4f matrix, VertexConsumer consumer, Vec3 camWorldPos,
                                               Vec3[] path, BeamSettings.LayerSettings layer, float flicker,
                                               int layerIndex, float beamScrollU, float[] cumDist,
                                               float uvScale) {
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

            float t1 = i / (float) max;
            float t2 = (i + 1) / (float) max;
            float e1 = endpointFade(t1);
            float e2 = endpointFade(t2);

            float w1 = layer.width * e1;
            float w2 = layer.width * e2;
            float a1 = layer.alpha * e1 * flicker;
            float a2 = layer.alpha * e2 * flicker;

            float spread = 1f + layerIndex * 0.04f;
            w1 *= spread;
            w2 *= spread;

            float u1 = beamScrollU + cumDist[i] * uvScale;
            float u2 = beamScrollU + cumDist[i + 1] * uvScale;

            emitCrossBillboardSegment(matrix, consumer, camWorldPos, p1, p2, tangent, w1, w2,
                    layer.r, layer.g, layer.b, a1, a2, u1, u2);
        }
    }

    private static void emitCrossBillboardSegment(Matrix4f matrix, VertexConsumer consumer, Vec3 camWorldPos,
                                                  Vec3 p1, Vec3 p2, Vec3 tangent,
                                                  float w1, float w2,
                                                  float r, float g, float b, float a1, float a2,
                                                  float u1, float u2) {
        Vec3 mid = p1.add(p2).scale(0.5);
        Vec3 toCam = camWorldPos.subtract(mid);
        if (toCam.lengthSqr() < 1e-8) {
            toCam = tangent.cross(new Vec3(0, 1, 0));
        }
        toCam = toCam.normalize();

        Vec3 side = tangent.cross(toCam);
        if (side.lengthSqr() < 1e-8) {
            side = tangent.cross(new Vec3(0, 1, 0));
        }
        side = side.normalize();

        Vec3 ortho = side.cross(tangent).normalize();

        Vec3 n = toCam;
        float nx = (float) n.x;
        float ny = (float) n.y;
        float nz = (float) n.z;

        emitBillboardQuadBetween(matrix, consumer, p1, p2, side, w1, w2, r, g, b, a1, a2, u1, u2, nx, ny, nz);
        emitBillboardQuadBetween(matrix, consumer, p1, p2, ortho, w1, w2, r, g, b, a1, a2, u1, u2, nx, ny, nz);
    }

    private static void emitBillboardQuadBetween(Matrix4f matrix, VertexConsumer consumer,
                                                 Vec3 p1, Vec3 p2, Vec3 axis,
                                                 float w1, float w2,
                                                 float r, float g, float blue, float a1, float a2,
                                                 float u1, float u2,
                                                 float nx, float ny, float nz) {
        Vec3 off1 = axis.scale(w1);
        Vec3 off2 = axis.scale(w2);

        Vec3 v0 = p1.add(off1);
        Vec3 v1 = p1.subtract(off1);
        Vec3 v2 = p2.subtract(off2);
        Vec3 v3 = p2.add(off2);

        quadTextured(matrix, consumer, v0, v1, v2, v3, r, g, blue, a1, a1, a2, a2, u1, u2, nx, ny, nz);
    }

    private static void renderBillboardQuad(Matrix4f matrix, VertexConsumer consumer, Vec3 camWorldPos,
                                            Vec3 center, float radius,
                                            float r, float g, float b, float a, float scrollU) {
        Vec3 toCam = camWorldPos.subtract(center);
        if (toCam.lengthSqr() < 1e-8) {
            return;
        }
        toCam = toCam.normalize();

        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = toCam.cross(worldUp);
        if (right.lengthSqr() < 1e-8) {
            right = new Vec3(1, 0, 0).cross(toCam);
        }
        right = right.normalize();
        Vec3 up = right.cross(toCam).normalize();

        Vec3 v0 = center.add(right.scale(-radius)).add(up.scale(radius));
        Vec3 v1 = center.add(right.scale(radius)).add(up.scale(radius));
        Vec3 v2 = center.add(right.scale(radius)).subtract(up.scale(radius));
        Vec3 v3 = center.add(right.scale(-radius)).subtract(up.scale(radius));

        float nx = (float) toCam.x;
        float ny = (float) toCam.y;
        float nz = (float) toCam.z;

        float u0 = scrollU;
        float u1 = scrollU + 1.0f;
        quadTextured(matrix, consumer, v0, v1, v2, v3, r, g, b, a, a, a, a, u0, u1, nx, ny, nz);
    }

    private static void quadTextured(Matrix4f matrix, VertexConsumer consumer,
                                       Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3,
                                       float r, float g, float b,
                                       float a0, float a1, float a2, float a3,
                                       float uLo, float uHi,
                                       float nx, float ny, float nz) {
        vertex(matrix, consumer, v0, r, g, b, a0, uLo, 0f, nx, ny, nz);
        vertex(matrix, consumer, v1, r, g, b, a1, uHi, 0f, nx, ny, nz);
        vertex(matrix, consumer, v2, r, g, b, a2, uHi, 1f, nx, ny, nz);
        vertex(matrix, consumer, v3, r, g, b, a3, uLo, 1f, nx, ny, nz);
    }

    private static void vertex(Matrix4f matrix, VertexConsumer c,
                               Vec3 p, float r, float g, float b, float a,
                               float u, float v, float nx, float ny, float nz) {
        c.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(nx, ny, nz);
    }

    private static void renderTubeLayer(Matrix4f matrix, VertexConsumer consumer, Vec3[] path,
                                        BeamSettings.LayerSettings layer, float flicker, int layerIndex) {
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

            Vec3 u = tangent.cross(new Vec3(0, 1, 0));
            if (u.lengthSqr() < 1e-8) {
                u = tangent.cross(new Vec3(1, 0, 0));
            }
            u = u.normalize();
            Vec3 v = tangent.cross(u).normalize();

            float t1 = i / (float) max;
            float t2 = (i + 1) / (float) max;
            float e1 = endpointFade(t1);
            float e2 = endpointFade(t2);

            float w1 = layer.width * e1;
            float w2 = layer.width * e2;
            float a1 = layer.alpha * e1 * flicker;
            float a2 = layer.alpha * e2 * flicker;

            float spread = 1f + layerIndex * 0.04f;
            w1 *= spread;
            w2 *= spread;

            emitTubeSegment(matrix, consumer, p1, p2, u, v, w1, w2,
                    layer.r, layer.g, layer.b, a1, a2);
        }
    }

    private static void fillDetrendedWalk(RandomSource random, int n, float[] outX, float[] outY) {
        outX[0] = 0f;
        outY[0] = 0f;
        float cx = 0f;
        float cy = 0f;
        // Gentler per-step deltas (±2 vs the old ±5): the wide step made the bolt a high-frequency zig-zag
        // that read as jagged lightning rather than a clean magical beam.
        for (int i = 1; i < n; i++) {
            cx += random.nextInt(5) - 2;
            cy += random.nextInt(5) - 2;
            outX[i] = cx;
            outY[i] = cy;
        }
        float endX = outX[n - 1];
        float endY = outY[n - 1];
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            outX[i] -= t * endX;
            outY[i] -= t * endY;
        }
    }

    private static float endpointFade(float t) {
        if (t < 0.06f) {
            return t / 0.06f;
        }
        if (t > 0.94f) {
            return (1f - t) / 0.06f;
        }
        return 1f;
    }

    private static void emitTubeSegment(Matrix4f matrix, VertexConsumer consumer,
                                        Vec3 p1, Vec3 p2, Vec3 u, Vec3 v,
                                        float w1, float w2,
                                        float r, float g, float b, float a1, float a2) {
        Vec3 c0 = p1.add(u.scale(w1)).add(v.scale(w1));
        Vec3 c1 = p1.add(u.scale(w1)).subtract(v.scale(w1));
        Vec3 c2 = p1.subtract(u.scale(w1)).subtract(v.scale(w1));
        Vec3 c3 = p1.subtract(u.scale(w1)).add(v.scale(w1));

        Vec3 d0 = p2.add(u.scale(w2)).add(v.scale(w2));
        Vec3 d1 = p2.add(u.scale(w2)).subtract(v.scale(w2));
        Vec3 d2 = p2.subtract(u.scale(w2)).subtract(v.scale(w2));
        Vec3 d3 = p2.subtract(u.scale(w2)).add(v.scale(w2));

        quad(matrix, consumer, c0, c1, d1, d0, r, g, b, a1, a1, a2, a2);
        quad(matrix, consumer, c1, c2, d2, d1, r, g, b, a1, a1, a2, a2);
        quad(matrix, consumer, c2, c3, d3, d2, r, g, b, a1, a1, a2, a2);
        quad(matrix, consumer, c3, c0, d0, d3, r, g, b, a1, a1, a2, a2);
    }

    private static void quad(Matrix4f matrix, VertexConsumer consumer,
                             Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3,
                             float r, float g, float b,
                             float a0, float a1, float a2, float a3) {
        consumer.addVertex(matrix, (float) v0.x, (float) v0.y, (float) v0.z).setColor(r, g, b, a0);
        consumer.addVertex(matrix, (float) v1.x, (float) v1.y, (float) v1.z).setColor(r, g, b, a1);
        consumer.addVertex(matrix, (float) v2.x, (float) v2.y, (float) v2.z).setColor(r, g, b, a2);
        consumer.addVertex(matrix, (float) v3.x, (float) v3.y, (float) v3.z).setColor(r, g, b, a3);
    }
}
