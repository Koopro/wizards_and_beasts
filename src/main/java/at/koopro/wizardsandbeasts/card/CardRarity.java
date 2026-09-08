package at.koopro.wizardsandbeasts.card;

import net.minecraft.ChatFormatting;
import org.jspecify.annotations.NullMarked;

/**
 * How hard a Famous Wizard Card is to pull, and what that looks like.
 *
 * <p>The weight is the whole point of the tier. A collection is only worth collecting if some of
 * it resists you, and the books agree — Ron has six Dumbledores and no Agrippa. So the tiers are
 * deliberately lopsided: a Dumbledore is twelve times as likely as an Andros, and the two
 * legendary cards together account for roughly one pull in eighty.
 *
 * <p>The colour is used twice over: it tints the stack's name in the inventory, and
 * {@code tools/wizard_cards.py} paints the same value into the card's foil frame, so a card you
 * can see on the ground already tells you what tier it is.
 */
@NullMarked
public enum CardRarity {

    COMMON(12, ChatFormatting.GRAY),
    UNCOMMON(7, ChatFormatting.GREEN),
    RARE(3, ChatFormatting.AQUA),
    LEGENDARY(1, ChatFormatting.GOLD);

    private final int weight;
    private final ChatFormatting colour;

    CardRarity(int weight, ChatFormatting colour) {
        this.weight = weight;
        this.colour = colour;
    }

    /** Relative draw weight against every other card in the deck, not against the other tiers. */
    public int weight() {
        return weight;
    }

    public ChatFormatting colour() {
        return colour;
    }

    /** Translation key for the tier's own name, e.g. {@code ...card.rarity.rare}. */
    public String translationKey() {
        return "item.wizards_and_beasts.famous_wizard_card.rarity." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
