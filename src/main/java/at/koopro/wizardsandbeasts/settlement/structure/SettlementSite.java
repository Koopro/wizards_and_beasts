package at.koopro.wizardsandbeasts.settlement.structure;

import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.List;

/**
 * Whether a generated hamlet may stand where the world offered it, as a rule. Pure over what
 * {@link SettlementStructure} measures.
 *
 * <p>Every piece is rigid and shares one height, so the land has to be nearly level across the whole footprint. Vanilla
 * asks none of this: it reads the biome at one point and takes the height at the plaza's centre.
 *
 * <p>Pools hold one role's variants, so a role is asked for once; but jigsaw drops a piece that does not fit, without
 * complaint. A hamlet without its bank or its shops is not a smaller hamlet, it is a broken one, so it is refused.
 */
@NullMarked
public final class SettlementSite {

    /** The most the ground may vary under the footprint before the terrain adaptation can no longer hide it. */
    public static final int MAX_RELIEF = 10;

    /**
     * Offsets from the plaza's centre at which the ground is read, far points first: most rejected land shows it there,
     * and each costs a noise column. A hamlet is a cross, the plaza with its buildings on the arms, so these lie under
     * it. Its corners are empty, and a pond or a rise there does it no harm. Mirrored both ways, because jigsaw may
     * turn the plaza a quarter.
     */
    public static final List<int[]> SAMPLES = List.of(
            new int[] {30, 0}, new int[] {-30, 0}, new int[] {0, 30}, new int[] {0, -30},
            new int[] {24, 6}, new int[] {24, -6}, new int[] {-24, 6}, new int[] {-24, -6},
            new int[] {6, 24}, new int[] {-6, 24}, new int[] {6, -24}, new int[] {-6, -24},
            new int[] {12, 12}, new int[] {12, -12}, new int[] {-12, 12}, new int[] {-12, -12});

    /** A template is {@code wizarding_hamlet/<role>/<variant>}; a site must have one of each of these. */
    public static final List<String> REQUIRED_ROLES =
            List.of("plaza", "keeper", "wandmaker", "apothecary", "reading_room", "bank");

    private SettlementSite() {}

    /**
     * @param surfaceBiomeListed the biome at the plaza is one a hamlet may stand in
     * @param anyLiquid          any sampled column is water or lava at its surface
     * @param lowest             the lowest ground height sampled
     * @param highest            the highest ground height sampled
     */
    public static boolean suits(boolean surfaceBiomeListed, boolean anyLiquid, int lowest, int highest) {
        return surfaceBiomeListed && !anyLiquid && highest - lowest <= MAX_RELIEF;
    }

    /** Whether every required role made it into the pieces jigsaw placed. */
    public static boolean complete(Collection<String> roles) {
        return roles.containsAll(REQUIRED_ROLES);
    }

    /** The role a template plays, from its path; empty for a template outside the layout. */
    public static String roleOf(String templatePath) {
        String[] parts = templatePath.split("/");
        return parts.length >= 3 ? parts[parts.length - 2] : "";
    }
}
