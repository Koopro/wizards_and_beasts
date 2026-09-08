package at.koopro.wizardsandbeasts.command.debug;

import at.koopro.wizardsandbeasts.command.debug.inspect.DebugInspectors;
import at.koopro.wizardsandbeasts.network.debug.DebugInspectResultPayload;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * The server half of the floating panel: answers "what am I looking at", no faster than it means to.
 *
 * <h2>The rate limit is the point</h2>
 *
 * <p>An inspection is a raycast plus a full state dump, and the client asks for one on a timer. A
 * client that ignores its own timer — or a modified one that does not have a timer — would otherwise
 * be able to make the server run a dump per packet. So the floor lives here, on the server, and a
 * request that arrives early is dropped rather than queued: queueing would only move the cost.
 *
 * <p>Debug mode is checked here too, not merely on the client. The client's copy of the flag is a
 * hint that lets it stop asking; it is not what decides whether it may be told.
 */
@NullMarked
public final class DebugPanelService {

    /**
     * Minimum ticks between two answers to the same player.
     *
     * <p>Deliberately one tick under {@code DebugPanelClient.POLL_INTERVAL_TICKS} rather than equal
     * to it. A client polling at exactly the floor sits on the boundary, and the two clocks do not
     * agree tick for tick — some of its perfectly well-behaved requests would land a tick early and
     * be dropped, which reads as a panel that stutters. The slack costs nothing and the cap is still
     * five answers a second.
     */
    public static final int MIN_TICKS_BETWEEN = 4;

    private static final PlayerScopedState<Long> LAST_ANSWER_TICK =
            PlayerScopedState.create("debug_panel_last_answer");

    private DebugPanelService() {}

    public static void answer(ServerPlayer player) {
        if (!DebugModeService.isEnabled(player)) {
            // Tell them once, so a client whose flag went stale stops asking and hides its panel.
            DebugInspectResultPayload.sendTo(player, DebugInspectResultPayload.empty());
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_ANSWER_TICK.get(player);
        if (last != null && now - last < MIN_TICKS_BETWEEN) {
            return;
        }
        LAST_ANSWER_TICK.put(player, now);

        DebugInspectResultPayload.sendTo(player, DebugInspectors.lookedAt(player)
                .map(result -> DebugInspectResultPayload.of(result.anchor(), result.report()))
                .orElseGet(DebugInspectResultPayload::empty));
    }
}
