package at.koopro.wizardsandbeasts.wand.making;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * One wood married to one core: how hard the pairing is to make, the lengths it is turned to, and how well the two
 * suit each other. {@link WandmakingRules} derives it from the wood's and the core's definitions, so every wood pairs
 * with every core.
 *
 * <p>Until 2026-11-26 this was {@code WandmakingRecipe}, loaded from one JSON file per pair (100 of them). Each file's
 * difficulty and length were a per-wood value plus a per-core value. Those values moved onto the definitions unchanged,
 * and the pairs became a rule.
 *
 * @param difficulty what the bench and the maker are measured against. Until the remade bench lands, the old bench
 *                   uses it as its tier requirement, with the same numbers.
 * @param affinity   {@link CoreMaking#affinityWith}: above 0 the wood and core suit each other, below 0 they fight
 */
@NullMarked
public record WandmakingSpec(
        Identifier woodKey,
        Identifier coreKey,
        float difficulty,
        float lengthMin,
        float lengthMax,
        int affinity) {

    /** A clean wand, at full integrity. A flawed making passes less. */
    public ItemStack createWand(@Nullable WandFlexibility flexibility, float lengthInches) {
        return createWand(flexibility, lengthInches, 1.0f);
    }

    /**
     * The wand this pair makes. This is the only builder. The bench, Ollivander's commissions, the admin tools, JEI
     * and the look previews all use it, so none of them can drift. (JEI once built its own and set no flexibility and
     * no elder-wand marker.)
     *
     * @param flexibility the maker's choice, or {@code null} for a display stack that claims none
     * @param lengthInches the maker's choice or {@link #rollLength}; {@link #lengthMin} for a display stack
     */
    public ItemStack createWand(@Nullable WandFlexibility flexibility, float lengthInches, float integrity) {
        ItemStack wand = new ItemStack(WandItemRegistry.WAND.get());
        wand.set(WandComponents.WAND_WOOD.get(), woodKey);
        wand.set(WandComponents.WAND_CORE.get(), coreKey);
        if (flexibility != null) {
            wand.set(WandComponents.WAND_FLEXIBILITY.get(), flexibility);
        }
        wand.set(WandComponents.WAND_LENGTH.get(), lengthInches);
        wand.set(WandComponents.WAND_INTEGRITY.get(), integrity);
        wand.set(WandComponents.WAND_MASTER.get(), Optional.empty());
        ModDataComponents.refreshElderWandMarker(wand);
        return wand;
    }

    /** A random length within this pair's range. */
    public float rollLength(RandomSource random) {
        return lengthMin + random.nextFloat() * (lengthMax - lengthMin);
    }
}
