package at.koopro.wizardsandbeasts.brew;

import net.minecraft.util.StringRepresentable;

/**
 * Quality tier of a brewing cauldron: what a pot is made of, and therefore what it can hold.
 *
 * <h2>The ladder, and why it was flipped</h2>
 * <p>It used to run {@code BRASS, COPPER, PEWTER} with pewter at the top. That is backwards. A
 * pewter cauldron is the <em>student</em> cauldron — it is the first line on the Hogwarts first-year
 * equipment list ("1 standard size 2 pewter cauldron"), the cheapest thing in the shop, the one
 * every eleven-year-old owns. Brass and copper are what you buy when you can afford better.
 *
 * <p>Flipped here rather than later because {@link #ordinal()} is the comparison
 * ({@link #isAtLeast}), so the order <b>is</b> the gate. Doing it once, with the shipped recipe JSONs
 * remapped in the same change, avoids a second migration where every recipe would have to move again.
 * The remap preserved each recipe's difficulty <em>rank</em> rather than its tier name: what used to
 * demand "the top cauldron" still demands the top cauldron, which is now copper.
 *
 * <p>Silver sits above copper when it lands, which is the other reason the ladder had to be the right
 * way up first — appending to the top of a correct ladder is free, and appending to the top of an
 * inverted one would have meant silver being the weakest cauldron in the game.
 */
public enum CauldronTier implements StringRepresentable {

    /** The student cauldron. Standard size 2. Everything basic brews in one. */
    PEWTER,

    /** A step up, and the first pot that will hold anything temperamental. */
    BRASS,

    /** Serious equipment. The top of the ladder until silver lands. */
    COPPER;

    public static final com.mojang.serialization.Codec<CauldronTier> CODEC =
            StringRepresentable.fromEnum(CauldronTier::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean isAtLeast(CauldronTier required) {
        return this.ordinal() >= required.ordinal();
    }

    /**
     * Complexity weight of a successful brew in this pot — feeds the Potions OWL grade.
     *
     * <p>Derived from the ladder position rather than written out per constant, so a new tier cannot
     * be added without a weight and the two can never disagree about which pot is harder.
     */
    public int brewPoints() {
        return ordinal() + 1;
    }
}
