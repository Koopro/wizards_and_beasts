package at.koopro.wizardsandbeasts.wand.elder;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.elder.ElderWandSavedData;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import javax.annotation.Nullable;
import java.util.Optional;
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
     * When the current elder wand master dies:
     * - Killed by a player → that player becomes the new master.
     * - Killed by anything else → master cleared; wand up for grabs again.
     *
     * Updates both SavedData and the WAND_MASTER component on the wand stack in the
     * victim's inventory (which propagates into the death drops before they spawn).
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;

        ServerLevel level = (ServerLevel) victim.level();
        ElderWandSavedData data = ElderWandSavedData.get(level);
        if (data.getMaster() == null || !data.getMaster().equals(victim.getUUID())) return;

        Entity killer = event.getSource().getEntity();
        UUID newMaster = (killer instanceof ServerPlayer killerPlayer) ? killerPlayer.getUUID() : null;
        data.setMaster(newMaster);

        int size = victim.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = victim.getInventory().getItem(i);
            if (!ModDataComponents.isElderWand(stack)) continue;
            if (newMaster != null) {
                stack.set(WandComponents.WAND_MASTER.get(), Optional.of(newMaster));
            } else {
                stack.remove(WandComponents.WAND_MASTER.get());
            }
            break;
        }
    }

    /**
     * Every second per player:
     * 1. Destroy any elder wand in inventory whose instance ID doesn't match the
     *    registered canonical one (handles creative /give duplication).
     * 2. Register the first elder wand seen if none is registered yet.
     * 3. First-claim: if the registered wand has no master and the player holds it,
     *    the player becomes master.
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

            if (data.getMaster() == null && WandComponents.getMaster(stack).isEmpty()) {
                data.setMaster(player.getUUID());
                stack.set(WandComponents.WAND_MASTER.get(), Optional.of(player.getUUID()));
                player.displayClientMessage(
                        Component.literal("The Elder Wand has chosen you.").withStyle(ChatFormatting.GOLD),
                        false);
            }
        }
    }

    /** Called from WandDisarmAllegianceSystem when WAND_MASTER changes on an elder wand. */
    public static void onElderWandMasterChanged(ServerLevel level, @Nullable UUID newMaster) {
        ElderWandSavedData.get(level).setMaster(newMaster);
    }
}
