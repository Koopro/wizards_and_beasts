package at.koopro.wizardsandbeasts.skill;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of all skill node definitions, loaded from datapack JSON
 * ({@code data/<namespace>/skill_nodes/}) by {@link SkillNodeLoader}. Follows the
 * {@link at.koopro.wizardsandbeasts.broom.BroomDefinitionRegistry} /
 * {@link at.koopro.wizardsandbeasts.bestiary.BestiaryEntryRegistry} pattern: a volatile-swapped
 * server-side map plus a client-side cache populated by
 * {@link at.koopro.wizardsandbeasts.network.skill.SyncSkillDefinitionsPayload}.
 *
 * <p>Server logic (allocation, commands, effect application) reads {@link #byId}/{@link #getTree};
 * client GUI code must read {@link #clientById}/{@link #clientGetTree} — on a dedicated server the
 * client-side server map is empty.
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

    private record Snapshot(Map<String, Skill> byId, Map<SkillTreeId, List<Skill>> byTree) {
        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of());

        /** Indexes by id and tree; per-tree lists sorted (tier, column, id) for a stable GUI order. */
        static Snapshot of(Collection<Skill> skills) {
            Map<String, Skill> byId = new LinkedHashMap<>();
            Map<SkillTreeId, List<Skill>> byTree = new EnumMap<>(SkillTreeId.class);
            List<Skill> sorted = new ArrayList<>(skills);
            sorted.sort(Comparator.comparingInt(Skill::getTier)
                    .thenComparingInt(Skill::getColumn)
                    .thenComparing(Skill::getId));
            for (Skill skill : sorted) {
                byId.put(skill.getId(), skill);
                byTree.computeIfAbsent(skill.getTree(), k -> new ArrayList<>()).add(skill);
            }
            byTree.replaceAll((tree, list) -> List.copyOf(list));
            return new Snapshot(Collections.unmodifiableMap(byId), Collections.unmodifiableMap(byTree));
        }
    }
}
