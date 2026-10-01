package at.koopro.wizardsandbeasts.command.debug;

import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DebugModeService {
    private static final Set<UUID> ENABLED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static volatile boolean globalEnabled;

    private DebugModeService() {
    }

    public static boolean toggleForPlayer(ServerPlayer player) {
        return togglePlayerUuid(player.getUUID());
    }

    /** Same toggle semantics as {@link #toggleForPlayer(ServerPlayer)}; package-private for unit tests. */
    static boolean togglePlayerUuid(UUID id) {
        if (!ENABLED_PLAYERS.add(id)) {
            ENABLED_PLAYERS.remove(id);
            return false;
        }
        return true;
    }

    static void resetForTests() {
        ENABLED_PLAYERS.clear();
        globalEnabled = false;
    }

    public static boolean toggleGlobal() {
        globalEnabled = !globalEnabled;
        return globalEnabled;
    }

    /** Sets one player's debug mode (the toggle's set form, for callers that must restore a known state). */
    public static void setForPlayer(UUID id, boolean enabled) {
        if (enabled) {
            ENABLED_PLAYERS.add(id);
        } else {
            ENABLED_PLAYERS.remove(id);
        }
    }

    public static boolean isEnabledFor(UUID id) {
        return globalEnabled || ENABLED_PLAYERS.contains(id);
    }

    /** Whether this player has debug mode of their own, apart from the all-players switch. */
    public static boolean hasOwn(UUID id) {
        return ENABLED_PLAYERS.contains(id);
    }

    public static void setGlobal(boolean enabled) {
        globalEnabled = enabled;
    }

    public static boolean isGlobalEnabled() {
        return globalEnabled;
    }

    public static boolean isEnabled(ServerPlayer player) {
        return globalEnabled || ENABLED_PLAYERS.contains(player.getUUID());
    }
}
