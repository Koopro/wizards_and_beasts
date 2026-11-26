package at.koopro.wizardsandbeasts.event.pose;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.artefact.cabinet.VanishingCabinetBlock;
import at.koopro.wizardsandbeasts.block.ExaminationDeskBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooGrateBlock;
import at.koopro.wizardsandbeasts.pose.PlayerGesture;
import at.koopro.wizardsandbeasts.pose.PlayerGestures;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A hand put out to a magical fitting — the wandmaker's bench, a Vanishing Cabinet, an examination desk, a Floo
 * hearth — plays {@link PlayerGesture#REACH}.
 *
 * <p>One listener rather than a line in each block, because all four only open something (a screen, a door) and the
 * gesture is the same for each. {@code LOWEST} and only when nothing cancelled the click, so a click another system
 * refused does not reach out at nothing. A Floo departure started by the same click overrides it with its own throw:
 * the later gesture wins on the client.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class GestureInteractHooks {

    private GestureInteractHooks() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player) || player.isShiftKeyDown()) {
            return;
        }
        if (reachable(event.getLevel().getBlockState(event.getPos()).getBlock())) {
            PlayerGestures.play(player, PlayerGesture.REACH);
        }
    }

    static boolean reachable(Block block) {
        return block instanceof WandmakersBenchBlock
                || block instanceof VanishingCabinetBlock
                || block instanceof ExaminationDeskBlock
                || block instanceof FlooGrateBlock;
    }
}
