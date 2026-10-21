package at.koopro.wizardsandbeasts.spell.learning.tracing;

import org.jspecify.annotations.NullMarked;

import java.util.Arrays;

/**
 * The centre line of a tracing corridor: an evenly resampled polyline in the unit square.
 *
 * <p>Immutable. The first point is the start mark and the last is the goal. {@link #nearest} is the one
 * geometric question the judge asks, and it is answered the same way on both sides of the network: the
 * client uses it to show a slip the moment it happens, and the server uses it to decide.
 */
@NullMarked
public final class TracePath {

    private final double[] xs;
    private final double[] ys;
    /** {@code cum[i]} = arc length from the start to point {@code i}. */
    private final double[] cum;

    public TracePath(double[] xs, double[] ys) {
        if (xs.length != ys.length || xs.length < 2) {
            throw new IllegalArgumentException("a path needs at least two points");
        }
        this.xs = xs.clone();
        this.ys = ys.clone();
        this.cum = new double[xs.length];
        for (int i = 1; i < xs.length; i++) {
            cum[i] = cum[i - 1] + Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
        }
    }

    public int size() {
        return xs.length;
    }

    public double x(int i) {
        return xs[i];
    }

    public double y(int i) {
        return ys[i];
    }

    /** Arc length from the start to point {@code i}. */
    public double arcAt(int i) {
        return cum[i];
    }

    public double length() {
        return cum[cum.length - 1];
    }

    public double startX() {
        return xs[0];
    }

    public double startY() {
        return ys[0];
    }

    public double goalX() {
        return xs[xs.length - 1];
    }

    public double goalY() {
        return ys[ys.length - 1];
    }

    /**
     * Distance from {@code (px, py)} to the polyline and the arc position of the closest point on it.
     * Linear in the number of segments; a path has a few hundred, and the judge asks a few thousand times
     * per submission.
     */
    public Projection nearest(double px, double py) {
        double best = Double.MAX_VALUE;
        double bestArc = 0.0;
        for (int i = 0; i < xs.length - 1; i++) {
            double ax = xs[i];
            double ay = ys[i];
            double dx = xs[i + 1] - ax;
            double dy = ys[i + 1] - ay;
            double lenSq = dx * dx + dy * dy;
            double t = lenSq <= 0.0 ? 0.0 : ((px - ax) * dx + (py - ay) * dy) / lenSq;
            t = Math.max(0.0, Math.min(1.0, t));
            double cx = ax + t * dx;
            double cy = ay + t * dy;
            double d = Math.hypot(px - cx, py - cy);
            if (d < best) {
                best = d;
                bestArc = cum[i] + t * (cum[i + 1] - cum[i]);
            }
        }
        return new Projection(best, bestArc);
    }

    /** The point at arc position {@code s}, clamped to the path. For drawing progress. */
    public double[] pointAt(double s) {
        if (s <= 0.0) {
            return new double[] {xs[0], ys[0]};
        }
        int i = Arrays.binarySearch(cum, s);
        if (i >= 0) {
            return new double[] {xs[i], ys[i]};
        }
        int hi = -i - 1;
        if (hi >= xs.length) {
            return new double[] {goalX(), goalY()};
        }
        int lo = hi - 1;
        double span = cum[hi] - cum[lo];
        double t = span <= 0.0 ? 0.0 : (s - cum[lo]) / span;
        return new double[] {xs[lo] + t * (xs[hi] - xs[lo]), ys[lo] + t * (ys[hi] - ys[lo])};
    }

    /** Closest point on the path: how far away, and how far along. */
    public record Projection(double distance, double arc) {
    }
}
