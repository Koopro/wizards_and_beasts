package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.spell.learning.tracing.SpellTraceService;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TraceChallenge;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TraceJudge;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TraceOutcome;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TracePath;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives a tracing challenge the way a steady hand would, through the same {@link SpellTraceService}
 * entry point the network payload calls. Every scenario that used to assert "reading teaches" now has
 * to trace in between, which is the point.
 */
final class TraceTestSupport {

    /** Long enough after issue that no path is "too fast", short enough that none is "too slow". */
    static final int HUMAN_TICKS = 60;

    private TraceTestSupport() {
    }

    /** Passes the player's live challenge. Null when there was none (or it was stale). */
    static @Nullable TraceOutcome pass(ServerPlayer player) {
        TraceChallenge c = SpellTraceService.active(player);
        if (c == null) {
            return null;
        }
        SpellTraceService.backdateForTest(player, HUMAN_TICKS);
        return SpellTraceService.submit(player, c.id(), false, centreLine(c.path()));
    }

    /** Fails the player's live challenge by leaving the corridor halfway. */
    static @Nullable TraceOutcome failOffPath(ServerPlayer player) {
        TraceChallenge c = SpellTraceService.active(player);
        if (c == null) {
            return null;
        }
        SpellTraceService.backdateForTest(player, HUMAN_TICKS);
        double[] xy = centreLine(c.path());
        int mid = xy.length / 4 * 2;
        xy[mid] = Math.min(1.0, xy[mid] + c.difficulty().halfWidth() * 4);
        return SpellTraceService.submit(player, c.id(), false, xy);
    }

    /** The path's centre line, sampled every 0.004 canvas units. */
    static double[] centreLine(TracePath path) {
        List<Double> out = new ArrayList<>();
        for (double s = 0; s < path.length(); s += 0.004) {
            double[] p = path.pointAt(s);
            out.add(p[0]);
            out.add(p[1]);
        }
        out.add(path.goalX());
        out.add(path.goalY());
        // Keep inside the wire limit even for the longest path.
        int pairs = Math.min(out.size() / 2, TraceJudge.MAX_SAMPLES);
        double[] xy = new double[pairs * 2];
        int stride = Math.max(1, (out.size() / 2 + pairs - 1) / pairs);
        for (int i = 0; i < pairs - 1; i++) {
            xy[2 * i] = out.get(2 * i * stride);
            xy[2 * i + 1] = out.get(2 * i * stride + 1);
        }
        xy[2 * (pairs - 1)] = path.goalX();
        xy[2 * (pairs - 1) + 1] = path.goalY();
        return xy;
    }
}
