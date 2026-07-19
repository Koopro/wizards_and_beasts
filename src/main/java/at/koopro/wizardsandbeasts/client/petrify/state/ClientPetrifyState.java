package at.koopro.wizardsandbeasts.client.petrify.state;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of which tracked players are currently petrified, keyed by UUID — mirrors
 * {@link at.koopro.wizardsandbeasts.client.form.state.ClientFormDataState}. Broadcast to every
 * tracking client (not just the petrified player), since a stone statue must be visible to everyone
 * in the room.
 */
public final class ClientPetrifyState {
    private static final Set<UUID> PETRIFIED = ConcurrentHashMap.newKeySet();

    private ClientPetrifyState() {}

    public static void set(UUID playerUUID, boolean petrified) {
        if (petrified) {
            PETRIFIED.add(playerUUID);
        } else {
            PETRIFIED.remove(playerUUID);
        }
    }

    public static boolean isPetrified(UUID playerUUID) {
        return PETRIFIED.contains(playerUUID);
    }

    public static void clear() {
        PETRIFIED.clear();
    }
}
