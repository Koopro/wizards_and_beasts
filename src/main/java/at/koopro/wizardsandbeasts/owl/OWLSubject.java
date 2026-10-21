package at.koopro.wizardsandbeasts.owl;

/**
 * The twelve O.W.L. subjects.
 *
 * <p>{@link #examined()} is whether the mod has anything for an examiner to look at. Three subjects
 * have no content behind them — no arithmancy, divination or Muggle-studies play exists — so they are
 * not sat at all: they never get a grade, never appear in a career's requirements, and the results
 * sheet says "not examined" rather than printing a Troll nobody could have avoided
 * (documentation/PROGRESSION_MAP.md, revision 2026-10-01). Astronomy is examined since 2026-10-14: the
 * telescope charts moon phases and constellations ({@code education.Astronomy}). The constants stay, in
 * this order, because saves and the sync payload name subjects by name and ordinal.
 */
public enum OWLSubject {
    CHARMS(true),
    DEFENCE_AGAINST_DARK_ARTS(true),
    TRANSFIGURATION(true),
    POTIONS(true),
    HERBOLOGY(true),
    CARE_OF_MAGICAL_CREATURES(true),
    ARITHMANCY(false),
    ANCIENT_RUNES(true),
    DIVINATION(false),
    HISTORY_OF_MAGIC(true),
    ASTRONOMY(true),
    MUGGLE_STUDIES(false);

    /** By lower-case name ({@code "care_of_magical_creatures"}); an unknown name is a decode error, not a crash. */
    public static final com.mojang.serialization.Codec<OWLSubject> CODEC = com.mojang.serialization.Codec.STRING
            .comapFlatMap(raw -> {
                try {
                    return com.mojang.serialization.DataResult.success(
                            valueOf(raw.toUpperCase(java.util.Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return com.mojang.serialization.DataResult.error(() -> "Unknown O.W.L. subject: " + raw);
                }
            }, s -> s.name().toLowerCase(java.util.Locale.ROOT));

    private final boolean examined;

    OWLSubject(boolean examined) {
        this.examined = examined;
    }

    /** Whether candidates sit this subject: something in the game feeds it. */
    public boolean examined() {
        return examined;
    }

    public String translationKey() {
        return "owls.subject." + name().toLowerCase();
    }
}
