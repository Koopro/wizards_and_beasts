package at.koopro.wizardsandbeasts.client.render.outline;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's view of who is outlined and in what colour.
 *
 * <p>Keyed by <b>entity UUID and shared across every entity</b>, not a self-only singleton. An outline
 * exists to be seen by other people; a per-client "my own state" cache would render the feature
 * invisible in exactly the case it is for. The server is authoritative and broadcasts to everyone.
 *
 * <p>Not {@code PlayerScopedState}: that clears on {@code PlayerLoggedOutEvent}, which never fires on
 * the client, so entries would accumulate for the session. Cleanup here hangs off disconnect instead.
 */
@NullMarked
public final class ClientOutlineState {

    private static final Map<UUID, Integer> BY_ENTITY = new ConcurrentHashMap<>();

    private ClientOutlineState() {}

    /** Applies one entry. A colour of {@link EntityOutlines#NO_OUTLINE} removes it. */
    public static void put(UUID entityId, int argb) {
        if (argb == EntityOutlines.NO_OUTLINE) {
            BY_ENTITY.remove(entityId);
        } else {
            BY_ENTITY.put(entityId, argb);
        }
    }

    public static void replaceAll(Map<UUID, Integer> snapshot) {
        BY_ENTITY.clear();
        snapshot.forEach(ClientOutlineState::put);
    }

    public static void clear() {
        BY_ENTITY.clear();
    }

    /** Provider hook for {@link EntityOutlines}. */
    public static int colorFor(Entity entity) {
        return colorFor(entity.getUUID());
    }

    public static int colorFor(UUID entityId) {
        return BY_ENTITY.getOrDefault(entityId, EntityOutlines.NO_OUTLINE);
    }

    /**
     * Dropping the map on disconnect matters: UUIDs are stable across worlds, so a stale entry would
     * follow a player into an unrelated singleplayer save and outline them there.
     */
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
