package at.koopro.wizardsandbeasts.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * Hands a stack to a player: what fits goes into the inventory, the rest drops at their feet.
 *
 * <p>{@code Inventory.add} returns {@code true} when it inserted <em>anything</em> and leaves the remainder in the
 * stack, so the familiar {@code if (!inventory.add(stack)) player.drop(stack)} destroys whatever did not fit on a
 * partial insert. For coins that is money destroyed (a counter withdrawal into a nearly full pocket used to lose the
 * overflow after the vault was debited). Use this instead.
 */
@NullMarked
public final class InventoryHandover {

    private InventoryHandover() {}

    public static void giveOrDrop(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
    }
}
