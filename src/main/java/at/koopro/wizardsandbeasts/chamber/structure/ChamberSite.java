package at.koopro.wizardsandbeasts.chamber.structure;

import org.jspecify.annotations.NullMarked;

/**
 * Whether the ground over a Chamber suits it, as a rule. Pure over what {@link ChamberOfSecretsStructure} measures.
 *
 * <p>Vanilla checks a jigsaw's biome at its start height, which for a buried Chamber is 36 blocks underground: on real
 * terrain a ring came up in a river, whose biome the Chamber's list leaves out. It checks nothing about the land around
 * the entrance either, and 2 of 20 surveyed halls had over a third of their roof uncovered, because the roof is 20
 * blocks below the ring wherever the ground beside it falls away.
 */
@NullMarked
public final class ChamberSite {

    /** How far the ground may fall from the ring and still cover the hall, whose roof is 20 blocks below it. */
    public static final int MAX_DROP = 16;
    /** How far from the ring the ground is sampled: the hall's far corners are about this far out. */
    public static final int SAMPLE_REACH = 18;

    private ChamberSite() {}

    /**
     * @param surfaceBiomeListed the biome at the ring itself is one a Chamber may stand in
     * @param surfaceIsLiquid    the ring would stand in water or lava
     * @param surface            the ground's height at the ring
     * @param lowestAround       the lowest ground height within {@link #SAMPLE_REACH}
     */
    public static boolean suits(boolean surfaceBiomeListed, boolean surfaceIsLiquid, int surface, int lowestAround) {
        return surfaceBiomeListed && !surfaceIsLiquid && lowestAround >= surface - MAX_DROP;
    }
}
