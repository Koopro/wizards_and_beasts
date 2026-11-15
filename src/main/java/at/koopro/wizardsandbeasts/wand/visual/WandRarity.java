package at.koopro.wizardsandbeasts.wand.visual;

import net.minecraft.world.item.Rarity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A wand's name colour, read off its wood the way the rest of its look is: an ordinary wood is white, an
 * uncommon one yellow, a rare one aqua, a legendary one -- and the Elder Wand -- the colour of the Hallows.
 * One of a wood's rare constructions (the old ornamented wands) is never shown below rare.
 */
@NullMarked
public final class WandRarity {

    private WandRarity() {}

    /**
     * @param woodRarity the wood's {@code rarity} as its datapack file writes it, or null for an unknown wood
     * @param rareIndex  which of the wood's rare constructions the wand is, or -1
     * @param elder      the Elder Wand
     */
    public static Rarity of(@Nullable String woodRarity, int rareIndex, boolean elder) {
        if (elder) {
            return Rarity.EPIC;
        }
        Rarity base = woodRarity == null ? Rarity.COMMON : switch (woodRarity) {
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "legendary" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
        return rareIndex >= 0 && base.ordinal() < Rarity.RARE.ordinal() ? Rarity.RARE : base;
    }
}
