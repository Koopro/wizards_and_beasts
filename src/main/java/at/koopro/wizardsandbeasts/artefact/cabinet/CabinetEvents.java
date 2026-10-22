package at.koopro.wizardsandbeasts.artefact.cabinet;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The wand gesture on a Vanishing Cabinet: sneak and click it with a wand in the main hand.
 *
 * <p>An event rather than {@code useItemOn} because vanilla never asks the block about a sneaking click with
 * something in hand -- that click goes straight to the item, and a wand would cast. Cancelling here, on both
 * sides, keeps the wand quiet and swings the arm; the server then does the linking.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class CabinetEvents {

    private CabinetEvents() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !player.isSecondaryUseActive()
                || !(event.getItemStack().getItem() instanceof WandItem)) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!(state.getBlock() instanceof VanishingCabinetBlock)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel() instanceof ServerLevel level && player instanceof ServerPlayer sp) {
            CabinetService.Result result = CabinetService.gesture(sp, level, VanishingCabinetBlock.lowerOf(state, event.getPos()));
            CabinetService.tell(sp, result);
        }
    }
}
