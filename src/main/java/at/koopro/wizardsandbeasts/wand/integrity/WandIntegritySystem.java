package at.koopro.wizardsandbeasts.wand.integrity;

import at.koopro.wizardsandbeasts.wand.WandComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Integrity changes from overload / failed Reparo / master repair. Spell system calls these hooks.
 */
public final class WandIntegritySystem {

    private WandIntegritySystem() {
    }

    public static void onWandDamaged(ItemStack wand, float damageAmount) {
        float v = WandComponents.getIntegrity(wand) - damageAmount;
        wand.set(WandComponents.WAND_INTEGRITY.get(), Math.max(0.0f, v));
    }

    public static void onWandRepaired(ItemStack wand, boolean masterRepair) {
        if (masterRepair) {
            wand.set(WandComponents.WAND_INTEGRITY.get(), 1.0f);
            return;
        }
        float cur = WandComponents.getIntegrity(wand);
        wand.set(WandComponents.WAND_INTEGRITY.get(), Math.min(cur + 0.5f, 0.9f));
    }
}
