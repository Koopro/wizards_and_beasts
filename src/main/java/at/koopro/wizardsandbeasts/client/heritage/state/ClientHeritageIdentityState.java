package at.koopro.wizardsandbeasts.client.heritage.state;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which heritage every visible player is, keyed by UUID.
 *
 * <p>The per-player counterpart to {@link ClientHeritageDataState}, which holds a single
 * {@code PlayerHeritageData} and therefore only ever describes the local player. A render path needs
 * to know what the <em>other</em> five people in the room are, so this exists alongside it rather
 * than replacing it — the HUD keeps reading the singleton, the renderer reads this.
 *
 * <p>Same shape as {@code ClientFormDataState}: a concurrent map, written from the payload handler
 * and read from the render thread.
 */
public final class ClientHeritageIdentityState {

    private static final Map<UUID, Identity> IDENTITIES = new ConcurrentHashMap<>();

    private ClientHeritageIdentityState() {}

    /**
     * Records a player's heritage. An unknown or empty id removes the entry rather than storing a
     * null-ish one, so "has not chosen a heritage" and "we were never told" are the same state to a
     * reader — which they are.
     */
    public static void update(UUID playerUUID, String heritageId, String variantId) {
        Heritage heritage = Heritage.byId(heritageId);
        if (heritage == null) {
            IDENTITIES.remove(playerUUID);
            return;
        }
        HeritageVariant variant = HeritageVariant.byId(variantId);
        if (variant != null && variant.getParentHeritage() != heritage) {
            // A variant from another heritage is a server-side bug, not something to render around.
            variant = null;
        }
        IDENTITIES.put(playerUUID, new Identity(heritage, variant));
    }

    public static @Nullable Identity get(UUID playerUUID) {
        return IDENTITIES.get(playerUUID);
    }

    public static void remove(UUID playerUUID) {
        IDENTITIES.remove(playerUUID);
    }

    /**
     * Dropped on disconnect. UUIDs are stable across worlds, so without this a player seen as a
     * half-giant on one server would still be one after joining another.
     */
    public static void clear() {
        IDENTITIES.clear();
    }

    public static int size() {
        return IDENTITIES.size();
    }

    public record Identity(Heritage heritage, @Nullable HeritageVariant variant) {}
}
