package at.koopro.wizardsandbeasts.particle;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The shapes a {@code SpellVfxShapeS2CPayload} lays out must land where the server loops they replaced put each
 * particle (2026-11-15), or a Protego ring, a cone line and a Patronus aura would quietly change shape.
 */
class ShapeLayoutTest {

    private static final double EPS = 1.0e-9;

    private static List<Vec3> collect(java.util.function.Consumer<ShapeLayout.PointSink> run) {
        List<Vec3> out = new ArrayList<>();
        run.accept((x, y, z) -> out.add(new Vec3(x, y, z)));
        return out;
    }

    private static void assertNear(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPS, "x");
        assertEquals(expected.y, actual.y, EPS, "y");
        assertEquals(expected.z, actual.z, EPS, "z");
    }

    @Test
    void lineMatchesTheOldStepLoop() {
        Vec3 from = new Vec3(1, 2, 3);
        Vec3 to = new Vec3(7, 4, -5);
        double distance = from.distanceTo(to);
        int steps = Math.max(8, (int) (distance * 10));
        List<Vec3> points = collect(sink -> ShapeLayout.line(from, to, steps + 1, sink));
        assertEquals(steps + 1, points.size());
        for (int i = 0; i <= steps; i++) {
            assertNear(from.lerp(to, i / (double) steps), points.get(i));
        }
    }

    @Test
    void horizontalRingMatchesTheOldProtegoRing() {
        Vec3 centre = new Vec3(10, 64.1, -3);
        double radius = 2.5;
        int n = 16;
        List<Vec3> points = collect(sink -> ShapeLayout.ring(centre, new Vec3(0, 1, 0), radius, n, 0.0, sink));
        assertEquals(n, points.size());
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2.0 / n;
            assertNear(new Vec3(centre.x + Math.cos(a) * radius, centre.y, centre.z + Math.sin(a) * radius),
                    points.get(i));
        }
    }

    @Test
    void ringStartsAtItsPhase() {
        List<Vec3> points = collect(sink -> ShapeLayout.ring(Vec3.ZERO, new Vec3(0, 1, 0), 1.0, 8, 0.3, sink));
        assertNear(new Vec3(Math.cos(0.3), 0, Math.sin(0.3)), points.get(0));
    }

    @Test
    void tiltedRingLiesInThePlaneOfItsNormal() {
        Vec3 centre = new Vec3(3, 70, 3);
        Vec3 normal = new Vec3(1, 0.4, -0.7).normalize();
        double radius = 0.65;
        for (Vec3 p : collect(sink -> ShapeLayout.ring(centre, normal, radius, 16, 0.0, sink))) {
            Vec3 d = p.subtract(centre);
            assertEquals(0.0, d.dot(normal), 1.0e-9, "point off the ward's surface plane");
            assertEquals(radius, d.length(), 1.0e-9, "point off the ring");
        }
    }

    @Test
    void shellMatchesTheOldProtegoShell() {
        Vec3 centre = new Vec3(0, 65, 0);
        double radius = 3.0;
        int n = 22;
        double coverage = 1.2;
        List<Vec3> points = collect(sink -> ShapeLayout.shell(centre, radius, n, coverage, sink));
        assertEquals(n, points.size());
        for (int i = 0; i < n; i++) {
            double y = 1.0 - (i / (double) (n - 1)) * coverage;
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = i * 2.399963;
            assertNear(centre.add(Math.cos(theta) * r * radius, y * radius, Math.sin(theta) * r * radius),
                    points.get(i));
        }
    }

    @Test
    void nothingAskedNothingLaidOut() {
        assertEquals(0, collect(sink -> ShapeLayout.line(Vec3.ZERO, new Vec3(1, 0, 0), 0, sink)).size());
        assertEquals(0, collect(sink -> ShapeLayout.ring(Vec3.ZERO, new Vec3(0, 1, 0), 1, 0, 0, sink)).size());
        assertEquals(0, collect(sink -> ShapeLayout.shell(Vec3.ZERO, 1, 0, 1, sink)).size());
    }
}
