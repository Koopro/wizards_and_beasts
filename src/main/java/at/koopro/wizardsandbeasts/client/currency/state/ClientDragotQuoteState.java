package at.koopro.wizardsandbeasts.client.currency.state;

/**
 * The last exchange quote the server sent, held for the Gringotts screen to draw.
 *
 * <p>Display only. The screen never computes a rate and never sends one back — it asks the server to
 * sell ten Dragots and the server applies whatever quote it is standing behind. If this state is
 * stale the player sees a stale number for a moment; they can never transact at one.
 */
public final class ClientDragotQuoteState {

    private static float rate;
    private static float drift;
    private static int purse;

    private ClientDragotQuoteState() {}

    public static void load(float newRate, float newDrift, int newPurse) {
        rate = newRate;
        drift = newDrift;
        purse = newPurse;
    }

    /** Galleons per Dragot as last quoted, or {@code 0} before the counter has ever been opened. */
    public static float rate() {
        return rate;
    }

    /** How far that quote sits above or below the configured base, as a percentage. */
    public static float drift() {
        return drift;
    }

    /** Dragots the player is carrying, as last counted server-side. */
    public static int purse() {
        return purse;
    }

    public static boolean hasQuote() {
        return rate > 0.0f;
    }
}
