package at.koopro.wizardsandbeasts.client.apparition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionPresentationState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Client-side lifecycle for the Apparition presentation state.
 *
 * <p>The tick here does not advance any charge — the server owns that clock and every number in
 * {@link ClientApparitionPresentationState} arrives in a packet. All this does is age out resolution events
 * nothing came to collect, so a client that misses a render pass does not accumulate them forever.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ApparitionClientEvents {

    private ApparitionClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientApparitionPresentationState.tick();
    }
}
