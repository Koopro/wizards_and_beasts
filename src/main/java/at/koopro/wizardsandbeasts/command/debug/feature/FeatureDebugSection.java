package at.koopro.wizardsandbeasts.command.debug.feature;

import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.module.Module;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One subsystem's answer to "what is this player's state, in your terms".
 *
 * <h2>Why a registry rather than a command per feature</h2>
 *
 * <p>Because there are roughly thirty features and they were being given debug output one at a time,
 * by whoever last needed it, in whatever shape they happened to reach for. Seven of them had a debug
 * node; the rest had a gameplay command that printed some of their state as a side effect, or
 * nothing at all. The gaps were not the unimportant subsystems — they were the ones nobody had
 * recently had to debug, which is a different thing entirely.
 *
 * <p>A section lives beside the subsystem it reports on, so it can be written by whoever knows what
 * the fields mean and can see them go stale when the subsystem changes. The command tree, the
 * {@code all} dump and the panel over a player's head all read from the same list.
 *
 * <p>Sections must be side-effect free. {@code /wandb debug feature all} runs every one of them in a
 * row, and the panel runs them several times a second.
 */
@NullMarked
public interface FeatureDebugSection {

    /** How much to print. */
    enum Detail {
        /** A handful of headline rows — what fits in a panel over someone's head. */
        BRIEF,
        /** Everything the section knows. */
        FULL
    }

    /** Command literal and lookup key: lowercase, no spaces. */
    String id();

    /** Section heading. */
    String title();

    /** One line for {@code /wandb debug feature} with no argument. */
    default String summary() {
        return "";
    }

    /**
     * The module this section reports on, if there is exactly one.
     *
     * <p>Reported beside the section so a subsystem that is switched off says so, rather than
     * printing a page of zeroes that reads as a bug.
     */
    default @Nullable Module module() {
        return null;
    }

    void append(DebugReport report, ServerPlayer target, Detail detail);
}
