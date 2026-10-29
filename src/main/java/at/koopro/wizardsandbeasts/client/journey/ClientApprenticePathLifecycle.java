package at.koopro.wizardsandbeasts.client.journey;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.NullMarked;

/** One world's path must not show in the next: the copy is dropped on leaving. */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ClientApprenticePathLifecycle {

    private ClientApprenticePathLifecycle() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientApprenticePathState.clear();
    }
}
