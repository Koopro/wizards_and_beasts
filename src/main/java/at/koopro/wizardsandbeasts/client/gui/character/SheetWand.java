package at.koopro.wizardsandbeasts.client.gui.character;

import at.koopro.wizardsandbeasts.util.WandHelper;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.UUID;

/**
 * Which wand the sheet describes: the one in hand, else the wand in the pack that answers to this wizard, else any
 * wand in the pack. The Attributes tab used to describe only a wand held in the main hand, so opening the sheet from
 * the inventory — where the hand is often empty — said "no wand" to a wizard who owned one.
 *
 * <p>Reads the stacks the server synced with the inventory; the components on them are the stored truth.
 */
@NullMarked
public final class SheetWand {

    /** Where the described wand was found. */
    public enum Where { HAND, PACK, NONE }

    public record Found(ItemStack stack, Where where) {
        public static final Found NONE = new Found(ItemStack.EMPTY, Where.NONE);

        public boolean present() {
            return where != Where.NONE;
        }
    }

    private SheetWand() {}

    public static Found find(Player player) {
        ItemStack held = WandHelper.getWandStack(player);
        if (!held.isEmpty()) {
            return new Found(held, Where.HAND);
        }
        UUID self = player.getUUID();
        Inventory inventory = player.getInventory();
        ItemStack anyWand = ItemStack.EMPTY;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !WandHelper.isWand(stack)) {
                continue;
            }
            Optional<UUID> master = WandComponents.getMaster(stack);
            if (master.isPresent() && master.get().equals(self)) {
                return new Found(stack, Where.PACK);
            }
            if (anyWand.isEmpty()) {
                anyWand = stack;
            }
        }
        return anyWand.isEmpty() ? Found.NONE : new Found(anyWand, Where.PACK);
    }
}
