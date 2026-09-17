package at.koopro.wizardsandbeasts.client.disguise;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of who looks like whom, keyed by the disguised player's UUID.
 *
 * <p>Mirrors {@code ClientPetrifyState}. Concurrent because the payload handler and the render thread
 * touch it from different threads, and a disguise resolving to a torn read would be a player
 * flickering between two faces.
 */
public final class ClientDisguiseState {

    /** Who a player appears as. Absent means they appear as themselves. */
    public record Disguise(UUID targetId, String targetName) {}

    private static final Map<UUID, Disguise> DISGUISES = new ConcurrentHashMap<>();

    private ClientDisguiseState() {}

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

    /**
     * Cleared on disconnect, so a disguise cannot follow a player into the next world they join.
     *
     * <p>Called from {@link ClientDisguiseLifecycle}, and it is worth naming the caller: this method
     * existed and was documented as "cleared on disconnect" for the whole life of the Polyjuice
     * version and nothing ever called it, which meant a single-player world entered after a
     * multiplayer session could draw somebody else's face on a player who happened to share a UUID.
     *
     * <p>Clears only the map. The renderer's own caches are dropped by the lifecycle beside this
     * call, so that nothing here names a client-only class: this class is reachable from
     * {@code ClientPayloadHandlers}, which is loaded on a dedicated server when the payload registrar
     * resolves its method references.
     */
    public static void clear() {
        DISGUISES.clear();
    }
}
