package at.koopro.wizardsandbeasts.spell.learning.tracing;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Turns a seed and a difficulty into a fair path.
 *
 * <p>Deterministic: the same {@code (seed, difficulty)} always gives the same path, so a failed challenge
 * can be reproduced from its seed in a test or a bug report. Variation comes from the template, the
 * template's own jitter, one of eight symmetries (four rotations, mirrored or not) and the direction of
 * travel.
 *
 * <p>Every candidate goes through {@link TracePathValidator}; a rejected one is redrawn with the next
 * sub-seed. If every draw fails, the generator returns {@link #fallback}, which the test suite proves fair
 * at every difficulty, so the player is never handed an impossible path and the server never fails to
 * produce one.
 */
@NullMarked
public final class TracePathGenerator {

    /** Spacing of the resampled centre line, in canvas units. */
    public static final double SPACING = 0.01;
    public static final int MAX_ATTEMPTS = 32;

    private TracePathGenerator() {
    }

    public static TracePath generate(long seed, TraceDifficulty difficulty) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Random r = new Random(seed * 31L + attempt);
            TraceTemplate template = difficulty.templates().get(r.nextInt(difficulty.templates().size()));
            TracePath candidate = build(template.raw(r), r.nextInt(4), r.nextBoolean(), r.nextBoolean(), difficulty);
            if (TracePathValidator.isFair(candidate, difficulty)) {
                return candidate;
            }
        }
        return fallback(difficulty);
    }

    /**
     * A serpentine — straight rows joined by half-circle turns — sized to the middle of the difficulty's
     * length band. Fair by construction: rows sit four half-widths apart, more than the corridor's width
     * plus the validator's gap, and a test pins it at every difficulty.
     */
    public static TracePath fallback(TraceDifficulty difficulty) {
        double hw = difficulty.halfWidth();
        double target = (difficulty.minLength() + difficulty.maxLength()) / 2.0;
        double edge = hw * TracePathValidator.MARKER_RADIUS + TracePathValidator.CANVAS_MARGIN + 0.01;
        double usable = 1.0 - 2.0 * edge;
        double rowGap = 4.0 * hw;
        double turn = Math.PI * rowGap / 2.0;
        int rows = 1;
        double rowLength = target;
        for (int k = 1; k <= 8; k++) {
            double w = (target - (k - 1) * turn) / k;
            // The half-circle turns bulge rowGap/2 past the row ends, so a row plus a bulge must fit.
            if (w + rowGap <= usable && (k - 1) * rowGap <= usable) {
                rows = k;
                rowLength = Math.max(0.2, w);
                break;
            }
        }
        double top = 0.5 - (rows - 1) * rowGap / 2.0;
        double left = 0.5 - rowLength / 2.0;
        List<double[]> pts = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            double y = top + row * rowGap;
            boolean rightward = row % 2 == 0;
            double from = rightward ? left : left + rowLength;
            double to = rightward ? left + rowLength : left;
            pts.add(new double[] {from, y});
            pts.add(new double[] {to, y});
            if (row < rows - 1) {
                // Half circle from this row's end down to the next row, bulging outward.
                double cy = y + rowGap / 2.0;
                for (int i = 1; i < 16; i++) {
                    double a = -Math.PI / 2 + Math.PI * i / 16.0;
                    double dx = Math.cos(a) * rowGap / 2.0;
                    pts.add(new double[] {rightward ? to + dx : to - dx, cy + Math.sin(a) * rowGap / 2.0});
                }
            }
        }
        return resample(pts);
    }

    static TracePath build(List<double[]> raw, int quarterTurns, boolean mirror, boolean reverse,
                           TraceDifficulty difficulty) {
        List<double[]> pts = new ArrayList<>(raw.size());
        for (double[] p : raw) {
            double x = mirror ? -p[0] : p[0];
            double y = p[1];
            for (int q = 0; q < quarterTurns; q++) {
                double nx = -y;
                y = x;
                x = nx;
            }
            pts.add(new double[] {x, y});
        }
        if (reverse) {
            java.util.Collections.reverse(pts);
        }
        return resample(fit(pts, difficulty));
    }

    /** Uniformly scales and centres the shape into the usable part of the canvas. */
    private static List<double[]> fit(List<double[]> pts, TraceDifficulty difficulty) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double[] p : pts) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        double edge = difficulty.halfWidth() * TracePathValidator.MARKER_RADIUS + TracePathValidator.CANVAS_MARGIN + 0.005;
        double usable = 1.0 - 2.0 * edge;
        double span = Math.max(maxX - minX, maxY - minY);
        double scale = span <= 0.0 ? 1.0 : usable / span;
        double offX = 0.5 - (minX + maxX) / 2.0 * scale;
        double offY = 0.5 - (minY + maxY) / 2.0 * scale;
        List<double[]> out = new ArrayList<>(pts.size());
        for (double[] p : pts) {
            out.add(new double[] {p[0] * scale + offX, p[1] * scale + offY});
        }
        return out;
    }

    /** Evenly spaced points along the polyline, corners preserved. */
    static TracePath resample(List<double[]> pts) {
        List<double[]> out = new ArrayList<>();
        out.add(pts.get(0));
        double carry = 0.0;
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1);
            double[] b = pts.get(i);
            double seg = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (seg <= 0.0) {
                continue;
            }
            double s = SPACING - carry;
            while (s < seg) {
                double t = s / seg;
                out.add(new double[] {a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])});
                s += SPACING;
            }
            carry = seg - (s - SPACING);
            // Keep every corner exactly, so a sharp turn stays sharp after resampling.
            double[] last = out.get(out.size() - 1);
            if (i < pts.size() - 1 && (last[0] != b[0] || last[1] != b[1]) && isCorner(pts, i)) {
                out.add(b);
                carry = 0.0;
            }
        }
        double[] end = pts.get(pts.size() - 1);
        double[] last = out.get(out.size() - 1);
        if (last[0] != end[0] || last[1] != end[1]) {
            out.add(end);
        }
        double[] xs = new double[out.size()];
        double[] ys = new double[out.size()];
        for (int i = 0; i < out.size(); i++) {
            xs[i] = out.get(i)[0];
            ys[i] = out.get(i)[1];
        }
        return new TracePath(xs, ys);
    }

    private static boolean isCorner(List<double[]> pts, int i) {
        double[] a = pts.get(i - 1);
        double[] b = pts.get(i);
        double[] c = pts.get(i + 1);
        double ux = b[0] - a[0];
        double uy = b[1] - a[1];
        double vx = c[0] - b[0];
        double vy = c[1] - b[1];
        double cross = Math.abs(ux * vy - uy * vx);
        double dot = ux * vx + uy * vy;
        return Math.atan2(cross, dot) > Math.toRadians(20);
    }
}
