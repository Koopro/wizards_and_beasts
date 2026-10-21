package at.koopro.wizardsandbeasts.broom.fitting;

import at.koopro.wizardsandbeasts.broom.BroomTier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Something a broom can be fitted with to change how it flies.
 *
 * <p>A closed set in code rather than a datapack folder, on purpose. Each fitting is an item, and items are
 * registered in code; and the numbers have to be identical on the rider's client (which simulates the flight) and
 * on the server (which checks it with {@code BroomSpeedGuard}). A datapack registry would need its own sync to the
 * client, and the broom definitions are the standing proof that forgetting that sync breaks every broom on a
 * dedicated server while working perfectly in singleplayer.
 *
 * <p><b>Every fitting is a trade.</b> Each one moves its characteristic by a bounded amount and costs something
 * else, so fitting a broom is choosing what kind of broom it becomes, not making it bigger. The canonical names
 * come from the books: the Cushioning Charm (<i>Quidditch Through the Ages</i> ch. 9, Elliot Smethwyck, 1820), the
 * Firebolt's "unbreakable Braking Charm" and its Anti-Jinx varnish (<i>Prisoner of Azkaban</i> ch. 4), and Harry's
 * servicing kit with its "silver tail-twig clippers" (ch. 1).
 */
@NullMarked
public enum BroomFitting {
    /** Softens a crash; the cushion is a little dead weight off the line. */
    CUSHIONING_CHARM(FittingSlot.CHARM, BroomTier.SCHOOL,
            mods(FittingStat.CRASH, 0.60f, FittingStat.ACCELERATION, 0.95f)),
    /** Stops on a Knut; the charm bleeds the boost a little sooner. */
    BRAKING_CHARM(FittingSlot.CHARM, BroomTier.STANDARD,
            mods(FittingStat.DECELERATION, 1.60f, FittingStat.BOOST_DURATION, 0.85f)),
    /** Clipped, swept twigs: faster, and less willing to hold a heading. */
    STREAMLINED_TWIGS(FittingSlot.TWIGS, BroomTier.STANDARD,
            mods(FittingStat.MAX_SPEED, 1.06f, FittingStat.DRIFT, 1.35f, FittingStat.STABILITY, 0.92f)),
    /** A tail trimmed for turning rather than running. */
    BALANCED_TAIL(FittingSlot.TWIGS, BroomTier.SCHOOL,
            mods(FittingStat.TURN, 1.10f, FittingStat.HANDLING, 1.08f, FittingStat.MAX_SPEED, 0.97f)),
    /** Iron-bound bristles shrug off knocks; the extra weight is slow to get going. */
    REINFORCED_BINDING(FittingSlot.BINDING, BroomTier.SCHOOL,
            mods(FittingStat.WEAR, 0.50f, FittingStat.ACCELERATION, 0.94f)),
    /** The Firebolt's finish: steady as a rail, at a whisker of its kick. */
    ANTI_JINX_VARNISH(FittingSlot.VARNISH, BroomTier.RACING,
            mods(FittingStat.DRIFT, 0.55f, FittingStat.WOBBLE, 0.50f, FittingStat.STABILITY, 1.08f,
                    FittingStat.BOOST, 0.90f));

    /**
     * The most a full set of fittings may raise top speed. One fitting raises it today, by 6 %; this is the line a
     * future one cannot cross without a deliberate decision, because top speed is the number the speed guard and
     * every race par are built on.
     */
    public static final float MAX_SPEED_GAIN = 1.10f;

    private final FittingSlot slot;
    private final BroomTier minTier;
    private final Map<FittingStat, Float> modifiers;

    BroomFitting(FittingSlot slot, BroomTier minTier, Map<FittingStat, Float> modifiers) {
        this.slot = slot;
        this.minTier = minTier;
        this.modifiers = modifiers;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public FittingSlot slot() {
        return slot;
    }

    /** The least broom tier that takes this fitting: a school broom's bundle will not hold racing twigs. */
    public BroomTier minTier() {
        return minTier;
    }

    /** What it does, characteristic by characteristic, as multipliers. */
    public Map<FittingStat, Float> modifiers() {
        return modifiers;
    }

    public String translationKey() {
        return "broom_fitting.wizards_and_beasts." + id();
    }

    /** Whether a broom of {@code tier} can take this fitting at all. */
    public boolean fits(BroomTier tier) {
        return tier.sortIndex() >= minTier.sortIndex();
    }

    public static @Nullable BroomFitting byId(String id) {
        for (BroomFitting fitting : values()) {
            if (fitting.id().equals(id)) {
                return fitting;
            }
        }
        return null;
    }

    /**
     * How many fittings a broom of {@code tier} has room for. A school broom's bundle takes one change; a racing
     * frame is built to be tuned.
     */
    public static int capacity(BroomTier tier) {
        return switch (tier) {
            case SCHOOL -> 1;
            case STANDARD -> 2;
            case RACING -> 3;
            case ELITE, LEGENDARY -> 4;
        };
    }

    private static Map<FittingStat, Float> mods(Object... pairs) {
        EnumMap<FittingStat, Float> map = new EnumMap<>(FittingStat.class);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((FittingStat) pairs[i], (Float) pairs[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }
}
