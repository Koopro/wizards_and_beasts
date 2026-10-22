package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.spell.learning.tracing.TraceDifficulty;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TracePath;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TracePathGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The game tests' "stray" sample must really stray, on every template a seed can draw. */
class TraceTestSupportTest {

    @Test
    void offPathIsOnTheCanvasAndOutsideTheWidestCorridor() {
        for (TraceDifficulty difficulty : TraceDifficulty.values()) {
            for (long seed = 0; seed < 500; seed++) {
                TracePath path = TracePathGenerator.generate(seed, difficulty);
                double[] xy = TraceTestSupport.centreLine(path);
                int mid = xy.length / 4 * 2;
                double[] p = TraceTestSupport.offPath(path, xy[mid], xy[mid + 1], difficulty.halfWidth());
                double allowed = difficulty.halfWidth() * TraceDifficulty.ASSIST_FACTOR;
                long s = seed;
                assertTrue(p[0] >= 0 && p[0] <= 1 && p[1] >= 0 && p[1] <= 1, () -> "off canvas at seed " + s);
                assertTrue(path.nearest(p[0], p[1]).distance() > allowed,
                        () -> difficulty + " seed " + s + ": the stray point is inside the corridor");
            }
        }
    }
}
