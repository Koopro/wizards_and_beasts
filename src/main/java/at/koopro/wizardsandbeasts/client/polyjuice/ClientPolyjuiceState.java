package at.koopro.wizardsandbeasts.client.polyjuice;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of who looks like whom, keyed by the disguised player's UUID.
 *
 * <p>Mirrors {@code ClientPetrifyState}. Concurrent because the payload handler and the render thread
 * touch it from different threads, and a disguise resolving to a torn read would be a player flickering
 * between two faces.
 */
public final class ClientPolyjuiceState {

    /** Who a player appears as. Absent means they appear as themselves. */
    public record Disguise(UUID targetId, String targetName) {}

    private static final Map<UUID, Disguise> DISGUISES = new ConcurrentHashMap<>();

    private ClientPolyjuiceState() {}

    public static void set(UUID playerUUID, @Nullable Disguise disguise) {
        if (disguise == null) {
            DISGUISES.remove(playerUUID);
        } else {
            DISGUISES.put(playerUUID, disguise);
        }
    }

    public static @Nullable Disguise get(UUID playerUUID) {
        return DISGUISES.get(playerUUID);
    }

    public static boolean isDisguised(UUID playerUUID) {
        return DISGUISES.containsKey(playerUUID);
    }

    /** Cleared on disconnect, so a disguise cannot follow a player into the next world they join. */
    public static void clear() {
        DISGUISES.clear();
    }
}
