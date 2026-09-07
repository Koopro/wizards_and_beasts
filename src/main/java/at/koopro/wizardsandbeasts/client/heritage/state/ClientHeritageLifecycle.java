package at.koopro.wizardsandbeasts.client.heritage.state;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Drops the client's copy of the local player's heritage, and of their blood pool, on disconnect.
 *
 * <p>The sibling of {@code ClientStatsLifecycle} and of the {@code ClientHeritageIdentityState.clear()} that
 * {@code PoseClientEvents} already performs — the per-player identity map was being cleared and the local
 * player's own block was not, which is the harder half to notice because in single-player the next world
 * overwrites it immediately.
 *
 * <p>{@link ClientHeritageDataState#clear()} carries the full argument for why both the data and its
 * sync-version guard have to go.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ClientHeritageLifecycle {

    private ClientHeritageLifecycle() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientHeritageDataState.clear();
        // Same argument, one system along: nothing on a server without this mod ever sends a blood
        // payload, so a vampire's meter would survive into the next world and draw a pool that is not
        // being drained by anything.
        ClientBloodState.clear();
    }
}
