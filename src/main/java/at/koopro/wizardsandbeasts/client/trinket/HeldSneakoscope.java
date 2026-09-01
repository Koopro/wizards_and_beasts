package at.koopro.wizardsandbeasts.client.trinket;

import at.koopro.wizardsandbeasts.item.trinket.SneakoscopeItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Finds the Sneakoscope a player is holding, if any.
 *
 * <p>Shared by the three client effects — the motes, the arrow and the alarm tint — so all three
 * agree on which hand is being read and, more importantly, on the fact that a Sneakoscope in the
 * pack is not a Sneakoscope in play.
 */
@NullMarked
public final class HeldSneakoscope {

    private HeldSneakoscope() {}

    /** The held Sneakoscope stack, main hand first, or {@code null}. */
    public static @Nullable ItemStack find(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof SneakoscopeItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof SneakoscopeItem ? off : null;
    }

    /** True when the stack that was found is the off-hand one, for placing the motes on the right side. */
    public static boolean isOffHand(Player player, ItemStack found) {
        return player.getMainHandItem() != found;
    }
}
