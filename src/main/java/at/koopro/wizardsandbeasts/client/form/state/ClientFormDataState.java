package at.koopro.wizardsandbeasts.client.form.state;

import at.koopro.wizardsandbeasts.form.RenderFlag;
import at.koopro.wizardsandbeasts.form.SizeProfile;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientFormDataState {
    private static final Map<UUID, FormData> DATA = new ConcurrentHashMap<>();
    private static boolean debugOverlay = false;

    private ClientFormDataState() {}

    public static void update(UUID playerUUID, String formId, SizeProfile sizeProfile, EnumSet<RenderFlag> renderFlags) {
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

    public record FormData(
            String formId,
            SizeProfile sizeProfile,
            EnumSet<RenderFlag> renderFlags
    ) {}
}
