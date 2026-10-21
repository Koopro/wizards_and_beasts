package at.koopro.wizardsandbeasts.spell.learning.tracing;

import org.jspecify.annotations.NullMarked;

/**
 * The server's verdict on a submitted sample stream. Shape checks only — timing and source belong to
 * {@link SpellTraceService}, which owns the clock and the inventory.
 */
@NullMarked
public final class TraceJudge {

    /** Hard ceiling on submitted samples: bounds the payload and the work a submission can cost. */
    public static final int MAX_SAMPLES = 2048;
    /** Fewer than this is not a trace of anything. */
    public static final int MIN_SAMPLES = 4;

    private TraceJudge() {
    }

    /**
     * @param xy interleaved canvas coordinates {@code x0, y0, x1, y1, …}
     */
    public static TraceProgress.Verdict judge(TracePath path, TraceDifficulty difficulty, boolean assist, double[] xy) {
        int count = xy.length / 2;
        if (xy.length % 2 != 0 || count < MIN_SAMPLES || count > MAX_SAMPLES) {
            return new TraceProgress.Verdict(TraceOutcome.MALFORMED, -1, 0.0);
        }
        TraceProgress progress = new TraceProgress(path, halfWidth(difficulty, assist));
        for (int i = 0; i < count && !progress.failed() && !progress.finished(); i++) {
            progress.accept(xy[2 * i], xy[2 * i + 1]);
        }
        return progress.verdict(difficulty.maxMeanDeviation());
    }

    /** The corridor half-width actually judged, after the accessibility assist. */
    public static double halfWidth(TraceDifficulty difficulty, boolean assist) {
        return difficulty.halfWidth() * (assist ? TraceDifficulty.ASSIST_FACTOR : 1.0);
    }

    /** Time limit for {@code path}, after the assist. */
    public static int timeLimitTicks(TracePath path, TraceDifficulty difficulty, boolean assist) {
        return (int) Math.ceil(difficulty.timeLimitTicks(path.length()) * (assist ? TraceDifficulty.ASSIST_FACTOR : 1.0));
    }

    /** Shortest believable time to trace {@code path} by hand, in ticks. */
    public static int minimumTicks(TracePath path) {
        return (int) Math.floor(path.length() / TraceDifficulty.MAX_POINTER_SPEED * 20.0);
    }

    /** Canvas coordinate → wire value. */
    public static int quantise(double v) {
        return (int) Math.round(Math.max(0.0, Math.min(1.0, v)) * 65535.0);
    }

    /** Wire value → canvas coordinate. */
    public static double dequantise(int q) {
        return (q & 0xFFFF) / 65535.0;
    }
}
