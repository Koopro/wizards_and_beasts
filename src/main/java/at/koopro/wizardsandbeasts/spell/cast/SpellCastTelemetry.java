package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side, in-memory record of each player's recent spell-cast outcomes (success vs. misfire).
 * Used as the "recent failure rate" signal feeding apparition focus — a shaky wand hand splinches more.
 *
 * <p>Transient by design: the window is short and only meaningful for the current session, so it is
 * not persisted or synced. Entries are evicted on logout to keep the map bounded to online players.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class SpellCastTelemetry {

    /** Number of most-recent casts considered when computing the failure rate. */
    private static final int WINDOW = 20;

    /**
     * Failure rate returned before a player has cast enough to fill the window. Matches the historical
     * hardcoded default so apparition balance is unchanged for players who have not yet cast.
     */

    /** Minimum recorded casts before the measured rate is trusted over {@link #DEFAULT_RATE}. */

    /** Per-player ring of recent outcomes; {@code true} == misfire. Bounded to {@link #WINDOW}. */
    private static final Map<UUID, Deque<Boolean>> HISTORY = new ConcurrentHashMap<>();

    private SpellCastTelemetry() {}

    /** Record one cast outcome for the player. {@code failed} is true when the cast misfired/fizzled. */
    public static void recordCast(Player player, boolean failed) {
        Deque<Boolean> ring = HISTORY.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>(WINDOW));
        synchronized (ring) {
            ring.addLast(failed);
            while (ring.size() > WINDOW) {
                ring.removeFirst();
            }
        }
    }

    public static void clear(UUID playerId) {
        HISTORY.remove(playerId);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HISTORY.remove(event.getEntity().getUUID());
    }
}
