package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.command.debug.DebugPanelService;
import at.koopro.wizardsandbeasts.network.debug.DebugInspectRequestPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * Asks the server what the player is looking at, while there is a panel to fill.
 *
 * <p>Polling rather than pushing. The alternative — the server watching every operator's crosshair
 * and pushing when it changes — means a raycast per player per tick whether or not anybody has a
 * panel open, and the panel is a developer tool that is off almost always. A poll costs nothing when
 * the flag is off because there is nothing to poll.
 *
 * <p>The interval sits just above {@link DebugPanelService#MIN_TICKS_BETWEEN}, the server's floor.
 * Asking faster would only be dropped there.
 */
@NullMarked
public final class DebugPanelClient {

    /** Ticks between polls — four a second, which is as fast as a debug panel needs to move. */
    private static final int POLL_INTERVAL_TICKS = 5;

    private static int ticksSinceRequest;

    private DebugPanelClient() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ClientDebugPanelState.isDebugMode()) {
            ticksSinceRequest = 0;
            return;
        }
        if (++ticksSinceRequest < POLL_INTERVAL_TICKS) {
            return;
        }
        ticksSinceRequest = 0;
        ClientPacketDistributor.sendToServer(new DebugInspectRequestPayload());
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ticksSinceRequest = 0;
        ClientDebugPanelState.reset();
    }
}
