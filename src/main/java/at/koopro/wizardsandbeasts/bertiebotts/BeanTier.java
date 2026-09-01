package at.koopro.wizardsandbeasts.bertiebotts;

import net.minecraft.ChatFormatting;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * How lucky a bean was, and what colour the joke lands in.
 *
 * <p>Weights are declared here rather than on the individual flavours so the shape of the bag is
 * readable in one place: three quarters of every bag is ordinary, a quarter is memorable, and one
 * bean in fifty is a story. Flavours are drawn <em>within</em> a tier, so adding a fourth unpleasant
 * flavour makes each unpleasant flavour rarer without making unpleasantness itself rarer — which is
 * the property a fifty-fifty coin flip could never have.
 */
@NullMarked
public enum BeanTier implements StringRepresentable {

    /** Saturation, a burst of speed, a scrap of healing. */
    COMMON_PLEASANT(40, ChatFormatting.GREEN),
    /** Earwax. Vomit. Dirt. */
    COMMON_UNPLEASANT(35, ChatFormatting.YELLOW),
    /** Worth hoping for. */
    RARE_GOOD(15, ChatFormatting.AQUA),
    /** Worth fearing. */
    RARE_BAD(8, ChatFormatting.RED),
    /** One in fifty. Chocolate, or a bogey. */
    LEGENDARY(2, ChatFormatting.LIGHT_PURPLE);

    /** Total weight across every tier — 100, so each weight reads directly as a percentage. */
    public static final int TOTAL_WEIGHT = 100;

    private final int weight;
    private final ChatFormatting colour;

    BeanTier(int weight, ChatFormatting colour) {
        this.weight = weight;
        this.colour = colour;
    }

    public int weight() {
        return weight;
    }

    /** How the flavour's name is coloured when it is announced. */
    public ChatFormatting colour() {
        return colour;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The tier a roll in {@code [0, TOTAL_WEIGHT)} lands in.
     *
     * <p>Declaration order is the draw order, so the cumulative bands are exactly the percentages
     * above and a reader can check the table by eye.
     */
    public static BeanTier byRoll(int roll) {
        int cursor = 0;
        for (BeanTier tier : values()) {
            cursor += tier.weight;
            if (roll < cursor) {
                return tier;
            }
        }
        return COMMON_PLEASANT;
    }
}
