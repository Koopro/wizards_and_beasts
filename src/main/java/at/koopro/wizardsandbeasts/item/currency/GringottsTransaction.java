package at.koopro.wizardsandbeasts.item.currency;

import at.koopro.wizardsandbeasts.registry.ModItems;
import net.minecraft.world.item.ItemStack;

public final class GringottsTransaction {
    private GringottsTransaction() {}

    public static boolean isAcceptedCoin(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(ModItems.COUNTERFEIT_GALLEON.get())) {
            return false;
        }
        return CurrencyHelper.isCanonicalCoin(stack);
    }
}
