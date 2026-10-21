package at.koopro.wizardsandbeasts.client.career;

import at.koopro.wizardsandbeasts.owl.career.track.CareerView;
import org.jspecify.annotations.NullMarked;

/**
 * The last Career tab view the server sent. A readout only: every number and line in it was decided by the
 * server ({@code CareerProgression#view}), and the tab sends intents back, never results.
 */
@NullMarked
public final class ClientCareerState {

    private static volatile CareerView view = CareerView.NONE;
    private static volatile long version;

    private ClientCareerState() {}

    public static CareerView get() {
        return view;
    }

    /** Bumps on every update, so the tab can tell a fresh view from the one it already laid out. */
    public static long version() {
        return version;
    }

    public static void set(CareerView next) {
        view = next;
        version++;
    }

    /** On disconnect: another server's career must not show on the next one's sheet. */
    public static void clear() {
        set(CareerView.NONE);
    }
}
