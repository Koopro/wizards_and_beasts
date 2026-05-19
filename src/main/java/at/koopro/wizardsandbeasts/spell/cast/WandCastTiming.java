package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-only: wand hold duration for the most recent {@code releaseUsing} (e.g. Protego tier).
 */
public final class WandCastTiming {
    private static final Map<UUID, Integer> LAST_HOLD_TICKS = new ConcurrentHashMap<>();

    private WandCastTiming() {}

    public static void recordRelease(ServerPlayer player, int holdTicks) {
        LAST_HOLD_TICKS.put(player.getUUID(), Math.max(0, holdTicks));
    }

    public static int consumeLastHoldTicks(ServerPlayer player) {
        Integer v = LAST_HOLD_TICKS.remove(player.getUUID());
        return v != null ? v : 0;
    }

    public static void clear(ServerPlayer player) {
        LAST_HOLD_TICKS.remove(player.getUUID());
    }
}
