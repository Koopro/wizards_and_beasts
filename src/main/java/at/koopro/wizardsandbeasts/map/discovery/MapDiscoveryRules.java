package at.koopro.wizardsandbeasts.map.discovery;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The loaded {@code map_discovery} rule set, split by variant at load time.
 *
 * <p>Split rather than filtered per use: the block rules run inside a per-chunk section scan and
 * the structure rules run once per survey pass, so a shared list would mean every chunk scan
 * walking past every structure rule to find nothing.
 *
 * <p>Volatile snapshot lists rather than mutable collections. A datapack reload replaces them
 * wholesale on the server thread while the surveyor may be part-way through a pass; swapping an
 * immutable reference means the pass in flight finishes against the rules it started with instead
 * of throwing a {@code ConcurrentModificationException}.
 */
public final class MapDiscoveryRules {

    private static volatile List<MapDiscoveryRule.FromStructure> structureRules = List.of();
    private static volatile List<MapDiscoveryRule.FromBlocks> blockRules = List.of();

    private MapDiscoveryRules() {
    }

    static void replaceAll(Map<Identifier, MapDiscoveryRule> loaded) {
        List<MapDiscoveryRule.FromStructure> structures = new ArrayList<>();
        List<MapDiscoveryRule.FromBlocks> blocks = new ArrayList<>();
        for (MapDiscoveryRule rule : loaded.values()) {
            switch (rule) {
                case MapDiscoveryRule.FromStructure fromStructure -> structures.add(fromStructure);
                case MapDiscoveryRule.FromBlocks fromBlocks -> {
                    if (fromBlocks.isSane()) {
                        blocks.add(fromBlocks);
                    }
                }
            }
        }
        structureRules = Collections.unmodifiableList(structures);
        blockRules = Collections.unmodifiableList(blocks);
    }

    public static List<MapDiscoveryRule.FromStructure> structures() {
        return structureRules;
    }

    public static List<MapDiscoveryRule.FromBlocks> blocks() {
        return blockRules;
    }

    /** True when nothing is loaded, which lets the surveyor skip its structure pass entirely. */
    public static boolean isEmpty() {
        return structureRules.isEmpty() && blockRules.isEmpty();
    }
}
