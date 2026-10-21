package at.koopro.wizardsandbeasts.spell.learning.tracing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The judge: a faithful trace passes, and each way of not tracing the path is named for what it was.
 */
class TraceJudgeTest {

    /** Follows the centre line at the given step, offset sideways by {@code offset} half-widths. */
    static double[] trace(TracePath path, double step, double offsetHalfWidths, double halfWidth) {
        List<Double> out = new ArrayList<>();
        for (double s = 0; s <= path.length(); s += step) {
            add(out, path, s, offsetHalfWidths * halfWidth);
        }
        add(out, path, path.length(), 0.0);
        double[] xy = new double[out.size()];
        for (int i = 0; i < xy.length; i++) {
            xy[i] = out.get(i);
        }
        return xy;
    }

    private static void add(List<Double> out, TracePath path, double s, double offset) {
        double[] p = path.pointAt(s);
        double[] q = path.pointAt(Math.min(path.length(), s + 0.005));
        double[] o = path.pointAt(Math.max(0, s - 0.005));
        double tx = q[0] - o[0];
        double ty = q[1] - o[1];
        double len = Math.hypot(tx, ty);
        double nx = len == 0 ? 0 : -ty / len;
        double ny = len == 0 ? 0 : tx / len;
        out.add(p[0] + nx * offset);
        out.add(p[1] + ny * offset);
    }

    @Test
    void faithfulTracePassesAtEveryDifficulty() {
        for (TraceDifficulty d : TraceDifficulty.values()) {
            for (long seed = 0; seed < 300; seed++) {
                TracePath path = TracePathGenerator.generate(seed, d);
                TraceProgress.Verdict v = TraceJudge.judge(path, d, false, trace(path, 0.004, 0.0, d.halfWidth()));
                long s = seed;
                assertEquals(TraceOutcome.PASSED, v.outcome(), () -> d + " seed " + s + " failed a perfect trace");
                assertTrue(v.accuracy() > 0.95);
            }
        }
    }

    @Test
    void sparseSamplesStillPassBecauseMotionIsDensified() {
        TraceDifficulty d = TraceDifficulty.INTERMEDIATE;
        TracePath path = TracePathGenerator.generate(7, d);
        // One sample every half half-width: a fast but faithful hand.
        TraceProgress.Verdict v = TraceJudge.judge(path, d, false, trace(path, d.halfWidth() / 2, 0.0, d.halfWidth()));
        assertEquals(TraceOutcome.PASSED, v.outcome());
    }

    @Test
    void wanderingInsideTheCorridorIsInaccurate() {
        TraceDifficulty d = TraceDifficulty.ADVANCED;
        TracePath path = TracePathGenerator.fallback(d); // serpentine: offsets stay inside on both sides
        TraceProgress.Verdict v = TraceJudge.judge(path, d, false, trace(path, 0.004, 0.85, d.halfWidth()));
        assertEquals(TraceOutcome.INACCURATE, v.outcome());
    }

    @Test
    void crossingTheBoundaryFails() {
        TraceDifficulty d = TraceDifficulty.BASIC;
        TracePath path = TracePathGenerator.generate(3, d);
        double[] xy = trace(path, 0.004, 0.0, d.halfWidth());
        int mid = xy.length / 4 * 2;
        xy[mid] += d.halfWidth() * 3; // one sample well outside
        TraceProgress.Verdict v = TraceJudge.judge(path, d, false, xy);
        assertEquals(TraceOutcome.LEFT_PATH, v.outcome());
        assertEquals(mid / 2, v.failIndex());
    }

    @Test
    void cuttingACornerBetweenTwoInsideSamplesFails() {
        // Serpentine: jump straight from row 1 to row 2 — both endpoints are inside the corridor, the
        // line between them is not.
        TraceDifficulty d = TraceDifficulty.MASTERY;
        TracePath path = TracePathGenerator.fallback(d);
        double rowLen = 0.3;
        double[] a = path.pointAt(rowLen);
        double[] full = trace(path, 0.004, 0.0, d.halfWidth());
        List<Double> xy = new ArrayList<>();
        for (int i = 0; i < full.length; i += 2) {
            double arc = path.nearest(full[i], full[i + 1]).arc();
            if (arc > rowLen) {
                break;
            }
            xy.add(full[i]);
            xy.add(full[i + 1]);
        }
        xy.add(a[0]);
        xy.add(a[1] + 4 * d.halfWidth()); // straight down onto the next row
        xy.add(a[0] - 0.1);
        xy.add(a[1] + 4 * d.halfWidth());
        double[] arr = xy.stream().mapToDouble(Double::doubleValue).toArray();
        assertEquals(TraceOutcome.LEFT_PATH, TraceJudge.judge(path, d, false, arr).outcome());
    }

    @Test
    void notStartingOnTheStartMarkFails() {
        TraceDifficulty d = TraceDifficulty.BASIC;
        TracePath path = TracePathGenerator.generate(11, d);
        double[] xy = trace(path, 0.004, 0.0, d.halfWidth());
        double[] late = java.util.Arrays.copyOfRange(xy, 100, xy.length); // begins 0.2 widths along the path
        assertEquals(TraceOutcome.NOT_STARTED, TraceJudge.judge(path, d, false, late).outcome());
    }

    @Test
    void stoppingShortIsUnfinished() {
        TraceDifficulty d = TraceDifficulty.BASIC;
        TracePath path = TracePathGenerator.generate(5, d);
        double[] xy = trace(path, 0.004, 0.0, d.halfWidth());
        double[] half = java.util.Arrays.copyOf(xy, xy.length / 4 * 2);
        assertEquals(TraceOutcome.UNFINISHED, TraceJudge.judge(path, d, false, half).outcome());
    }

    @Test
    void malformedStreamsAreRejected() {
        TraceDifficulty d = TraceDifficulty.BASIC;
        TracePath path = TracePathGenerator.generate(5, d);
        assertEquals(TraceOutcome.MALFORMED, TraceJudge.judge(path, d, false, new double[] {0.5, 0.5}).outcome());
        assertEquals(TraceOutcome.MALFORMED, TraceJudge.judge(path, d, false, new double[3]).outcome());
        assertEquals(TraceOutcome.MALFORMED,
                TraceJudge.judge(path, d, false, new double[(TraceJudge.MAX_SAMPLES + 1) * 2]).outcome());
    }

    @Test
    void assistWidensTheCorridorButNeverSkips() {
        TraceDifficulty d = TraceDifficulty.ADVANCED;
        TracePath path = TracePathGenerator.fallback(d);
        // 1.1 half-widths out: outside without assist, inside with it.
        double[] xy = trace(path, 0.004, 1.1, d.halfWidth());
        xy[0] = path.startX();
        xy[1] = path.startY();
        assertEquals(TraceOutcome.LEFT_PATH, TraceJudge.judge(path, d, false, xy).outcome());
        assertTrue(TraceJudge.judge(path, d, true, xy).outcome() != TraceOutcome.LEFT_PATH);
        // An empty attempt fails with or without it.
        assertEquals(TraceOutcome.MALFORMED, TraceJudge.judge(path, d, true, new double[0]).outcome());
    }

    @Test
    void quantisationRoundTripsWithinASubPixel() {
        for (double v = 0; v <= 1.0; v += 0.0137) {
            assertEquals(v, TraceJudge.dequantise(TraceJudge.quantise(v)), 1.0 / 65535);
        }
    }
}
