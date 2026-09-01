package at.koopro.wizardsandbeasts.bertiebotts;

import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Drawing a bean out of the bag.
 *
 * <p>Two rolls, not one: the tier first, then a flavour inside it. That is what makes the bag feel
 * like a bag rather than a coin — how *good or bad* a bean is has fixed odds, and which particular
 * flavour delivers that is a separate surprise. It also means the table extends without rebalancing:
 * adding a fourth unpleasant flavour makes each unpleasant flavour rarer and leaves unpleasantness
 * itself at thirty-five percent.
 *
 * <p>Both rolls are taken as parameters where it matters, so the distribution can be pinned by a test
 * rather than described in a comment.
 */
@NullMarked
public final class BertieBotts {

    /** Hunger and saturation. The same for every bean: the joke is the flavour, not the nutrition. */
    public static final int NUTRITION = 1;
    public static final float SATURATION = 0.15f;

    /** Ticks before the next bean. Short — this exists to stop a double-click eating two. */
    public static final int COOLDOWN_TICKS = 8;

    private BertieBotts() {}

    /** One bean. */
    public static BeanFlavour draw(RandomSource random) {
        BeanTier tier = BeanTier.byRoll(random.nextInt(BeanTier.TOTAL_WEIGHT));
        return drawFrom(tier, random.nextInt(Integer.MAX_VALUE));
    }

    /**
     * A flavour from one tier, chosen by {@code index} modulo however many that tier holds.
     *
     * <p>Separated out so a test can walk every tier without depending on how many flavours each one
     * currently has.
     */
    public static BeanFlavour drawFrom(BeanTier tier, int index) {
        List<BeanFlavour> flavours = BeanFlavour.inTier(tier);
        if (flavours.isEmpty()) {
            // A tier with no flavours would be a table nobody could see was broken. Fall back to the
            // dullest possible bean rather than throwing in the middle of somebody's snack.
            return BeanFlavour.HONEY;
        }
        return flavours.get(Math.floorMod(index, flavours.size()));
    }
}
