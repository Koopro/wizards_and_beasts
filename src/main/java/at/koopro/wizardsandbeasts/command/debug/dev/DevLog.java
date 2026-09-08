package at.koopro.wizardsandbeasts.command.debug.dev;

import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import org.jspecify.annotations.NullMarked;

/**
 * What a dev action actually did, collected as it does it.
 *
 * <h2>Why every action reports</h2>
 *
 * <p>These commands rewrite a player's progression. A command that silently sets fourteen fields is
 * one you cannot trust, because the failure mode — a field that was already right, a subsystem that
 * refused, a gate that turned out to be somewhere else — looks exactly like success. So each action
 * says what it changed, and just as importantly what it <em>skipped</em> and why.
 *
 * <p>It writes into a {@link DebugReport}, so the output is the same shape as every other debug dump
 * and can be read in chat or drawn in a panel without a second renderer.
 */
@NullMarked
public final class DevLog {

    private final DebugReport report;
    private int changes;
    private int skips;

    DevLog(DebugReport report) {
        this.report = report;
    }

    /** Something was written. */
    public void changed(String what) {
        changes++;
        report.state("  " + what, "done", ChatPalette.OK);
    }

    /** Something was written, and the new value is worth reading back. */
    public void changed(String what, Object detail) {
        changes++;
        report.state("  " + what, String.valueOf(detail), ChatPalette.OK);
    }

    /**
     * Nothing was written, and that is fine.
     *
     * <p>Distinct from {@link #warn}: a kit with no items to give and a licence that was already held
     * are both skips. Neither is a problem, and both are things you would otherwise have to go and
     * check by hand.
     */
    public void skip(String why) {
        skips++;
        report.note("  " + why);
    }

    /** Nothing was written and it should have been. */
    public void warn(String why) {
        report.warn("  " + why);
    }

    public int changes() {
        return changes;
    }

    public int skips() {
        return skips;
    }
}
