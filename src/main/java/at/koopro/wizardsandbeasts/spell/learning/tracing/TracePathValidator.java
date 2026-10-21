package at.koopro.wizardsandbeasts.spell.learning.tracing;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Whether a generated path is fair to ask of a player at a given difficulty.
 *
 * <p>The one property everything else rests on is <b>no near-self-contact</b>: two stretches of the path
 * that are far apart along it must also be far apart on the canvas, by at least the corridor's full width
 * plus a visible gap. That makes the corridor a single strip that never touches itself, so a pointer inside
 * it is always unambiguously "here" on the path, a short-cut between two stretches has to leave the
 * corridor, and the judge can be a simple distance test.
 */
@NullMarked
public final class TracePathValidator {

    /** Margin between the corridor's edge and the canvas edge. */
    public static final double CANVAS_MARGIN = 0.03;
    /** Extra clearance, in half-widths, between two stretches of corridor that must not touch. */
    public static final double GAP = 0.25;
    /** Two points this many half-widths apart along the path count as "different stretches". */
    public static final double ALONG_PATH_WINDOW = 4.0;
    /** Start and goal markers' radius, in half-widths. */
    public static final double MARKER_RADIUS = 1.25;

    private TracePathValidator() {
    }

    /** Null when fair, otherwise a short reason (for tests and logging). */
    public static @Nullable String problem(TracePath path, TraceDifficulty difficulty) {
        double hw = difficulty.halfWidth();
        double edge = hw * MARKER_RADIUS + CANVAS_MARGIN;
        for (int i = 0; i < path.size(); i++) {
            double x = path.x(i);
            double y = path.y(i);
            if (x < edge || y < edge || x > 1.0 - edge || y > 1.0 - edge) {
                return "outside canvas at point " + i;
            }
        }
        double length = path.length();
        if (length < difficulty.minLength() || length > difficulty.maxLength()) {
            return "length " + length + " outside [" + difficulty.minLength() + ", " + difficulty.maxLength() + "]";
        }
        double startGoal = Math.hypot(path.goalX() - path.startX(), path.goalY() - path.startY());
        if (startGoal < 3.0 * hw) {
            return "start and goal too close";
        }
        String contact = selfContact(path, hw);
        if (contact != null) {
            return contact;
        }
        // The goal mark must not sit on another stretch, or a player could appear to arrive early.
        double goalArc = path.length();
        double goalClear = hw * (MARKER_RADIUS + 1.0 + GAP);
        for (int i = 0; i < path.size(); i++) {
            if (goalArc - path.arcAt(i) > ALONG_PATH_WINDOW * hw
                    && Math.hypot(path.x(i) - path.goalX(), path.y(i) - path.goalY()) < goalClear) {
                return "goal marker overlaps the path at point " + i;
            }
        }
        return null;
    }

    public static boolean isFair(TracePath path, TraceDifficulty difficulty) {
        return problem(path, difficulty) == null;
    }

    /** Spatial-hash pair check: linear in the number of points rather than quadratic. */
    private static @Nullable String selfContact(TracePath path, double hw) {
        double minSep = hw * (2.0 + GAP);
        double window = hw * ALONG_PATH_WINDOW;
        Map<Long, List<Integer>> cells = new HashMap<>();
        for (int i = 0; i < path.size(); i++) {
            int cx = (int) Math.floor(path.x(i) / minSep);
            int cy = (int) Math.floor(path.y(i) / minSep);
            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    List<Integer> bucket = cells.get(key(cx + ox, cy + oy));
                    if (bucket == null) {
                        continue;
                    }
                    for (int j : bucket) {
                        if (path.arcAt(i) - path.arcAt(j) <= window) {
                            continue;
                        }
                        if (Math.hypot(path.x(i) - path.x(j), path.y(i) - path.y(j)) < minSep) {
                            return "self-contact between points " + j + " and " + i;
                        }
                    }
                }
            }
            cells.computeIfAbsent(key(cx, cy), k -> new ArrayList<>()).add(i);
        }
        return null;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
    }
}
