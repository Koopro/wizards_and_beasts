package at.koopro.wizardsandbeasts.event.veritaserum;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.veritaserum.VeritaserumService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The clock behind Veritaserum.
 *
 * <p>One handler, because the rest of the compulsion is enforced at the points that would let
 * somebody out of it — {@code PolyjuiceService.drink} and the Occlumency read — rather than by
 * watching for them here. A refusal belongs at the thing being refused; an event handler that tried
 * to undo a disguise after it had already been granted would leave a window in which it existed.
 *
 * <p>Returns immediately for anyone not compelled, which is everyone almost always: the cost of
 * Veritaserum existing on a server where nobody has been dosed is one attachment read per player
 * tick, the same shape as {@code FelixEvents}.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class VeritaserumEvents {

    private VeritaserumEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            VeritaserumService.tick(player);
        }
    }
}
