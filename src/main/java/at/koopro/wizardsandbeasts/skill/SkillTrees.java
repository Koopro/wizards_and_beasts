package at.koopro.wizardsandbeasts.skill;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry of all skill node definitions, loaded from datapack JSON
 * ({@code data/<namespace>/skill_nodes/}) by {@link SkillNodeLoader}. Follows the
 * {@link at.koopro.wizardsandbeasts.broom.BroomDefinitionRegistry} /
 * {@link at.koopro.wizardsandbeasts.bestiary.BestiaryEntryRegistry} pattern: a volatile-swapped
 * server-side snapshot plus a client-side cache populated by
 * {@link at.koopro.wizardsandbeasts.network.skill.SyncSkillDefinitionsPayload}.
 *
 * <p>Each snapshot also carries the <b>web graph</b> (Phase 2): the symmetrized edge adjacency
 * used by allocation ({@link SkillSystemAPI#evaluateUnlock}) and the canvas. Edges referencing
 * unknown ids or crossing audiences are dropped here (and reported by the loader's validation
 * pass, which runs the same rules with logging).
 *
 * <p>Server logic (allocation, commands, effect application) reads {@link #byId}/{@link #getTree}/
 * {@link #neighbors}; client GUI code must read the {@code client*} variants — on a dedicated
 * server the client-side server snapshot is empty.
 */
public final class SkillTrees {

    /** Volatile-swapped immutable state: readers never observe a mid-reload empty/partial registry. */
    private static volatile Snapshot SERVER = Snapshot.EMPTY;

    /** Client-side cache, populated on {@code SyncSkillDefinitionsPayload} receipt. Read only on the client. */
    private static volatile Snapshot CLIENT = Snapshot.EMPTY;

    private SkillTrees() {}

    /** Replaces the server-side definitions; called by {@link SkillNodeLoader} on datapack (re)load. */
    public static void replaceAll(Collection<Skill> skills) {
        SERVER = Snapshot.of(skills);
    }

    /** Replaces the client cache with the synced definition list. */
    public static void setClientDefinitions(Collection<Skill> skills) {
        CLIENT = Snapshot.of(skills);
    }

    // ── Server-side reads (authoritative) ──

    @Nullable
    public static Skill byId(String id) {
        return SERVER.byId.get(id);
    }

    public static List<Skill> getTree(SkillTreeId tree) {
        return SERVER.byTree.getOrDefault(tree, Collections.emptyList());
    }

    public static Collection<Skill> all() {
        return Collections.unmodifiableCollection(SERVER.byId.values());
    }

    public static Collection<String> allIds() {
        return Collections.unmodifiableCollection(SERVER.byId.keySet());
    }

    public static int count() {
        return SERVER.byId.size();
    }

    /** Symmetrized edge-neighbors of a node (unknown/cross-audience edges already dropped). */
    public static Set<String> neighbors(String id) {
        return SERVER.adjacency.getOrDefault(id, Set.of());
    }

    /** All nodes in the given audience's web. */
    public static List<Skill> webNodes(SkillTreeId.Audience audience) {
        return SERVER.byAudience.getOrDefault(audience, List.of());
    }

    // ── Client-side reads (synced cache) ──

    @Nullable
    public static Skill clientById(String id) {
        return CLIENT.byId.get(id);
    }

    public static List<Skill> clientGetTree(SkillTreeId tree) {
        return CLIENT.byTree.getOrDefault(tree, Collections.emptyList());
    }

    public static Collection<Skill> clientAll() {
        return Collections.unmodifiableCollection(CLIENT.byId.values());
    }

    public static Set<String> clientNeighbors(String id) {
        return CLIENT.adjacency.getOrDefault(id, Set.of());
    }

    public static List<Skill> clientWebNodes(SkillTreeId.Audience audience) {
        return CLIENT.byAudience.getOrDefault(audience, List.of());
    }

    private record Snapshot(Map<String, Skill> byId,
                            Map<SkillTreeId, List<Skill>> byTree,
                            Map<SkillTreeId.Audience, List<Skill>> byAudience,
                            Map<String, Set<String>> adjacency) {
        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of(), Map.of());

        /** Indexes by id/tree/audience and builds the symmetrized web adjacency. Per-tree lists sorted by id. */
        static Snapshot of(Collection<Skill> skills) {
            Map<String, Skill> byId = new LinkedHashMap<>();
            Map<SkillTreeId, List<Skill>> byTree = new EnumMap<>(SkillTreeId.class);
            Map<SkillTreeId.Audience, List<Skill>> byAudience = new EnumMap<>(SkillTreeId.Audience.class);
            List<Skill> sorted = new ArrayList<>(skills);
            sorted.sort(Comparator.comparing(Skill::getId));
            for (Skill skill : sorted) {
                byId.put(skill.getId(), skill);
                byTree.computeIfAbsent(skill.getTree(), k -> new ArrayList<>()).add(skill);
                byAudience.computeIfAbsent(skill.getTree().getAudience(), k -> new ArrayList<>()).add(skill);
            }

            Map<String, Set<String>> adjacency = new LinkedHashMap<>();
            for (Skill skill : sorted) {
                for (String edge : skill.getEdges()) {
                    Skill other = byId.get(edge);
                    if (other == null || other == skill) {
                        continue; // unknown or self edge — loader logged it
                    }
                    if (other.getTree().getAudience() != skill.getTree().getAudience()) {
                        continue; // cross-audience edge — data error, loader logged it
                    }
                    adjacency.computeIfAbsent(skill.getId(), k -> new LinkedHashSet<>()).add(other.getId());
                    adjacency.computeIfAbsent(other.getId(), k -> new LinkedHashSet<>()).add(skill.getId());
                }
            }
            adjacency.replaceAll((id, set) -> Collections.unmodifiableSet(set));

            byTree.replaceAll((tree, list) -> List.copyOf(list));
            byAudience.replaceAll((audience, list) -> List.copyOf(list));
            return new Snapshot(Collections.unmodifiableMap(byId), Collections.unmodifiableMap(byTree),
                    Collections.unmodifiableMap(byAudience), Collections.unmodifiableMap(adjacency));
        }
    }
}
