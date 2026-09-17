package at.koopro.wizardsandbeasts.wand.allegiance;

import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Picking up another wizard's wand.
 *
 * <p>Taking a wand is not winning it: it stays its master's, and works for its new holder only grudgingly. The
 * transfer this class used to attempt on an equipment change was keyed to a disarm registry nothing ever filled,
 * so it never ran; winning a wand now lives in {@link WandAllegianceService#onDefeat}.
 */
public final class WandDisarmAllegianceSystem {

    private WandDisarmAllegianceSystem() {
    }

    public static void onItemPickupPre(ItemEntityPickupEvent.Pre event) {
        if (!ModuleManager.isEnabled(Module.WANDS)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack stack = event.getItemEntity().getItem();
        if (!(stack.getItem() instanceof WandItem)) {
            return;
        }
        Optional<UUID> master = WandComponents.getMaster(stack);
        if (master.isEmpty() || master.get().equals(player.getUUID())) {
            return;
        }
        PlayerFeedback.toast(player, NoticeKind.WARN,
                Component.translatable("wandcraft.allegiance.title"),
                Component.translatable("wandcraft.allegiance.stolen_warning"));
    }
}
