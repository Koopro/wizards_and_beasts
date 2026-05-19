package at.koopro.wizardsandbeasts.client.ability.state;

import at.koopro.wizardsandbeasts.ability.data.PlayerAbilityData;
import org.jspecify.annotations.NonNull;

public final class ClientAbilityCache {
    private static @NonNull PlayerAbilityData data = PlayerAbilityData.DEFAULT;

    private ClientAbilityCache() {
    }

    public static @NonNull PlayerAbilityData get() {
        return data;
    }

    public static void set(@NonNull PlayerAbilityData newData) {
        data = newData;
    }

    public static void clear() {
        data = PlayerAbilityData.DEFAULT;
    }
}
