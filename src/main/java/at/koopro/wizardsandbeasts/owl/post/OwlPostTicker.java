package at.koopro.wizardsandbeasts.owl.post;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Drives {@link OwlPostService#sweep}.
 *
 * <p>Once a second, not once a tick: a parcel has a two-minute flight and nothing about it needs
 * twenty checks per second. The sweep walks a list that is almost always empty, so the cost when no
 * post is in the air is a size check.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
@NullMarked
public final class OwlPostTicker {

    private OwlPostTicker() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var overworld = event.getServer().overworld();
        if (overworld.getGameTime() % OwlPostService.SWEEP_INTERVAL_TICKS != 0) {
            return;
        }
        OwlPostService.sweep(overworld);
    }
}
