package at.koopro.wizardsandbeasts.client.apparition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionPointsState;
import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionPresentationState;
import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionWardState;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Client-side lifecycle for the Apparition presentation state.
 *
 * <p>The tick here does not advance any charge — the server owns that clock and every number in
 * {@link ClientApparitionPresentationState} arrives in a packet. What it does is hand finished attempts to
 * {@link ApparitionResolutionFx} and then age out anything that somehow went uncollected, so a client that
 * misses a tick does not accumulate them forever.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class ApparitionClientEvents {

    private ApparitionClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Play first, then age. The other order drops a resolution that arrived this very tick whenever the
        // lifetime is short, and it is the drain that has to be reliable: a missed crack is a jump that
        // happened in silence.
        ApparitionResolutionFx.drainAndPlay(Minecraft.getInstance());
        ApparitionResolutionFx.tick(Minecraft.getInstance());
        ClientApparitionPresentationState.tick();
    }

    /**
     * Drops every scrap of Apparition state the server told us. All three holders are per-world server data
     * that nothing was clearing, so wards, memorised destinations and in-flight charges all survived a
     * disconnect and leaked into whatever world was joined next.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientApparitionWardState.clear();
        ClientApparitionPointsState.clear();
        ClientApparitionPresentationState.clear();
        ApparitionResolutionFx.clear();
    }
}
