package at.koopro.wizardsandbeasts.brew;

/**
 * The {@code potion_potency} herbology skill, as the brewing pillar reads it.
 *
 * <p>It used to scale every potion by the <em>drinker's</em> level. Since 2026-10-22 it is the brewer's: it raises
 * the quality stamped on the bottle ({@link BrewQuality}) and lowers the chance of a ruined batch
 * ({@link BrewFailure}). This class keeps only the skill id both read.
 */
public final class BrewPotency {

    /** The Herbology node id, {@code data/wizards_and_beasts/skill_nodes/herbology/potion_potency.json}. */
    public static final String SKILL_ID = "potion_potency";

    private BrewPotency() {}
}
