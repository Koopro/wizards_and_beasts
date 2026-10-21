package at.koopro.wizardsandbeasts.spell.learning.tracing;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The shapes a tracing path is drawn from.
 *
 * <p>Each template produces a raw polyline in roughly unit space from a seeded {@link Random}. The jitter
 * is real: two Lumos books never ask for the same stroke. {@link TracePathGenerator} then rotates, mirrors,
 * reverses, fits and resamples the shape, and {@link TracePathValidator} decides whether the result is fair.
 * A template therefore only has to be <em>usually</em> fair, never always fair; a rejected draw is
 * redrawn with the next seed.
 *
 * <p>Angular templates (zigzag, rune) keep their corners sharp, because a sharp corner is what the
 * gesture is about. Their interior angles are kept at 75° or more so the corridor never folds onto itself
 * at a corner.
 */
@NullMarked
public enum TraceTemplate {
    /** One shallow curve. */
    ARC {
        @Override
        List<double[]> raw(Random r) {
            double bow = 0.30 + 0.20 * r.nextDouble();
            double apexX = 0.40 + 0.20 * r.nextDouble();
            double tilt = (r.nextDouble() - 0.5) * 0.3;
            List<double[]> pts = new ArrayList<>();
            for (int i = 0; i <= 64; i++) {
                double t = i / 64.0;
                // Quadratic Bezier start → apex control → end.
                double cx = apexX;
                double cy = 0.5 - 2 * bow;
                double x = sq(1 - t) * 0.0 + 2 * (1 - t) * t * cx + sq(t) * 1.0;
                double y = sq(1 - t) * 0.5 + 2 * (1 - t) * t * cy + sq(t) * (0.5 + tilt);
                pts.add(new double[] {x, y});
            }
            return pts;
        }
    },
    /** Swish and flick: a long curve, then one sharp stroke. */
    SWISH {
        @Override
        List<double[]> raw(Random r) {
            double dip = 0.35 + 0.15 * r.nextDouble();
            double flickRise = 0.35 + 0.15 * r.nextDouble();
            List<double[]> pts = new ArrayList<>();
            for (int i = 0; i <= 48; i++) {
                double t = i / 48.0;
                double x = 0.7 * t;
                double y = 0.2 + dip * Math.sin(Math.PI * t * 0.85);
                pts.add(new double[] {x, y});
            }
            double[] last = pts.get(pts.size() - 1);
            pts.add(new double[] {last[0] + 0.30, last[1] - flickRise});
            return pts;
        }
    },
    WAVE_1 {
        @Override
        List<double[]> raw(Random r) {
            return wave(r, 1);
        }
    },
    WAVE_2 {
        @Override
        List<double[]> raw(Random r) {
            return wave(r, 2);
        }
    },
    WAVE_3 {
        @Override
        List<double[]> raw(Random r) {
            return wave(r, 3);
        }
    },
    WAVE_4 {
        @Override
        List<double[]> raw(Random r) {
            return wave(r, 4);
        }
    },
    ZIGZAG_2 {
        @Override
        List<double[]> raw(Random r) {
            return zigzag(r, 2);
        }
    },
    ZIGZAG_3 {
        @Override
        List<double[]> raw(Random r) {
            return zigzag(r, 3);
        }
    },
    ZIGZAG_4 {
        @Override
        List<double[]> raw(Random r) {
            return zigzag(r, 4);
        }
    },
    ZIGZAG_5 {
        @Override
        List<double[]> raw(Random r) {
            return zigzag(r, 5);
        }
    },
    RUNE_4 {
        @Override
        List<double[]> raw(Random r) {
            return rune(r, 4);
        }
    },
    RUNE_6 {
        @Override
        List<double[]> raw(Random r) {
            return rune(r, 6);
        }
    },
    RUNE_7 {
        @Override
        List<double[]> raw(Random r) {
            return rune(r, 7);
        }
    },
    RUNE_8 {
        @Override
        List<double[]> raw(Random r) {
            return rune(r, 8);
        }
    },
    /**
     * An Archimedean spiral of one and a half turns, traced inside-out or outside-in. The gap between rings
     * is a third of the outer radius or more, which after fitting stays well clear of the corridor width.
     */
    SPIRAL {
        @Override
        List<double[]> raw(Random r) {
            double r0 = 0.25 + 0.10 * r.nextDouble();
            double gap = 0.36 + 0.10 * r.nextDouble();
            double turns = 1.35 + 0.25 * r.nextDouble();
            double start = r.nextDouble() * Math.PI * 2;
            List<double[]> pts = new ArrayList<>();
            int steps = (int) (96 * turns);
            for (int i = 0; i <= steps; i++) {
                double theta = Math.PI * 2 * turns * i / steps;
                double radius = r0 + gap * theta / (Math.PI * 2);
                pts.add(new double[] {radius * Math.cos(theta + start), radius * Math.sin(theta + start)});
            }
            return pts;
        }
    },
    /** A straight lead-in that curls three quarters of the way round. */
    HOOK {
        @Override
        List<double[]> raw(Random r) {
            double lead = 0.45 + 0.25 * r.nextDouble();
            double radius = 0.22 + 0.08 * r.nextDouble();
            double sweep = Math.toRadians(220 + 50 * r.nextDouble());
            List<double[]> pts = new ArrayList<>();
            pts.add(new double[] {0.0, 0.0});
            pts.add(new double[] {lead, 0.0});
            // Curl to the left of the travel direction (+x): centre is above the end of the lead.
            double cx = lead;
            double cy = radius;
            int steps = 48;
            for (int i = 1; i <= steps; i++) {
                double a = -Math.PI / 2 + sweep * i / steps;
                pts.add(new double[] {cx + radius * Math.cos(a), cy + radius * Math.sin(a)});
            }
            return pts;
        }
    };

