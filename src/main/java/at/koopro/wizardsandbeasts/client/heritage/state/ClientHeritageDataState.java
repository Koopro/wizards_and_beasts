package at.koopro.wizardsandbeasts.client.heritage.state;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.TransformationState;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * The local player's heritage, as of the last sync.
 *
 * <p>Read by the character sheet's POWER cap tick, the HUD and the appearance layer, so what is in here is
 * what the player is told they are. Cleared on disconnect by {@link ClientHeritageLifecycle} — see
 * {@link #clear()} for the two separate ways carrying it into the next session goes wrong.
 */
public final class ClientHeritageDataState {
    private static final PlayerHeritageData INSTANCE = new PlayerHeritageData();

    /**
     * Guards against an out-of-order sync overwriting a newer one.
     *
     * <p>Scoped to a connection, not to the client: the counter it compares against is a plain
     * {@code AtomicInteger} on the server, which starts at zero every time a server does.
     */
    private static int lastSyncVersion;

    private ClientHeritageDataState() {}

    public static void applySync(int syncVersion,
                                 @Nullable Heritage heritage,
                                 @Nullable HeritageVariant variant,
                                 boolean locked,
                                 TransformationState state,
                                 @Nullable String activeFormId,
                                 boolean debugOverlay,
                                 Map<String, String> flags,
                                 int professionPoints,
                                 int totalProfessionPointsEarned,
                                 Set<String> unlockedProfessions,
                                 @Nullable String selectedProfessionId) {
        if (syncVersion < lastSyncVersion) {
            return;
        }
        lastSyncVersion = syncVersion;
        INSTANCE.applySync(heritage, variant, locked, state, activeFormId, debugOverlay, flags,
                professionPoints, totalProfessionPointsEarned, unlockedProfessions, selectedProfessionId);
    }

    public static PlayerHeritageData get() {
        return INSTANCE;
    }

    /**
     * Forgets the previous connection entirely, both halves.
     *
     * <p>The data, because {@code PlayerHeritageData} is one static instance with no world scoping: on a
     * server without this mod — or one where {@code HERITAGE} is off — nothing ever overwrites it, and the
     * character sheet keeps drawing the last world's heritage and its POWER cap as though they were this
     * character's. The same argument as {@code ClientStatsLifecycle}, whose {@code clear()} had no callers
     * either.
     *
     * <p>And {@link #lastSyncVersion}, which is the worse of the two. The version it is compared against is
     * a server-side counter that starts at zero with the process, so a client that had climbed to version
     * 40 on one server and then joined a freshly started one rejected <em>every</em> heritage sync it was
     * sent, for the whole session, with no packet lost and nothing logged. The player simply had no
     * heritage on the client: no cap tick, no HUD entry, and the appearance layer drawing them as whatever
     * the previous server said.
     */
    public static void clear() {
        INSTANCE.reset();
        lastSyncVersion = 0;
    }
}
