package at.koopro.wizardsandbeasts.skill.build;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Whether a node can give back a level without leaving the rest of the web floating.
 *
 * <p>Allocation is adjacency: a node's first level needs an allocated neighbour. Unlearning has to respect the
 * same shape in reverse, or a player could buy {@code A → B → C}, unlearn {@code B}, and keep {@code C} with
 * nothing connecting it to Polaris — a free teleport across the web. So the rule is: after the removal, every
 * still-allocated node must be reachable from an allocated root through allocated nodes.
 *
 * <p>Taking one level off a multi-level node never strands anything (it stays allocated), so that is always
 * allowed. Ids that no longer resolve to a node are ignored rather than counted as stranded; the login migration
 * owns those.
 */
@NullMarked
public final class WebConnectivity {

    private WebConnectivity() {}

    /**
     * Nodes that would be cut off if {@code nodeId} lost a level. Empty means the unlearn is safe.
     *
     * @param allocated node id → level, as stored
     * @param neighbours undirected adjacency, the same the allocation gate uses
     * @param isRoot    whether a node is a web root (always allocatable)
     * @param known     whether an id still resolves to a node
     */
    public static Set<String> strandedBy(String nodeId, Map<String, Integer> allocated,
                                         Function<String, Collection<String>> neighbours,
                                         Predicate<String> isRoot, Predicate<String> known) {
        int level = allocated.getOrDefault(nodeId, 0);
        if (level != 1) {
            return Set.of(); // not allocated (nothing to strand) or keeps a level after the removal
        }
        Set<String> remaining = new HashSet<>();
        for (Map.Entry<String, Integer> entry : allocated.entrySet()) {
            if (entry.getValue() > 0 && !entry.getKey().equals(nodeId) && known.test(entry.getKey())) {
                remaining.add(entry.getKey());
            }
        }

        Set<String> reached = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        for (String id : remaining) {
            if (isRoot.test(id)) {
                reached.add(id);
                queue.add(id);
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String next : neighbours.apply(current)) {
                if (remaining.contains(next) && reached.add(next)) {
                    queue.add(next);
                }
            }
        }

        Set<String> stranded = new TreeSet<>(remaining);
        stranded.removeAll(reached);
        return stranded;
    }
}
