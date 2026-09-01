package at.koopro.wizardsandbeasts.client.ministry.state;

import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import org.jspecify.annotations.NullMarked;

/**
 * Client-side cache of the local player's own Ministry record, filled by
 * {@code MinistryRecordSyncS2CPayload}.
 *
 * <p>Holds only the local player's file. There is no map keyed by UUID here on purpose — the server never
 * sends anyone else's record, and a cache shaped to hold them would invite a future payload that leaks one.
 *
 * <p>{@code traceActive} is separate from an empty record because the two look identical otherwise: a clean
 * wizard and a server with the Ministry module switched off both show zero offences, and the sheet needs to
 * say something different in each case.
 */
@NullMarked
public final class ClientMinistryRecordState {

    private static PlayerMinistryRecord record = PlayerMinistryRecord.DEFAULT;
    private static boolean traceActive;
    private static boolean finesActive;

    private ClientMinistryRecordState() {}

    public static void set(PlayerMinistryRecord incoming, boolean trace, boolean fines) {
        record = incoming;
        traceActive = trace;
        finesActive = fines;
    }

    public static PlayerMinistryRecord get() {
        return record;
    }

    public static boolean traceActive() {
        return traceActive;
    }

    public static boolean finesActive() {
        return finesActive;
    }

    /**
     * Drops back to the default. Called on disconnect so a single-player world entered after leaving a
     * server does not open the sheet showing the other world's criminal record.
     */
    public static void clear() {
        record = PlayerMinistryRecord.DEFAULT;
        traceActive = false;
        finesActive = false;
    }
}
