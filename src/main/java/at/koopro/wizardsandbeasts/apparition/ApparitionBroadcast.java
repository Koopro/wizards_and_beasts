package at.koopro.wizardsandbeasts.apparition;

import org.jspecify.annotations.NullMarked;

/**
 * Where the mechanics reach the presentation layer. One mutable slot holding an
 * {@link ApparitionEventBroadcaster}, defaulted to {@link ApparitionEventBroadcaster#NOOP} so the server
 * behaves identically whether or not anything is listening.
 */
@NullMarked
public final class ApparitionBroadcast {

    private static ApparitionEventBroadcaster broadcaster = ApparitionEventBroadcaster.NOOP;

    private ApparitionBroadcast() {}

    public static ApparitionEventBroadcaster get() {
        return broadcaster;
    }

    /** Installs a listener. Called once during setup; passing {@code null} is not permitted. */
    public static void install(ApparitionEventBroadcaster value) {
        broadcaster = value;
    }

    public static void reset() {
        broadcaster = ApparitionEventBroadcaster.NOOP;
    }
}
