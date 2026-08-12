package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.network.pose.PoseOverrideSyncS2CPayload;
import at.koopro.wizardsandbeasts.pose.PoseOverride;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of every tracked player's {@code PoseOverride}.
 *
 * <p>Keyed by UUID rather than held on the player, because a pass runs against a render state and
 * the entity may not be conveniently in hand — and because the client must know the override for
 * <em>other</em> players, which is the whole reason the state is synced rather than local.
 *
 * <p>Entries are dropped on world unload; a stale entry for a player who left is harmless in the
 * meantime, since nothing looks up an override for an entity that is not rendering.
 */
@NullMarked
public final class ClientPoseState {

    private static final Map<UUID, PoseOverride> OVERRIDES = new ConcurrentHashMap<>();

    private ClientPoseState() {}

    /** Payload handler. Runs on the client thread via the context. */
    public static void handleSync(PoseOverrideSyncS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.override().active()) {
                OVERRIDES.put(payload.playerUuid(), payload.override());
            } else {
                // An inactive override is an absence, not a stored "nothing" — so clearing removes
                // the entry rather than parking a NONE that every lookup then has to unwrap.
                OVERRIDES.remove(payload.playerUuid());
            }
        });
    }

    public static PoseOverride get(UUID playerUuid) {
        return OVERRIDES.getOrDefault(playerUuid, PoseOverride.NONE);
    }

    /** Called on disconnect / world change so a rejoin cannot inherit the last session's poses. */
    public static void clear() {
        OVERRIDES.clear();
    }
}
