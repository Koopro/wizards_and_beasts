package at.koopro.wizardsandbeasts.wand.elder;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.elder.ElderWandSavedData;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.UUID;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ElderWandEventHandler {

    private ElderWandEventHandler() {}

    /**
     * When an elder-wand item entity joins the world:
     * - First ever: assign a WAND_INSTANCE_ID and register it as the canonical instance.
     * - Subsequent: if the instance ID doesn't match the registered one, cancel the join
     *   (silently destroys the duplicate before it exists in the world).
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity ie)) return;
        ItemStack stack = ie.getItem();
        if (!ModDataComponents.isElderWand(stack)) return;

        ServerLevel level = (ServerLevel) event.getLevel();
        ElderWandSavedData data = ElderWandSavedData.get(level);

        UUID stackId = stack.get(ModDataComponents.WAND_INSTANCE_ID.get());
        if (!data.isRegistered()) {
            if (stackId == null) {
                stackId = UUID.randomUUID();
                stack.set(ModDataComponents.WAND_INSTANCE_ID.get(), stackId);
                ie.setItem(stack);
            }
            data.register(stackId);
        } else if (!data.getInstanceId().equals(stackId)) {
            event.setCanceled(true);
        }
    }

    /**
     * Every second per player:
     * 1. Destroy any elder wand in inventory whose instance ID doesn't match the
     *    registered canonical one (handles creative /give duplication).
     * 2. Register the first elder wand seen if none is registered yet.
     * 3. Bring the stack in line with the saved master, which is authoritative: the Elder Wand's allegiance
     *    moves on a defeat of its master wherever the wand happens to be.
     *
     * <p>This used to make whoever held a masterless Elder Wand its master — so crafting one, or picking one up,
     * was mastery. Nobody masters it that way now; it is won ({@code WandAllegianceService}).
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;

        ServerLevel level = (ServerLevel) player.level();
        ElderWandSavedData data = ElderWandSavedData.get(level);

        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!ModDataComponents.isElderWand(stack)) continue;

            UUID stackId = stack.get(ModDataComponents.WAND_INSTANCE_ID.get());

            if (!data.isRegistered()) {
                if (stackId == null) {
                    stackId = UUID.randomUUID();
                    stack.set(ModDataComponents.WAND_INSTANCE_ID.get(), stackId);
                }
                data.register(stackId);
            } else if (!data.getInstanceId().equals(stackId)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                player.displayClientMessage(
                        Component.literal("A counterfeit Elder Wand dissolved.").withStyle(ChatFormatting.DARK_RED),
                        true);
                continue;
            }

            WandAllegianceService.reconcileElderStack(stack, data.getMaster());
        }
    }

}
