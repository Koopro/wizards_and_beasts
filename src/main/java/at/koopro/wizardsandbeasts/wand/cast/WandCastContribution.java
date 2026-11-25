package at.koopro.wizardsandbeasts.wand.cast;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.spell.cast.ModifierStack;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.wand.corruption.WandCorruptionSystem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;

/**
 * Everything a wand puts on a cast's {@link ModifierStack}, in one place.
 *
 * <p>The cast path and the wand tooltip both read from here. Before this the tooltip showed the
 * resolved {@link WandStats} while the cast also multiplied damage by the wand's condition (integrity,
 * corruption) in {@code SpellExecutor} — a worn wand cast weaker than its tooltip said. Now the
 * tooltip's "stability" row is {@link #stability} and the cast applies the same number through
 * {@link #applyTo}; {@code WandTooltipParityTests} holds the two together.
 */
public final class WandCastContribution {

    /** The provenance source the condition multiplier is recorded under. */
    public static final String CONDITION_SOURCE = "wand_corruption_integrity";

    private WandCastContribution() {}

    /**
     * Applies the wand's condition and its resolved stats to {@code stack}, exactly as a cast does.
     * Side effects of casting (corruption build-up) are not here; the caller owns those.
     */
    public static void applyTo(ModifierStack stack, ItemStack wandStack, WandStats stats, Spell spell,
                               RegistryAccess registries) {
        if (appliesCondition(wandStack)) {
            stack.multiplyDamage(condition(wandStack, spell.getCategory(), registries), CONDITION_SOURCE);
        }
        WandStatsResolver.applyToStack(stack, stats, spell);
    }

    /**
     * The wand's condition as a power multiplier for an ordinary (not Dark, not Defence) spell — what
     * the tooltip prints as stability. 1.0 for a whole, clean wand.
     */
    public static float stability(ItemStack wandStack, RegistryAccess registries) {
        return appliesCondition(wandStack) ? condition(wandStack, SpellCategory.UTILITY, registries) : 1.0f;
    }

    private static boolean appliesCondition(ItemStack wandStack) {
        return ModuleManager.isEnabled(Module.WANDS) && wandStack.getItem() instanceof WandItem;
    }

    private static float condition(ItemStack wandStack, SpellCategory category, RegistryAccess registries) {
        return WandCorruptionSystem.getEffectivePowerMultiplier(wandStack, category, registries);
    }
}
