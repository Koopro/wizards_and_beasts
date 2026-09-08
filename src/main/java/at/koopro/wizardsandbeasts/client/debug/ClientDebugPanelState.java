package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.command.debug.report.DebugLine;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What the client knows about the debug panel: whether it is on, and the last thing the server said.
 *
 * <p>Nothing here is authoritative. The debug flag lets the client stop polling and hide the box; it
 * does not decide whether it is allowed to be told anything, which is the server's business. The
 * report is a cached answer to a question asked a few ticks ago, and is drawn as such — see
 * {@link #isStale}.
 */
@NullMarked
public final class ClientDebugPanelState {

    /**
     * Ticks after which a cached answer is dimmed rather than trusted.
     *
     * <p>Longer than the poll interval on purpose: at exactly the poll interval every panel would
     * flicker between fresh and stale on a normal, healthy connection.
     */
    private static final long STALE_AFTER_TICKS = 20L;

    private static boolean debugMode;
    private static @Nullable Vec3 anchor;
    private static String title = "";
    private static List<DebugLine> lines = List.of();
    private static long receivedAtTick;

    private ClientDebugPanelState() {}

    public static boolean isDebugMode() {
        return debugMode;
    }

    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
        if (!enabled) {
            clearTarget();
        }
    }

    public static void setTarget(Vec3 newAnchor, String newTitle, List<DebugLine> newLines,
                                 long nowTick) {
        anchor = newAnchor;
        title = newTitle;
        lines = List.copyOf(newLines);
        receivedAtTick = nowTick;
    }

    public static void clearTarget() {
        anchor = null;
        title = "";
        lines = List.of();
    }

    /** Everything forgotten — on logout, so a panel does not survive into the next world. */
    public static void reset() {
        debugMode = false;
        clearTarget();
    }

    public static @Nullable Vec3 anchor() {
        return anchor;
    }

    public static String title() {
        return title;
    }

    public static List<DebugLine> lines() {
        return lines;
    }

    public static boolean hasTarget() {
        return anchor != null && !lines.isEmpty();
    }

    public static boolean isStale(long nowTick) {
        return nowTick - receivedAtTick > STALE_AFTER_TICKS;
    }
}
