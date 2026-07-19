package at.koopro.wizardsandbeasts.ministry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Server-side Ministry lifecycle. Currently just the cooling of heat; Auror dispatch and sentence ticking
 * join it in the enforcement phase.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class MinistryEvents {

    /** Notoriety is recomputed on a slow cadence — it moves in fractions per second, not per tick. */
    private static final int DECAY_INTERVAL_TICKS = 20;

    private MinistryEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % DECAY_INTERVAL_TICKS != 0) {
            return;
        }
        TraceService.decay(player, DECAY_INTERVAL_TICKS);
    }
}
