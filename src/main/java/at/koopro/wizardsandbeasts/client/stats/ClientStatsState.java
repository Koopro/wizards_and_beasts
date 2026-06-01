package at.koopro.wizardsandbeasts.client.stats;

import at.koopro.wizardsandbeasts.stats.PlayerStatsData;
import org.jspecify.annotations.Nullable;

public final class ClientStatsState {

    @Nullable
    private static PlayerStatsData current = null;

    private ClientStatsState() {}

    public static void applySync(PlayerStatsData data) {
        current = data;
    }

    /** Returns the last synced stats, or null if no sync has been received yet. */
    @Nullable
    public static PlayerStatsData get() {
        return current;
    }

    /** True if at least one sync packet has been received this session. */
    public static boolean hasData() {
        return current != null;
    }

    public static void clear() {
        current = null;
    }
}
