package at.koopro.wizardsandbeasts.wand.visual;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The metal a wand's fittings are made of, read off how rare its wood is: an ordinary wand is bound in
 * pewter and leather, a rare wood earns silver, and a legendary wood -- or one of a wood's rare
 * constructions, the old ornamented wands -- is gilt. The renderer draws the matching trim sheet
 * ({@code tools/wand_metal.py}); the silver and gold sheets add a pommel cap and a ferrule to every
 * plain handle, so the metal shows even on a wand with no bands of its own.
 */
@NullMarked
public enum WandMetal {
    PEWTER,
    SILVER,
    GOLD;

    /**
     * @param rarity    the wood's {@code rarity} as written in its datapack file, or null for an unknown wood
     * @param rareIndex which of the wood's rare constructions the wand is, or -1
     */
    public static WandMetal of(@Nullable String rarity, int rareIndex) {
        if (rareIndex >= 0 || "legendary".equals(rarity)) {
            return GOLD;
        }
        return "rare".equals(rarity) ? SILVER : PEWTER;
    }
}
