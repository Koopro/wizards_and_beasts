package at.koopro.wizardsandbeasts.client.render.outline;

import at.koopro.wizardsandbeasts.render.outline.OutlineEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's view of who is outlined, in what colour, and until when.
 *
 * <p>Keyed by <b>entity UUID and shared across every entity</b>, not a self-only singleton. An outline
 * exists to be seen by other people; a per-client "my own state" cache would render the feature
 * invisible in exactly the case it is for. The server is authoritative and broadcasts to everyone.
 *
 * <p>Still purely server-driven: an entry leaves this map only when the server clears it. The expiry is
 * read for one thing, the last-second fade in {@link #colorFor(Entity)}.
 *
 * <p>Not {@code PlayerScopedState}: that clears on {@code PlayerLoggedOutEvent}, which never fires on
 * the client, so entries would accumulate for the session. Cleanup here hangs off disconnect instead.
 */
@NullMarked
public final class ClientOutlineState {

    private static final Map<UUID, OutlineEntry> BY_ENTITY = new ConcurrentHashMap<>();

    private ClientOutlineState() {}

    /** Applies one entry. {@link OutlineEntry#NONE} (colour {@code 0}) removes it. */
    public static void put(UUID entityId, OutlineEntry entry) {
        if (entry.isNone()) {
            BY_ENTITY.remove(entityId);
        } else {
            BY_ENTITY.put(entityId, entry);
        }
    }

    public static void replaceAll(Map<UUID, OutlineEntry> snapshot) {
        BY_ENTITY.clear();
        snapshot.forEach(ClientOutlineState::put);
    }

    public static void clear() {
        BY_ENTITY.clear();
    }

    /** Provider hook for {@link EntityOutlines}: the colour to draw this frame, faded near the end. */
    public static int colorFor(Entity entity) {
        OutlineEntry entry = BY_ENTITY.get(entity.getUUID());
        if (entry == null) {
            return EntityOutlines.NO_OUTLINE;
        }
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return entry.colourAt(entity.level().getGameTime(), partialTick);
    }

    /** The colour as sent, unfaded. */
    public static int colorFor(UUID entityId) {
        OutlineEntry entry = BY_ENTITY.get(entityId);
        return entry == null ? EntityOutlines.NO_OUTLINE : entry.argb();
    }

    /**
     * Dropping the map on disconnect matters: UUIDs are stable across worlds, so a stale entry would
     * follow a player into an unrelated singleplayer save and outline them there.
     */
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
