package at.koopro.wizardsandbeasts.particle;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

/**
 * Where the points of a particle line, ring or shell go. Pure geometry, shared by the client that lays out a
 * {@code SpellVfxShapeS2CPayload} and by the tests; nothing here spawns anything.
 *
 * <p>The formulas are the ones the server loops used before the shapes became one packet each (ProtegoFeedback,
 * SpellParticles, the Patronus aura), so a shape at full budget looks as it did.
 */
@NullMarked
public final class ShapeLayout {

    /** Receives one point. */
    @FunctionalInterface
    public interface PointSink {
        void accept(double x, double y, double z);
    }

    private ShapeLayout() {}

    /** {@code points} spaced evenly from {@code a} to {@code b}, both ends included. */
    public static void line(Vec3 a, Vec3 b, int points, PointSink sink) {
        if (points <= 0) {
            return;
        }
        if (points == 1) {
            sink.accept((a.x + b.x) * 0.5, (a.y + b.y) * 0.5, (a.z + b.z) * 0.5);
            return;
        }
        for (int i = 0; i < points; i++) {
            double t = i / (double) (points - 1);
            sink.accept(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
        }
    }

    /**
     * {@code points} round {@code centre} at {@code radius}, in the plane whose normal is {@code normal}, starting at
     * angle {@code phase}. A vertical normal lays the ring on the world's x/z axes, as the horizontal rings did.
     */
    public static void ring(Vec3 centre, Vec3 normal, double radius, int points, double phase, PointSink sink) {
        if (points <= 0) {
            return;
        }
        double ux;
        double uy;
        double uz;
        double vx;
        double vy;
        double vz;
        Vec3 n = normal.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : normal.normalize();
        if (Math.abs(n.y) > 0.99) {
            ux = 1;
            uy = 0;
            uz = 0;
            vx = 0;
            vy = 0;
            vz = 1;
        } else {
            Vec3 u = n.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 v = n.cross(u).normalize();
            ux = u.x;
            uy = u.y;
            uz = u.z;
            vx = v.x;
            vy = v.y;
            vz = v.z;
        }
        for (int i = 0; i < points; i++) {
            double ang = phase + i * Math.PI * 2.0 / points;
            double c = Math.cos(ang) * radius;
            double s = Math.sin(ang) * radius;
            sink.accept(centre.x + ux * c + vx * s, centre.y + uy * c + vy * s, centre.z + uz * c + vz * s);
        }
    }

    /**
     * {@code points} on a golden-angle spiral over a sphere, from the top down to {@code y = 1 − coverage}
     * (1 = the upper half, 2 = the whole sphere), as {@code ProtegoFeedback.shell} laid out a ward's surface.
     */
    public static void shell(Vec3 centre, double radius, int points, double coverage, PointSink sink) {
        if (points <= 0) {
            return;
        }
        for (int i = 0; i < points; i++) {
            double y = 1.0 - (i / (double) Math.max(1, points - 1)) * coverage;
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = i * 2.399963;
            sink.accept(centre.x + Math.cos(theta) * r * radius, centre.y + y * radius,
                    centre.z + Math.sin(theta) * r * radius);
        }
    }
}
