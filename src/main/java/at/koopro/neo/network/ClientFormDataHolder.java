package at.koopro.neo.network;

import at.koopro.neo.form.RenderFlag;
import at.koopro.neo.form.SizeProfile;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of form/size data for all tracked players.
 * Updated by {@link FormSyncS2CPacket}.
 */
public final class ClientFormDataHolder {

    private static final Map<UUID, FormData> DATA = new ConcurrentHashMap<>();
    private static boolean debugOverlay = false;

    public static void update(UUID playerUUID, String formId, SizeProfile sizeProfile,
                               EnumSet<RenderFlag> renderFlags) {
        DATA.put(playerUUID, new FormData(formId, sizeProfile, renderFlags));
    }

    @Nullable
    public static FormData get(UUID playerUUID) {
        return DATA.get(playerUUID);
    }

    public static void remove(UUID playerUUID) {
        DATA.remove(playerUUID);
    }

    public static void setDebugOverlay(boolean enabled) {
        debugOverlay = enabled;
    }

    public static boolean isDebugOverlay() {
        return debugOverlay;
    }

    public static void clear() {
        DATA.clear();
        debugOverlay = false;
    }

    private ClientFormDataHolder() {}

    /**
     * Holds the cached form state for a single player on the client.
     */
    public record FormData(
            String formId,
            SizeProfile sizeProfile,
            EnumSet<RenderFlag> renderFlags
    ) {}
}
