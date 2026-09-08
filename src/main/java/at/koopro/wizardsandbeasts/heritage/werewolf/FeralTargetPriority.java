package at.koopro.wizardsandbeasts.heritage.werewolf;

/**
 * What a feral werewolf wants, in order.
 *
 * <p>Deliberately free of Minecraft types so the ordering can be asserted in a plain unit test —
 * {@link FeralTargeting} is the only thing that knows how to map an entity onto one of these, and it is
 * a single {@code switch}-shaped method for exactly that reason.
 *
 * <p>The weights are spaced widely rather than by 1 so that
 * {@link FeralTargetSelection#score(FeralTargetSelection.Candidate)} can subtract a distance in blocks
 * from them and still never let distance cross a band — the nearest possible animal must not outscore
 * the furthest possible villager. Every gap therefore has to exceed {@link #MAX_SCORED_DISTANCE}.
 * {@code FeralTargetSelectionTest} asserts exactly that, and it is what fails if somebody re-tunes
 * these numbers or raises the radius cap without re-tuning the other.
 */
public enum FeralTargetPriority {

    /** A person, and not one of the pack. The thing the wolf came out for. */
    PLAYER(1000),
    /** Villagers, wandering traders, and the iron golems standing between the wolf and them. */
    VILLAGER_OR_GOLEM(750),
    /** Livestock and wildlife: prey rather than a rival. */
    ANIMAL(500),
    /** Hostiles. The wolf will fight them; it is not what it came out for. */
    HOSTILE(250),
    /** Everything else alive — armour stands are not living, so in practice this is modded oddities. */
    OTHER(0);

    /**
     * The widest distance a scored candidate can ever be at, in blocks.
     *
     * <p>Three factors compound to it: the {@code werewolfAggroRadius} config caps at <b>96</b>, a wolf
     * at full rage widens that by {@code FeralController.RAGE_RADIUS_BONUS} (<b>×1.5</b>), and a target
     * already acquired is followed out to {@code FeralTargeting.LEASH_FACTOR} (<b>×1.5</b>) of that.
     * 96 × 1.5 × 1.5 = 216.
     *
     * <p>Stated here, in the file the band weights live in, because it is the <em>weights'</em>
     * constraint: they are what breaks if it is wrong.
     */
    public static final double MAX_SCORED_DISTANCE = 216.0;

    private final int weight;

    FeralTargetPriority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }

    /** True when this band beats {@code other} outright. Ties are not outranking. */
    public boolean outranks(FeralTargetPriority other) {
        return weight > other.weight;
    }
}
