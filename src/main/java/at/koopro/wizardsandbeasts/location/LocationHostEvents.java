package at.koopro.wizardsandbeasts.location;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Sneak-use on a location's host opens that location's board, for every location at once: a goblin teller opens
 * Gringotts' business, a wandmaker Ollivanders'. A plain use keeps doing what the host always did (the vault, the
 * wand trial), so nothing a player already knows changes. Runs ahead of those handlers and cancels them only when
 * it opened a board.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class LocationHostEvents {

    private LocationHostEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // A courier parcel for this place is handed over first; the board then opens as usual.
        at.koopro.wizardsandbeasts.travel.courier.CourierService.deliverAt(player, event.getTarget());
        if (LocationProgression.openAtHost(player, event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
