package at.koopro.wizardsandbeasts.standing.gate;

import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The loaded {@link StandingGate}s, indexed by what they gate.
 *
 * <p>Volatile-swapped immutable index, like the mod's other datapack registries. Read from
 * {@code SkillSystemAPI.evaluateUnlock}, which runs on a click and must not allocate or block.
 *
 * <p>{@link #isEmpty()} exists so the unlock path can skip the whole check on a default install, where
 * no gates are authored — the mechanism costs nothing until somebody uses it.
 */
@NullMarked
public final class StandingGates {

    private static volatile Index INDEX = Index.EMPTY;

    private StandingGates() {}

    public static void replaceAll(Map<Identifier, StandingGate> gates) {
        INDEX = Index.of(gates.values());
    }

    /** True when nothing is gated at all. */
    public static boolean isEmpty() {
        return INDEX.byTree.isEmpty() && INDEX.byNode.isEmpty();
    }

    public static int count() {
        return INDEX.byTree.values().stream().mapToInt(List::size).sum()
                + INDEX.byNode.values().stream().mapToInt(List::size).sum();
    }

    /**
     * The first requirement {@code lookup} fails for this node, or {@code null} if nothing stands in
     * the way. Node gates are checked before tree gates: a node-level rule is the more specific
     * statement, so it should be the one the player is told about.
     */
    public static @Nullable StandingRequirement firstUnmet(SkillTreeId tree, String nodeId,
                                                           StandingGate.StandingLookup lookup) {
        if (isEmpty()) {
            return null;
        }
        StandingRequirement unmet = firstUnmetIn(
                INDEX.byNode.get(nodeId.toLowerCase(Locale.ROOT)), lookup);
        if (unmet != null) {
            return unmet;
        }
        return firstUnmetIn(INDEX.byTree.get(tree), lookup);
    }

    private static @Nullable StandingRequirement firstUnmetIn(@Nullable List<StandingGate> gates,
                                                              StandingGate.StandingLookup lookup) {
        if (gates == null) {
            return null;
        }
        for (StandingGate gate : gates) {
            StandingRequirement unmet = gate.firstUnmet(lookup);
            if (unmet != null) {
                return unmet;
            }
        }
        return null;
    }

    private record Index(Map<SkillTreeId, List<StandingGate>> byTree,
                         Map<String, List<StandingGate>> byNode) {

        static final Index EMPTY = new Index(Map.of(), Map.of());

        static Index of(Iterable<StandingGate> gates) {
            Map<SkillTreeId, List<StandingGate>> byTree = new EnumMap<>(SkillTreeId.class);
            Map<String, List<StandingGate>> byNode = new LinkedHashMap<>();
            for (StandingGate gate : gates) {
                if (gate.tree() != null) {
                    byTree.computeIfAbsent(gate.tree(), t -> new ArrayList<>()).add(gate);
                } else if (gate.node() != null) {
                    byNode.computeIfAbsent(gate.node().toLowerCase(Locale.ROOT), n -> new ArrayList<>())
                            .add(gate);
                }
            }
            byTree.replaceAll((tree, list) -> Collections.unmodifiableList(list));
            byNode.replaceAll((node, list) -> Collections.unmodifiableList(list));
            return new Index(Collections.unmodifiableMap(byTree), Collections.unmodifiableMap(byNode));
        }
    }
}
