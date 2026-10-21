package at.koopro.wizardsandbeasts.spell.learning.tracing;

import org.jspecify.annotations.NullMarked;

/**
 * One attempt at a path, fed a pointer sample at a time.
 *
 * <p>The same class judges on both sides. The client feeds it live so that a slip shows the moment it
 * happens; the server feeds it the submitted samples and its answer is the one that counts. Between
 * consecutive samples the motion is densified to quarter-half-width steps, so a fast flick that jumps
 * the pointer across a corner is judged on the line it travelled, not just on where it landed.
 *
 * <p>Allowed region: the corridor (distance to the centre line ≤ half-width), the start mark while the
 * attempt is still near the start, and the goal mark. {@link TracePathValidator} guarantees the goal mark
 * touches no other stretch of the corridor, so it cannot be reached by a short-cut.
 */
@NullMarked
public final class TraceProgress {

    /** Largest forward jump along the path between two densified points before it counts as skipping. */
    private static final double MAX_JUMP_HALF_WIDTHS = TracePathValidator.ALONG_PATH_WINDOW;

    private final TracePath path;
    private final double halfWidth;
    private final double markerRadius;

    private int samples;
    private double lastX;
    private double lastY;
    private double furthest;
    private double deviationSum;
    private int deviationCount;
    private boolean finished;
    private TraceOutcome failure = TraceOutcome.PASSED;
    private int failIndex = -1;
    private double failX;
    private double failY;

    public TraceProgress(TracePath path, double halfWidth) {
        this.path = path;
        this.halfWidth = halfWidth;
        this.markerRadius = halfWidth * TracePathValidator.MARKER_RADIUS;
    }

    /** Feeds one pointer position in canvas units. Ignored once the attempt has failed or finished. */
    public void accept(double x, double y) {
        if (failed() || finished) {
            return;
        }
        int index = samples++;
        if (index == 0) {
            if (Math.hypot(x - path.startX(), y - path.startY()) > markerRadius) {
                fail(TraceOutcome.NOT_STARTED, index, x, y);
                return;
            }
            lastX = x;
            lastY = y;
            visit(x, y, index);
            return;
        }
        double dist = Math.hypot(x - lastX, y - lastY);
        int steps = Math.max(1, (int) Math.ceil(dist / (halfWidth / 4.0)));
        for (int s = 1; s <= steps && !failed() && !finished; s++) {
            double t = (double) s / steps;
            visit(lastX + (x - lastX) * t, lastY + (y - lastY) * t, index);
        }
        lastX = x;
        lastY = y;
    }

    private void visit(double x, double y, int index) {
        TracePath.Projection p = path.nearest(x, y);
        boolean inCorridor = p.distance() <= halfWidth;
        boolean inStart = furthest <= 3.0 * halfWidth
                && Math.hypot(x - path.startX(), y - path.startY()) <= markerRadius;
        boolean inGoal = Math.hypot(x - path.goalX(), y - path.goalY()) <= markerRadius;
        if (!inCorridor && !inStart && !inGoal) {
            fail(TraceOutcome.LEFT_PATH, index, x, y);
            return;
        }
        if (inCorridor) {
            if (p.arc() - furthest > MAX_JUMP_HALF_WIDTHS * halfWidth) {
                fail(TraceOutcome.LEFT_PATH, index, x, y);
                return;
            }
            furthest = Math.max(furthest, p.arc());
            deviationSum += p.distance();
            deviationCount++;
        }
        if (inGoal && furthest >= path.length() - 2.0 * halfWidth) {
            finished = true;
        }
    }

    private void fail(TraceOutcome outcome, int index, double x, double y) {
        failure = outcome;
        failIndex = index;
        failX = x;
        failY = y;
    }

    public boolean failed() {
        return failure != TraceOutcome.PASSED;
    }

    /** Reached the goal mark having covered the path. Accuracy is only decided by {@link #verdict}. */
    public boolean finished() {
        return finished;
    }

    /** Furthest point reached along the path, 0..1. */
    public double fraction() {
        return Math.min(1.0, furthest / path.length());
    }

    public int samples() {
        return samples;
    }

    public double failX() {
        return failX;
    }

    public double failY() {
        return failY;
    }

    /** Mean distance from the centre line as a fraction of the half-width; 0 = perfect. */
    public double meanDeviation() {
        return deviationCount == 0 ? 0.0 : deviationSum / deviationCount / halfWidth;
    }

    /** 0..1, shown to the player as a percentage. */
    public double accuracy() {
        return Math.max(0.0, Math.min(1.0, 1.0 - meanDeviation()));
    }

    /** The attempt as it stands, treating "not finished yet" as {@link TraceOutcome#UNFINISHED}. */
    public Verdict verdict(double maxMeanDeviation) {
        if (failed()) {
            return new Verdict(failure, failIndex, accuracy());
        }
        if (!finished) {
            return new Verdict(TraceOutcome.UNFINISHED, Math.max(0, samples - 1), accuracy());
        }
        if (meanDeviation() > maxMeanDeviation) {
            return new Verdict(TraceOutcome.INACCURATE, -1, accuracy());
        }
        return new Verdict(TraceOutcome.PASSED, -1, accuracy());
    }

    public record Verdict(TraceOutcome outcome, int failIndex, double accuracy) {
    }
}
