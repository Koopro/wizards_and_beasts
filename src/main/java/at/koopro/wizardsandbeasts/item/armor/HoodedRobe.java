package at.koopro.wizardsandbeasts.item.armor;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import org.jspecify.annotations.NullMarked;

/**
 * Marker for a robe whose model carries both a hood-up and a hood-down bone.
 *
 * <p>A set is three items off one model — chest, legs, boots — and only the chest one wears the
 * hood, so the interface is implemented by the item class and narrowed by slot in
 * {@link #hasHood(ItemStack)} rather than by having a separate hooded item.
 *
 * <p>The two bone names are declared here because they are a contract between three places that
 * cannot see each other: the generator that writes them into the {@code .geo.json}
 * ({@code tools/armor_model.py}), the renderer that skips one of them per frame, and this item.
 * A rename in any one of them is silent — GeckoLib drops an unknown bone without complaint.
 */
@NullMarked
public interface HoodedRobe {

    /** Bone holding the hood as worn up, over the head. */
    String HOOD_UP_BONE = "robeHood";

    /** Bone holding the same hood bunched on the shoulders. */
    String HOOD_DOWN_BONE = "robeHoodDown";

    /** The slot this piece occupies; supplied by {@link WizardArmorItem}. */
    ArmorType armorType();

    /** Only the chest piece of a set has the hood on it. */
    default boolean hasHood(ItemStack stack) {
        return armorType() == ArmorType.CHESTPLATE;
    }
}
