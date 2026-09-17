package at.koopro.wizardsandbeasts.bestiary;

import org.jspecify.annotations.NullMarked;

/**
 * The one rule that decides how a bestiary page fills in. Pure functions over {@link DiscoveryTier} — no player,
 * level or registry — so it is testable on its own and every call site agrees with every other one.
 *
 * <h2>The rule: a naturalist's notebook, not a trophy wall</h2>
 * <ul>
 *   <li><b>{@link DiscoveryTier#ENCOUNTERED}</b> — you have seen it, or met it in a fight. Killing a creature is an
 *       encounter and nothing more: it teaches you it can die.</li>
 *   <li><b>{@link DiscoveryTier#OBSERVED}</b> — you have watched it calmly, in sight and in range, for
 *       {@link #OBSERVED_TICKS}. Hurting it stops the clock for a while.</li>
 *   <li><b>{@link DiscoveryTier#STUDIED}</b> — you have done what its profile says a naturalist does with it:
 *       fed it, handled it, taken what it sheds. A creature whose profile asks only for watching is studied after
 *       {@link #STUDIED_BY_WATCHING_TICKS} of it.</li>
 *   <li><b>{@link DiscoveryTier#KNOWN}</b> — you have witnessed its signature behaviour (a mooncalf's dance, a
 *       phoenix's rebirth) or won its trust. A creature with no signature is known after
 *       {@link #KNOWN_BY_WATCHING_TICKS} of patient watching.</li>
 * </ul>
 *
 * <p>Tiers never fall. Every method is monotonic, and {@code BestiaryDataHelper.setTier} enforces the same
 * invariant again at the storage layer.
 *
 * <p>{@link EncounterTrigger} on an entry no longer decides depth. Only {@link EncounterTrigger#MANUAL} still
 * means something: the page is moved by command or by a creature's own code, and nothing automatic touches it.
 */
@NullMarked
public final class EncounterRule {

    /** Blocks a player may be from a creature and still log a sighting. */
    public static final double SIGHTING_RANGE = 12.0;

    /** Blocks a player may be from a creature and still be watching it. */
    public static final double OBSERVATION_RANGE = 16.0;

    /** Thirty seconds of calm watching. */
    public static final int OBSERVED_TICKS = 600;

    /** Five minutes, for a creature whose profile asks for nothing but watching. */
    public static final int STUDIED_BY_WATCHING_TICKS = 6000;

    /** Twenty minutes, for a creature with no signature behaviour to wait for. */
    public static final int KNOWN_BY_WATCHING_TICKS = 24000;

    /** After hurting a creature, this long before watching it counts again. */
    public static final int CALM_AFTER_HARM_TICKS = 1200;

    private EncounterRule() {}

    /** Seen it, fought it, or killed it. Opens an unopened page and otherwise changes nothing. */
    public static DiscoveryTier onEncountered(DiscoveryTier current) {
        return current == DiscoveryTier.UNKNOWN ? DiscoveryTier.ENCOUNTERED : current;
    }

    /**
     * The tier a total amount of calm watching has earned.
     *
     * @param studiedByHand the creature's profile names a study act other than watching, so watching alone
     *                      never reaches {@link DiscoveryTier#STUDIED}
     * @param hasSignature  the creature has a signature behaviour, so watching alone never reaches
     *                      {@link DiscoveryTier#KNOWN}
     */
    public static DiscoveryTier afterWatching(DiscoveryTier current, int watchedTicks, boolean studiedByHand,
                                              boolean hasSignature) {
        DiscoveryTier earned = onEncountered(current);
        if (watchedTicks >= OBSERVED_TICKS) {
            earned = max(earned, DiscoveryTier.OBSERVED);
        }
        if (!studiedByHand && watchedTicks >= STUDIED_BY_WATCHING_TICKS) {
            earned = max(earned, DiscoveryTier.STUDIED);
        }
        if (!hasSignature && earned.atLeast(DiscoveryTier.STUDIED) && watchedTicks >= KNOWN_BY_WATCHING_TICKS) {
            earned = DiscoveryTier.KNOWN;
        }
        return earned;
    }

    /** A study act — feeding, handling, harvesting what it sheds. */
    public static DiscoveryTier onStudied(DiscoveryTier current) {
        return max(current, DiscoveryTier.STUDIED);
    }

    /** Its signature behaviour witnessed, or its trust won. */
    public static DiscoveryTier onSignature(DiscoveryTier current) {
        return DiscoveryTier.KNOWN;
    }

    /** Whether automatic progress applies to an entry at all. */
    public static boolean automatic(EncounterTrigger declared) {
        return declared != EncounterTrigger.MANUAL;
    }

    private static DiscoveryTier max(DiscoveryTier a, DiscoveryTier b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
