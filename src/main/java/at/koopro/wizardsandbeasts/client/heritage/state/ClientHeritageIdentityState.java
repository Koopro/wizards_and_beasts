package at.koopro.wizardsandbeasts.client.heritage.state;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.TransformationState;

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
 *
 * <p>Carries {@code transformationState} too. That used to live only on the local player's
 * {@code PlayerHeritageData}, so anything keyed off it rendered correctly in single-player and did
 * nothing for remote players — a failure mode no single-player test can reach.
 */
public final class ClientHeritageIdentityState {

    private static final Map<UUID, Identity> IDENTITIES = new ConcurrentHashMap<>();

    private ClientHeritageIdentityState() {}

    /**
     * Records a player's heritage. An unknown or empty id removes the entry rather than storing a
     * null-ish one, so "has not chosen a heritage" and "we were never told" are the same state to a
     * reader — which they are.
     */
    public static void update(UUID playerUUID, String heritageId, String variantId,
                              String transformationState) {
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
        IDENTITIES.put(playerUUID, new Identity(heritage, variant, parseState(transformationState)));
    }

    /**
     * An unrecognised state reads as {@code NORMAL} rather than throwing. A client on an older build
     * than the server will meet states it has no constant for, and the right answer to "I do not know
     * what shape they are in" is to draw them as themselves, not to drop the packet.
     */
    private static TransformationState parseState(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return TransformationState.NORMAL;
        }
        try {
            return TransformationState.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return TransformationState.NORMAL;
        }
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

    /**
     * @param transformationState what shape this player is currently in. Present here rather than only
     *                            on the local player's {@code PlayerHeritageData} because a renderer
     *                            needs it for everyone in the room, which is the whole point.
     */
    public record Identity(Heritage heritage, @Nullable HeritageVariant variant,
                           TransformationState transformationState) {}
}
