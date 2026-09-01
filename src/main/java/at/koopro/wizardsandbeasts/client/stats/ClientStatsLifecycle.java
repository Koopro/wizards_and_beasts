package at.koopro.wizardsandbeasts.client.stats;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Drops the client's stat snapshot on disconnect.
 *
 * <p>{@link ClientStatsState#clear()} existed and had no callers, so one server's stat block survived
 * into the next session. On a server without this mod — or one where {@code PLAYER_STATS} is off —
 * nothing would ever overwrite it, and the character sheet would keep showing the previous world's
 * numbers as though they were this character's.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ClientStatsLifecycle {

    private ClientStatsLifecycle() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientStatsState.clear();
        ClientStatLevelUps.clear();
    }
}
