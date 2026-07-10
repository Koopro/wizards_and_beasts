package at.koopro.wizardsandbeasts.skill;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the shipped skill web datapack JSON: every file parses through {@link Skill#CODEC},
 * ids are unique, no removed fields linger, every edge resolves inside the same audience web,
 * and every node is reachable from a root of its web (the adjacency-allocatable invariant).
 */
class SkillNodeJsonTest {

    private static final Path NODE_DIR =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "skill_nodes");

    @Test
    void allShippedNodesParseAndFormValidWebs() throws IOException {
        assertTrue(Files.isDirectory(NODE_DIR), "missing skill_nodes datapack directory");

        Map<String, Skill> byId = new HashMap<>();
        try (Stream<Path> files = Files.walk(NODE_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonElement json = JsonParser.parseString(Files.readString(file));
                JsonObject obj = json.getAsJsonObject();
                assertFalse(obj.has("prerequisites"), file + ": removed field 'prerequisites' still present");
                assertFalse(obj.has("column"), file + ": removed field 'column' still present");
                assertFalse(obj.has("tier"), file + ": removed field 'tier' still present");
                assertTrue(obj.has("x") && obj.has("y"), file + ": missing web coordinates");

                Skill skill = Skill.CODEC.parse(JsonOps.INSTANCE, json)
                        .getOrThrow(msg -> new AssertionError(file + ": " + msg));
                assertNull(byId.put(skill.getId(), skill), "duplicate skill id: " + skill.getId());
                assertEquals(NODE_DIR.resolve(skill.getTree().getId()).resolve(skill.getId() + ".json"), file,
                        "file location must match <tree>/<id>.json for " + skill.getId());
            }
        }
        assertEquals(61, byId.size(), "expected 60 nodes + 1 placeholder wizard center");

        // Symmetrized adjacency; every edge must resolve within the same audience web.
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (Skill skill : byId.values()) {
            for (String edge : skill.getEdges()) {
                Skill other = byId.get(edge);
                assertTrue(other != null,
                        "skill '" + skill.getId() + "' has dangling edge '" + edge + "'");
                assertEquals(skill.getTree().getAudience(), other.getTree().getAudience(),
                        "cross-audience edge " + skill.getId() + " -> " + edge);
                adjacency.computeIfAbsent(skill.getId(), k -> new HashSet<>()).add(edge);
                adjacency.computeIfAbsent(edge, k -> new HashSet<>()).add(skill.getId());
            }
        }

        // Reachability per audience web from its roots.
        for (SkillTreeId.Audience audience : SkillTreeId.Audience.values()) {
            Set<String> visited = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            List<Skill> web = byId.values().stream()
                    .filter(s -> s.getTree().getAudience() == audience).toList();
            web.stream().filter(Skill::isRoot).forEach(s -> { visited.add(s.getId()); queue.add(s.getId()); });
            assertFalse(web.isEmpty() && audience == SkillTreeId.Audience.WIZARD, "wizard web empty");
            if (web.isEmpty()) continue;
            assertFalse(queue.isEmpty(), audience + " web has no root node");
            while (!queue.isEmpty()) {
                for (String n : adjacency.getOrDefault(queue.poll(), Set.of())) {
                    if (visited.add(n)) queue.add(n);
                }
            }
            for (Skill skill : web) {
                assertTrue(visited.contains(skill.getId()),
                        "skill '" + skill.getId() + "' unreachable from any " + audience + " web root");
            }
        }

        // Placeholder center: sole wizard root, edges to each former wizard tree root.
        Skill center = byId.get("wizard_core");
        assertTrue(center != null && center.isRoot(), "placeholder wizard_core root missing");
        assertEquals(6, center.getEdges().size(), "wizard_core must link all 6 former tree roots");
        long wizardRoots = byId.values().stream()
                .filter(s -> s.getTree().getAudience() == SkillTreeId.Audience.WIZARD)
                .filter(Skill::isRoot).count();
        assertEquals(1, wizardRoots, "wizard web must have exactly one root (the placeholder center)");
    }

    @Test
    void codecRoundTripPreservesAllFields() {
        Skill original = Skill.builder("test_node", "Test Node")
                .description("A test node.")
                .tree(SkillTreeId.SPELL_MASTERY)
                .maxLevel(3)
                .cost(2)
                .position(-60.5, 285.0)
                .edge("basic_casting")
                .size(Skill.Size.KEYSTONE)
                .root(true)
                .effect(new SkillEffect.SpellDamageBonus("stupefy", 0.1f))
                .effect(new SkillEffect.GameplayBonus(GameplayStat.HARVEST_BONUS_CHANCE, 0.15f))
                .build();

        JsonElement encoded = Skill.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg));
        Skill reparsed = Skill.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(msg -> new AssertionError("parse failed: " + msg));

        assertEquals(original.getId(), reparsed.getId());
        assertEquals(original.getDisplayName(), reparsed.getDisplayName());
        assertEquals(original.getDescription(), reparsed.getDescription());
        assertEquals(original.getTree(), reparsed.getTree());
        assertEquals(original.getMaxLevel(), reparsed.getMaxLevel());
        assertEquals(original.getPointCost(), reparsed.getPointCost());
        assertEquals(original.getEffects(), reparsed.getEffects());
        assertEquals(original.getExplicitNodeEffects(), reparsed.getExplicitNodeEffects());
        assertEquals(original.getX(), reparsed.getX());
        assertEquals(original.getY(), reparsed.getY());
        assertEquals(original.getEdges(), reparsed.getEdges());
        assertEquals(original.getSize(), reparsed.getSize());
        assertEquals(original.isRoot(), reparsed.isRoot());
        assertEquals(List.of(), reparsed.getExplicitNodeEffects());
    }
}
