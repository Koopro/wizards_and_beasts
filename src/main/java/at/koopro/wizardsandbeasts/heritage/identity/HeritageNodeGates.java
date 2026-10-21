package at.koopro.wizardsandbeasts.heritage.identity;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Skill-web nodes a lineage must have reached in play before they can be learned. The node JSON has no room for it
 * (the codec is at its sixteen fields), and there are few enough that a table here reads better than a schema change.
 *
 * <ul>
 *   <li>{@code elf_free} — <i>Free Elf</i>. Freedom is a gift of clothing from a master (CoS ch.18,
 *       {@code HouseElfBond.present}), never something an elf buys with skill points; the node is what a free elf
 *       learns to do with it.</li>
 * </ul>
 */
@NullMarked
public final class HeritageNodeGates {

    private static final Map<String, Predicate<PlayerHeritageData>> GATES = Map.of(
            "elf_free", data -> !data.hasTrait("bound_service"));

    private HeritageNodeGates() {}

    /** Whether {@code data}'s lineage may learn node {@code skillId}; true for every node without a gate. */
    public static boolean open(String skillId, PlayerHeritageData data) {
        Predicate<PlayerHeritageData> gate = GATES.get(skillId);
        return gate == null || gate.test(data);
    }
}
