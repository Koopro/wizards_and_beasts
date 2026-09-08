package at.koopro.wizardsandbeasts.item.armor;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * The hood up/down state, and the one place that decides which stack a toggle applies to.
 *
 * <p>State lives on the stack rather than on the player: a robe carries its own hood, so it stays
 * where you left it across a wardrobe change, and it syncs to other clients for free because the
 * component is {@code networkSynchronized} — the wearer is not the only one who has to see it.
 */
@NullMarked
public final class RobeHood {

    private RobeHood() {}

    /** True when this stack is a robe with a hood on it — the chest piece of a hooded set. */
    public static boolean hasHood(ItemStack stack) {
        return stack.getItem() instanceof HoodedRobe robe && robe.hasHood(stack);
    }

    /** Absent means down: a robe from {@code /give} starts in the state the wearer can see. */
    public static boolean isUp(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModDataComponents.HOOD_UP.get()));
    }

    public static void setUp(ItemStack stack, boolean up) {
        if (up) {
            stack.set(ModDataComponents.HOOD_UP.get(), true);
        } else {
            // Removed rather than set to false, so a hood-down robe stacks with a fresh one and
            // carries no component at all — the same reason the getter treats absent as down.
            stack.remove(ModDataComponents.HOOD_UP.get());
        }
    }

    /**
     * The stack a hood toggle should act on: the worn chest piece, or a robe held in hand when
     * none is worn (which is how you set a hood before putting the robe on).
     *
     * @return the stack, or {@link ItemStack#EMPTY} if the player has no hooded robe to toggle
     */
    public static ItemStack toggleTarget(Player player) {
        ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);

        if (hasHood(worn)) {
            return worn;
        }

        for (ItemStack held : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
            if (hasHood(held)) {
                return held;
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * Flip the hood on whatever {@link #toggleTarget} finds.
     *
     * @return the new state, or {@code null} if there was nothing to toggle
     */
    public static Boolean toggle(Player player) {
        ItemStack stack = toggleTarget(player);

        if (stack.isEmpty()) {
            return null;
        }

        boolean up = !isUp(stack);
        setUp(stack, up);

        return up;
    }
}
