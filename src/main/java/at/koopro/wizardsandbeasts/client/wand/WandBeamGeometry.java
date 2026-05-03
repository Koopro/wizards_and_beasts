package at.koopro.wizardsandbeasts.client.wand;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class WandBeamGeometry {

    private WandBeamGeometry() {
    }

    static Vec3[] buildBeamPath(Vec3 start, Vec3 end, long seed, float noiseAmp) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.08) {
            return null;
        }

        Vec3 dir = delta.scale(1.0 / length);

        Vec3 up = new Vec3(0, 1, 0);
        Vec3 perp1 = dir.cross(up);
        if (perp1.lengthSqr() < 1e-8) {
            perp1 = dir.cross(new Vec3(1, 0, 0));
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

        float lateralScale = (float) length * noiseAmp * 0.014f;

        Vec3[] points = new Vec3[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            Vec3 base = start.add(delta.scale(t));
            points[i] = base.add(perp1.scale(ox[i] * lateralScale)).add(perp2.scale(oy[i] * lateralScale));
        }
        return points;
    }

    static void renderLayer(Matrix4f matrix, VertexConsumer consumer, Vec3[] path,
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
        for (int i = 1; i < n; i++) {
            cx += random.nextInt(11) - 5;
            cy += random.nextInt(11) - 5;
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
