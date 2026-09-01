package at.koopro.wizardsandbeasts.bestiary;

import org.jspecify.annotations.NullMarked;

/**
 * The one rule that decides how a bestiary entry advances. Pure functions over
 * {@link DiscoveryTier} — no player, no level, no registry — so the rule is testable on its own and
 * every call site necessarily agrees with every other one.
 *
 * <h2>The rule</h2>
 * <p><b>Seeing a creature opens its page. The entry's declared trigger deepens it.</b>
 *
 * <ul>
 *   <li>Coming within sighting range of a creature moves its entry to {@link DiscoveryTier#SIGHTED}
 *       — <em>for every entry</em>, whatever trigger it declares.</li>
 *   <li>Whenever the entry's own {@link EncounterTrigger} fires (killing it, taking its drops, using
 *       the declared item on it) the entry advances exactly one tier, up to
 *       {@link DiscoveryTier#MASTERED}.</li>
 * </ul>
 *
 * <p>The first half is the part that changed. 73 of the 107 shipped entries declare
 * {@link EncounterTrigger#KILL}, and the discovery handler only ever scanned for entries declaring
 * {@link EncounterTrigger#PROXIMITY}: the only way to open a Unicorn's page was to kill a unicorn,
 * which is both hostile to the fiction and the opposite of what a field naturalist's notebook is.
 * Sighting is now universal, and {@code encounterTrigger} keeps its meaning as the channel through
 * which an entry gets <em>deeper</em> rather than the channel through which it exists at all.
 *
 * <p>Tiers never fall. Every method here is monotonic, so a repeat encounter can only leave the tier
 * where it was or raise it; {@code BestiaryDataHelper.setTier} enforces the same invariant a second
 * time at the storage layer.
 */
@NullMarked
public final class EncounterRule {

    /**
     * Blocks a player may be from a creature and still log a sighting.
     *
     * <p>Twelve, matching the radius the proximity scan already used. {@link DiscoveryTier#SIGHTED}'s
     * own hint text used to promise eight, which was never the number the scan applied.
     */
    public static final double SIGHTING_RANGE = 12.0;

    private EncounterRule() {}

    /**
     * Tier after seeing the creature. Opens an unopened entry and otherwise changes nothing —
     * sighting is how a page is <em>started</em>, never how it progresses, or standing next to a
     * bowtruckle for a minute would master it.
     */
    public static DiscoveryTier onSighted(DiscoveryTier current) {
        return current == DiscoveryTier.UNDISCOVERED ? DiscoveryTier.SIGHTED : current;
    }

    /**
     * Tier after the entry's declared trigger fired: one step up, clamped at
     * {@link DiscoveryTier#MASTERED}.
     *
     * <p>From {@link DiscoveryTier#UNDISCOVERED} this lands on {@link DiscoveryTier#SIGHTED}, not
     * {@link DiscoveryTier#ENCOUNTERED} — killing something you had never seen still only proves you
     * have now seen it, and the next kill carries it further.
     */
    public static DiscoveryTier onTriggered(DiscoveryTier current) {
        return current.next();
    }

    /**
     * Whether {@code event} is the channel {@code declared} names.
     *
     * <p>{@link EncounterTrigger#MANUAL} answers {@code false} to everything: those entries are
     * advanced by command or by another system calling {@code BestiaryDataHelper} directly, and
     * nothing that happens in the world should move them on its own.
     */
    public static boolean deepensOn(EncounterTrigger declared, EncounterTrigger event) {
        return declared != EncounterTrigger.MANUAL && declared == event;
    }
}
