package at.koopro.wizardsandbeasts.currency.dragot;

import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * Counting and spending Dragots, keeping the good ones and the bad ones apart.
 *
 * <p>{@code CurrencyHelper} cannot do this job: it counts by item, and every Dragot is the same item.
 * What separates a devalued coin from a sound one is a data component, so the whole purse has to be
 * walked stack by stack.
 *
 * <p><b>Bad money goes first.</b> {@link #take} spends devalued coins before good ones, which is what
 * a person passing dud currency would actually do and is also what makes the 20% notice roll matter:
 * a wizard holding one bad Dragot in twenty meets that roll on their very next purchase rather than
 * twenty purchases later.
 */
@NullMarked
public final class DragotPurse {

    private DragotPurse() {}

    /** Whether a stack is a Dragot that somebody has shaved, plated or otherwise ruined. */
    public static boolean isDevalued(ItemStack stack) {
        return stack.is(CurrencyItemRegistry.DRAGOT.get())
                && Boolean.TRUE.equals(stack.get(ModDataComponents.DRAGOT_DEVALUED.get()));
    }

    /** Whether a stack is a Dragot at all, sound or not. */
    public static boolean isDragot(ItemStack stack) {
        return stack.is(CurrencyItemRegistry.DRAGOT.get());
    }

    /** Marks a stack devalued. Returns the same stack for chaining. */
    public static ItemStack devalue(ItemStack stack) {
        if (isDragot(stack)) {
            stack.set(ModDataComponents.DRAGOT_DEVALUED.get(), true);
        }
        return stack;
    }

    /** Every Dragot the player is carrying, sound or not. */
    public static int total(Player player) {
        return count(player.getInventory(), Counted.ANY);
    }

    /** Only the sound ones. */
    public static int sound(Player player) {
        return count(player.getInventory(), Counted.SOUND);
    }

    /** Only the bad ones. */
    public static int devalued(Player player) {
        return count(player.getInventory(), Counted.DEVALUED);
    }

    private enum Counted { ANY, SOUND, DEVALUED }

    private static int count(Inventory inventory, Counted which) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isDragot(stack)) {
                continue;
            }
            boolean bad = isDevalued(stack);
            if (which == Counted.ANY || (which == Counted.DEVALUED) == bad) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Removes {@code amount} Dragots, worst first.
     *
     * @return how many of the coins taken were devalued, or {@code -1} if the player did not have
     *         enough and nothing was taken
     */
    public static int take(Player player, int amount) {
        if (amount <= 0) {
            return 0;
        }
        if (total(player) < amount) {
            return -1;
        }
        Inventory inventory = player.getInventory();
        int remaining = amount;
        int badTaken = 0;

        // Two passes rather than one: the devalued coins must all be gone before a sound one is
        // touched, and a single pass would spend whichever came first in slot order.
        for (boolean devaluedPass : new boolean[] {true, false}) {
            for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!isDragot(stack) || isDevalued(stack) != devaluedPass) {
                    continue;
                }
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (devaluedPass) {
                    badTaken += take;
                }
            }
        }
        return badTaken;
    }

    /** Gives the player {@code amount} sound Dragots, dropping any that do not fit. */
    public static void give(Player player, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int batch = Math.min(remaining, CurrencyItemRegistry.DRAGOT.get().getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(CurrencyItemRegistry.DRAGOT.get(), batch);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= batch;
        }
    }

    /** Whether either hand is presenting a Dragot — the gesture that counts as offering to pay in them. */
    public static boolean isOffering(Player player) {
        return isDragot(player.getMainHandItem()) || isDragot(player.getOffhandItem());
    }
}
