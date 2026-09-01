package at.koopro.wizardsandbeasts.ministry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ministry.law.MinistryFines;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Server-side Ministry lifecycle: the cooling (or warming) of heat, and the standing order against a
 * debtor's Gringotts vault. Auror dispatch and sentence ticking join it in the enforcement phase.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class MinistryEvents {

    /** Notoriety is recomputed on a slow cadence — it moves in fractions per second, not per tick. */
    private static final int DECAY_INTERVAL_TICKS = 20;

    /**
     * How often a standing fine is swept against the vault. Slower than the heat tick because it reads and
     * writes two attachments and pushes a vault sync; five seconds is well inside the time it takes a
     * player to walk out of Gringotts having just deposited.
     */
    private static final int COLLECTION_INTERVAL_TICKS = 100;

    private MinistryEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % DECAY_INTERVAL_TICKS == 0) {
            TraceService.decay(player, DECAY_INTERVAL_TICKS);
        }
        if (player.tickCount % COLLECTION_INTERVAL_TICKS == 0) {
            // Returns immediately for the overwhelming majority of players, who owe nothing.
            MinistryFines.collect(player);
        }
    }
}
