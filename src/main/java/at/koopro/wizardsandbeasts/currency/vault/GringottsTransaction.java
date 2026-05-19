package at.koopro.wizardsandbeasts.currency.vault;

import net.minecraft.world.item.ItemStack;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;

public final class GringottsTransaction {
    private GringottsTransaction() {}

    public static boolean isAcceptedCoin(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(CurrencyItemRegistry.COUNTERFEIT_GALLEON.get())) {
            return false;
        }
        return CurrencyHelper.isCanonicalCoin(stack);
    }
}
