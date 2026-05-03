package at.koopro.wizardsandbeasts.brew;

import net.minecraft.util.StringRepresentable;

/**
 * Quality tier of a brewing cauldron. Higher tiers may unlock more advanced
 * recipes and apply potency multipliers in future iterations; for now the
 * tier is a {@link BrewingRecipe#cauldronTier() recipe-side requirement} so
 * recipes can demand "pewter or better" without changing item shapes.
 *
 * <p>Order is meaningful: {@link #ordinal()} is used by
 * {@link #isAtLeast(CauldronTier)} so adding a tier below {@link #BRASS} would
 * be a breaking change.
 */
public enum CauldronTier implements StringRepresentable {
    BRASS,
    COPPER,
    PEWTER;

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }

    public boolean isAtLeast(CauldronTier required) {
        return this.ordinal() >= required.ordinal();
    }
}
