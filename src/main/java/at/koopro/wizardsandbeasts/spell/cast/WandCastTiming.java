package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.util.PlayerScopedState;

import at.koopro.wizardsandbeasts.spell.core.*;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Server-only: wand hold duration for the most recent {@code releaseUsing} (e.g. Protego tier).
 */
public final class WandCastTiming {
    private static final PlayerScopedState<Integer> LAST_HOLD_TICKS =
            PlayerScopedState.create("wand-cast-hold-ticks");

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