    /** The raw, un-fitted shape. */
    abstract List<double[]> raw(Random r);

    private static double sq(double v) {
        return v * v;
    }

    private static List<double[]> wave(Random r, int halfPeriods) {
        double amplitude = 0.17 + 0.07 * r.nextDouble();
        double phase = r.nextBoolean() ? 0.0 : Math.PI;
        double width = 0.30 * halfPeriods;
        List<double[]> pts = new ArrayList<>();
        int steps = 32 * halfPeriods;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            pts.add(new double[] {width * t, amplitude * Math.sin(Math.PI * halfPeriods * t + phase)});
        }
        return pts;
    }

    /**
     * {@code corners} sharp turns. Each tooth rises 100–130% of its run, which keeps the interior angle at
     * a corner between ~75° and 90°: sharp enough to be a gesture, open enough that the corridor never folds.
     */
    private static List<double[]> zigzag(Random r, int corners) {
        double run = 0.2;
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[] {0.0, 0.0});
        for (int i = 1; i <= corners + 1; i++) {
            double rise = run * (1.00 + 0.30 * r.nextDouble());
            double y = (i % 2 == 1) ? rise : 0.0;
            pts.add(new double[] {i * run, y});
        }
        return pts;
    }

    /**
     * A self-avoiding walk of {@code moves} single steps on a 4×4 grid: an angular "rune". Straight runs
     * of more than two steps are avoided so the shape is made of turns, not of one long line.
     */
    private static List<double[]> rune(Random r, int moves) {
        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        for (int attempt = 0; attempt < 64; attempt++) {
            boolean[][] seen = new boolean[4][4];
            int x = r.nextInt(4);
            int y = r.nextInt(4);
            seen[x][y] = true;
            List<double[]> pts = new ArrayList<>();
            pts.add(new double[] {x, y});
            int lastDir = -1;
            int straight = 0;
            boolean stuck = false;
            for (int m = 0; m < moves && !stuck; m++) {
                List<Integer> options = new ArrayList<>();
                for (int d = 0; d < 4; d++) {
                    int nx = x + dirs[d][0];
                    int ny = y + dirs[d][1];
                    if (nx < 0 || ny < 0 || nx > 3 || ny > 3 || seen[nx][ny]) {
                        continue;
                    }
                    if (d == lastDir && straight >= 1) {
                        continue;
                    }
                    options.add(d);
                }
                if (options.isEmpty()) {
                    stuck = true;
                    break;
                }
                int d = options.get(r.nextInt(options.size()));
                straight = d == lastDir ? straight + 1 : 0;
                lastDir = d;
                x += dirs[d][0];
                y += dirs[d][1];
                seen[x][y] = true;
                pts.add(new double[] {x, y});
            }
            if (!stuck) {
                return pts;
            }
        }
        // A 4×4 grid always admits a short walk; this is the shape the walk above degenerates to.
        List<double[]> fallback = new ArrayList<>();
        fallback.add(new double[] {0, 0});
        fallback.add(new double[] {1, 0});
        fallback.add(new double[] {1, 1});
        fallback.add(new double[] {2, 1});
        fallback.add(new double[] {2, 2});
        return fallback;
    }
}
