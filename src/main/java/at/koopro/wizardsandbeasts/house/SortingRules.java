package at.koopro.wizardsandbeasts.house;

import org.jspecify.annotations.NullMarked;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * How the Sorting Hat weighs a wizard, as pure arithmetic: what they have studied, what they have committed to, how
 * dark they have gone, and a small personal lean of their own (from who they are, so it never changes).
 *
 * <p>The Hat takes the wizard's choice into account (<i>Philosopher's Stone</i> ch. 7: "Not Slytherin, eh?"): a
 * plea against the house it leans to is honoured unless the lean is overwhelming, by more than {@link #PLEA_LIMIT}.
 */
@NullMarked
public final class SortingRules {

    /** How far ahead the leading house must be before the Hat ignores a plea against it. */
    public static final float PLEA_LIMIT = 8.0f;
    /** Extra weight on the tree of a committed vocation. */
    public static final float VOCATION_WEIGHT = 4.0f;
    /** Dark corruption (0..100) divided by this goes to Slytherin. */
    public static final float CORRUPTION_DIVISOR = 10.0f;

    private SortingRules() {}

    /** How much each spent level in a skill tree says about each house. Unknown trees say nothing. */
    public static Map<House, Float> treeWeights(String tree) {
        Map<House, Float> w = new EnumMap<>(House.class);
        switch (tree) {
            case "spell_mastery" -> { w.put(House.GRYFFINDOR, 2f); w.put(House.RAVENCLAW, 1f); }
            case "dark_arts" -> w.put(House.SLYTHERIN, 3f);
            case "herbology" -> w.put(House.HUFFLEPUFF, 3f);
            case "magizoology" -> { w.put(House.HUFFLEPUFF, 2f); w.put(House.GRYFFINDOR, 1f); }
            case "alchemy" -> { w.put(House.RAVENCLAW, 2f); w.put(House.SLYTHERIN, 1f); }
            case "wandlore" -> { w.put(House.RAVENCLAW, 2f); w.put(House.GRYFFINDOR, 1f); }
            case "goblin_craft" -> { w.put(House.SLYTHERIN, 1f); w.put(House.RAVENCLAW, 1f); }
            case "elf_bond" -> w.put(House.HUFFLEPUFF, 2f);
            default -> { }
        }
        return w;
    }

    /** A small lean of the wizard's own, 0..1.5 per house, fixed by who they are. */
    public static Map<House, Float> personal(UUID id) {
        Map<House, Float> w = new EnumMap<>(House.class);
        long bits = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        for (House house : House.values()) {
            int slice = (int) ((bits >>> (house.ordinal() * 8)) & 0xFF);
            w.put(house, slice / 255f * 1.5f);
        }
        return w;
    }

    /**
     * The lean, summed.
     *
     * @param treeLevels levels spent per skill tree id
     * @param vocationTree the committed vocation's tree id, or empty
     * @param corruption dark corruption, 0..100
     */
    public static Map<House, Float> lean(UUID id, Map<String, Integer> treeLevels, String vocationTree, float corruption) {
        Map<House, Float> total = new EnumMap<>(personal(id));
        treeLevels.forEach((tree, levels) -> treeWeights(tree).forEach((house, weight) ->
                total.merge(house, weight * levels, Float::sum)));
        if (!vocationTree.isEmpty()) {
            treeWeights(vocationTree).forEach((house, weight) ->
                    total.merge(house, weight * VOCATION_WEIGHT, Float::sum));
        }
        total.merge(House.SLYTHERIN, Math.max(0f, corruption) / CORRUPTION_DIVISOR, Float::sum);
        return total;
    }

    /** Houses from strongest lean to weakest; ties keep the enum's order. */
    public static House[] ranked(Map<House, Float> lean) {
        return lean.entrySet().stream()
                .sorted(Map.Entry.<House, Float>comparingByValue(Comparator.reverseOrder()))
                .map(Map.Entry::getKey).toArray(House[]::new);
    }

    /** Where the Hat puts the wizard: the leading house, or the next one if they pleaded and the lead is not overwhelming. */
    public static House decide(Map<House, Float> lean, boolean pleadAgainstLeader) {
        House[] order = ranked(lean);
        if (!pleadAgainstLeader) {
            return order[0];
        }
        float margin = lean.get(order[0]) - lean.get(order[1]);
        return margin > PLEA_LIMIT ? order[0] : order[1];
    }
}
