package at.koopro.wizardsandbeasts.util;

import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;

import java.util.Optional;
import java.util.UUID;

/**
 * Wand-related utility methods.
 */
public final class WandHelper {

    private WandHelper() {}

    /**
     * Checks if the player is holding a wand in either hand.
     */
    public static boolean isHoldingWand(Player player) {
        return isWand(player.getMainHandItem()) || isWand(player.getOffhandItem());
    }

    /**
     * Checks if the given stack is a wand.
     */
    public static boolean isWand(ItemStack stack) {
        return isWand(stack.getItem());
    }

    /**
     * Checks if the given item is a wand.
     */
    public static boolean isWand(Item item) {
        return item instanceof WandItem;
    }

    /**
     * Gets the wand ItemStack from whichever hand holds it.
     * Returns the main hand wand if both hands hold wands.
     * Returns {@link ItemStack#EMPTY} if no wand is held.
     */
    public static ItemStack getWandStack(Player player) {
        if (isWand(player.getMainHandItem())) {
            return player.getMainHandItem();
        }
        if (isWand(player.getOffhandItem())) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    /**
     * True when the player is actively using a wand (right-click hold).
     */
    public static boolean isUsingWand(Player player) {
        return player.isUsingItem() && isWand(player.getUseItem());
    }

    /** True when this wand stack lists the player as bonded master (post-resonance match). */
    public static boolean isWandBondedTo(Player player, ItemStack wand) {
        if (wand.isEmpty() || !isWand(wand)) {
            return false;
        }
        Optional<UUID> master = WandComponents.getMaster(wand);
        return master.isPresent() && master.get().equals(player.getUUID());
    }
}
