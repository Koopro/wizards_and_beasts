package at.koopro.wizardsandbeasts.event.module;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleContentIndex;
import at.koopro.wizardsandbeasts.module.ModuleIds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.TriState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Makes content whose module is switched off do nothing when it is used.
 *
 * <p>Three interaction handlers rather than a check in each of ~200 item and block classes. Every use of
 * an item, every interaction with a block and every interaction with an entity passes through these
 * events, so one guard each covers content that does not exist yet and content a datapack has attached to
 * a module we never wrote a class for. The alternative — editing several hundred classes — would be a
 * check someone has to remember on every new item, which is exactly the failure mode the module tags
 * exist to remove.
 *
 * <p><b>Nothing is ever taken away.</b> A blocked interaction is cancelled, not destructive: the item
 * stays in the inventory, the block stays in the world, and re-enabling the module restores both
 * immediately. Left-clicking is deliberately not gated, so a player can always break and pick up a block
 * belonging to a module an operator has since switched off.
 *
 * <p>Entity AI is not suppressed here. The two modules that own mobs — {@code CREATURES} and
 * {@code AZKABAN} — already go inert through the per-entity guard {@code DementorEntity} and
 * {@code GenericBeastEntity} use, which keeps physics running while stopping behaviour. A blanket tick
 * cancel would freeze already-spawned mobs in mid-air, which is worse for a world than what it fixes.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ModuleContentGate {

    private ModuleContentGate() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (blockedItem(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        // Covers spawn eggs used on a mob, and any item whose whole purpose is a right-click on something
        // living. The target entity is not consulted: an existing creature stays interactive so a player
        // can still lead it, name it or move it out of the way.
        if (blockedItem(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (blockedItem(player, event.getItemStack())) {
            event.setCanceled(true);
            return;
        }

        BlockState state = event.getLevel().getBlockState(event.getPos());
        Module owner = ModuleContentIndex.moduleOf(state.getBlock());
        if (owner != null && !ModuleContentIndex.accessible(owner)) {
            // Deny only the block's own behaviour. Placing another item against it still works, so a
            // switched-off block does not become an obstacle you cannot build around.
            event.setUseBlock(TriState.FALSE);
            explain(player, owner);
        }
    }

    /**
     * Stops new natural spawns of a switched-off module's mobs.
     *
     * <p>{@link FinalizeSpawnEvent} rather than {@code EntityJoinLevelEvent}: this one fires for spawns
     * being created, while the join event also fires for entities being loaded from a chunk. Cancelling
     * there would delete creatures already living in the world.
     */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!ModuleContentIndex.isAccessible(event.getEntity().getType())) {
            event.setSpawnCancelled(true);
        }
    }

    private static boolean blockedItem(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Module owner = ModuleContentIndex.moduleOf(stack.getItem());
        if (owner == null || ModuleContentIndex.accessible(owner)) {
            return false;
        }
        explain(player, owner);
        return true;
    }

    /**
     * Says why nothing happened. An item that silently does nothing reads as a bug; the module name is
     * what an operator needs to hear to fix it.
     */
    private static void explain(@Nullable Player player, Module module) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.wizards_and_beasts.module.content_disabled",
                            ModuleIds.displayName(module)),
                    true);
        }
    }
}
