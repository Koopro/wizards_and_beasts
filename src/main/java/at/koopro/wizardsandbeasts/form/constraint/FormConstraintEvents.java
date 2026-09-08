package at.koopro.wizardsandbeasts.form.constraint;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The one wall. Every {@link FormConstraint} except {@link FormConstraint#NO_SPELLCASTING} and
 * {@link FormConstraint#NO_VOLUNTARY_EXIT} is enforced here and nowhere else.
 *
 * <p>Those two are enforced elsewhere because they are not interactions: casting is refused at the
 * network edge in {@code SpellNetworkGuards.canUseWand}, the single guard every wand packet passes, and
 * a voluntary exit is refused inside whichever service offers one.
 *
 * <p>This replaced three separate near-identical walls — one in {@code AnimagusEvents}, one in
 * {@code WerewolfControlHandler}, and a third still standing in {@code ObscurialHeritageHandler}. They
 * had already drifted apart; a fourth transformed state would have drifted further.
 *
 * <p><b>Everything here is server-side.</b> The events fire on both logical sides, and
 * {@link FormConstraints#denies(Object, FormConstraint)} answers false for anything that is not a
 * {@link ServerPlayer}, so a client's own copy of an interaction is never what decides.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class FormConstraintEvents {

    private FormConstraintEvents() {}

    // ── hands ──────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (FormConstraints.denies(event.getEntity(), FormConstraint.NO_ITEM_USE)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** A use can begin without an interact event — eating from a full-block hitbox, for one. */
    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (FormConstraints.denies(event.getEntity(), FormConstraint.NO_ITEM_USE)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (FormConstraints.denies(event.getEntity(), FormConstraint.NO_BLOCK_INTERACT)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** {@code LeftClickBlock} carries no cancellation result, unlike its siblings. */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (FormConstraints.denies(event.getEntity(), FormConstraint.NO_BLOCK_BREAK)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (FormConstraints.denies(event.getEntity(), FormConstraint.NO_ENTITY_INTERACT)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (FormConstraints.denies(event.getEntity(), FormConstraint.NO_ENTITY_INTERACT)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    // ── pockets ────────────────────────────────────────────────────────

    /**
     * Cancelling {@link ItemTossEvent} alone <em>destroys</em> the stack — the event's own javadoc says
     * so, because the item has already left the inventory by the time it fires. So the stack goes back
     * first and the cancel is conditional on that working. A full inventory lets the toss through, which
     * is the one outcome that cannot lose anybody's items.
     */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!FormConstraints.denies(player, FormConstraint.NO_ITEM_DROP)) {
            return;
        }
        if (player.getInventory().add(event.getEntity().getItem().copy())) {
            event.setCanceled(true);
            player.containerMenu.broadcastChanges();
        }
    }

    // ── screens ────────────────────────────────────────────────────────

    /**
     * Holds every container shut.
     *
     * <p>A tick loop rather than a cancel because {@code PlayerContainerEvent.Open} is not cancellable.
     * The client refuses to open one too, but that is smoothing — this is what makes the rule true for a
     * client that sends the packet anyway, or for a screen opened on the player's behalf by a block.
     * {@code inventoryMenu} is the always-present player menu, so comparing against it is how you ask
     * "is anything actually open".
     *
     * <p>Also stops any use in progress, which covers the frame between a use starting and the next
     * {@code Start} event.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            FormConstraintSet constraints = FormConstraints.of(player);
            if (constraints.isEmpty()) {
                continue;
            }
            if (constraints.denies(FormConstraint.NO_INVENTORY)
                    && player.containerMenu != player.inventoryMenu) {
                player.closeContainer();
            }
            if (constraints.denies(FormConstraint.NO_ITEM_USE) && player.isUsingItem()) {
                player.stopUsingItem();
            }
        }
    }
}
