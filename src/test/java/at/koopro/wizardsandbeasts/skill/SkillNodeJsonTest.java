package at.koopro.wizardsandbeasts.skill;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.BeforeAll;
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
 * Validates the shipped skill web datapack JSON: parsing, id uniqueness, removed-field absence,
 * edge resolution, per-web reachability, and the Phase 4 wizard-web invariants — sector-sealed
 * borders, the §3.4 connectivity contract (every pre-Phase-4 notable–notable edge survives as a
 * direct edge or an all-filler chain), filler shape rules, and lang-key resolution.
 */
class SkillNodeJsonTest {

    private static final Path NODE_DIR =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "skill_nodes");
    private static final Path LANG_FILE =
            Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "lang", "en_us.json");

    private static final List<String> SPOKE_ORDER =
            List.of("herbology", "magizoology", "spell_mastery", "dark_arts", "alchemy", "wandlore");
    private static final Set<String> OPEN_PATHWAY_PAIRS = Set.of(
            "wandlore|herbology", "herbology|magizoology", "alchemy|wandlore");
    /** Hub zone: nodes below this radius (Polaris, its cluster, spoke trunks) are sector-exempt. */
    private static final double HUB_RADIUS = 112;

    /**
     * The pre-Phase-4 wizard edge set (Phase 2's prereq conversion) — the §3.4 connectivity
     * contract: each pair must remain connected by a path whose interior nodes are all fillers.
     */
    private static final List<String[]> LEGACY_EDGES = List.of(
            // wizard_core spokes
            new String[]{"wizard_core", "basic_casting"}, new String[]{"wizard_core", "dark_knowledge"},
            new String[]{"wizard_core", "creature_knowledge"}, new String[]{"wizard_core", "wand_study"},
            new String[]{"wizard_core", "green_thumb"}, new String[]{"wizard_core", "alchemical_vigor"},
            // alchemy
            new String[]{"hardened_skin", "alchemical_vigor"}, new String[]{"philosophers_stone", "transmute_focus"},
            new String[]{"philosophers_stone", "hardened_skin"}, new String[]{"swift_brewer", "alchemical_vigor"},
            new String[]{"transmute_focus", "swift_brewer"},
            // dark_arts
            new String[]{"avada_kedavra_unlock", "imperio_unlock"}, new String[]{"avada_kedavra_unlock", "dark_resilience"},
            new String[]{"crucio_unlock", "dark_knowledge"}, new String[]{"curse_mastery", "dark_resilience"},
            new String[]{"dark_damage", "dark_knowledge"}, new String[]{"dark_resilience", "dark_damage"},
            new String[]{"imperio_unlock", "crucio_unlock"}, new String[]{"legilimency", "dark_knowledge"},
            // herbology
            new String[]{"bountiful_harvest", "harvest_bounty"}, new String[]{"harvest_bounty", "green_thumb"},
            new String[]{"herbal_vitality", "natural_remedy"}, new String[]{"natural_remedy", "potion_potency"},
            new String[]{"natural_remedy", "harvest_bounty"}, new String[]{"potion_potency", "green_thumb"},
            // magizoology
            new String[]{"animagus_study", "creature_bond"}, new String[]{"beast_handler", "creature_knowledge"},
            new String[]{"creature_bond", "niffler_friend"}, new String[]{"creature_bond", "beast_handler"},
            new String[]{"dragon_tamer", "beast_handler"}, new String[]{"keeper_vigor", "creature_bond"},
            new String[]{"niffler_friend", "creature_knowledge"},
            // spell_mastery
            new String[]{"accio_unlock", "lumos_unlock"}, new String[]{"alohomora_unlock", "wingardium_unlock"},
            new String[]{"bombarda_unlock", "combat_focus"}, new String[]{"combat_focus", "incendio_unlock"},
            new String[]{"expecto_patronum_unlock", "protego_unlock"}, new String[]{"expelliarmus_unlock", "stupefy_unlock"},
            new String[]{"flipendo_unlock", "expelliarmus_unlock"}, new String[]{"incendio_unlock", "stupefy_power"},
            new String[]{"lumos_unlock", "basic_casting"}, new String[]{"nox_unlock", "lumos_unlock"},
            new String[]{"protego_unlock", "basic_casting"}, new String[]{"reparo_unlock", "accio_unlock"},
            new String[]{"stupefy_power", "stupefy_unlock"}, new String[]{"stupefy_unlock", "basic_casting"},
            new String[]{"utility_mastery", "reparo_unlock"}, new String[]{"wingardium_unlock", "flipendo_unlock"},
            // wandlore
            new String[]{"apparition_training", "wand_mastery"}, new String[]{"arcane_reserve", "wand_precision"},
            new String[]{"quick_cast", "wand_study"}, new String[]{"spell_efficiency", "quick_cast"},
            new String[]{"wand_mastery", "spell_efficiency"}, new String[]{"wand_mastery", "wand_precision"},
            new String[]{"wand_precision", "wand_study"});

    private static final Map<String, Skill> BY_ID = new HashMap<>();
    private static final Map<String, Set<String>> ADJACENCY = new HashMap<>();

    @BeforeAll
    static void loadAll() throws IOException {
        assertTrue(Files.isDirectory(NODE_DIR), "missing skill_nodes datapack directory");
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
                assertNull(BY_ID.put(skill.getId(), skill), "duplicate skill id: " + skill.getId());
                assertEquals(NODE_DIR.resolve(skill.getTree().getId()).resolve(skill.getId() + ".json"), file,
                        "file location must match <tree>/<id>.json for " + skill.getId());
            }
        }
        for (Skill skill : BY_ID.values()) {
            for (String edge : skill.getEdges()) {
                Skill other = BY_ID.get(edge);
                assertTrue(other != null, "skill '" + skill.getId() + "' has dangling edge '" + edge + "'");
                assertEquals(skill.getTree().getAudience(), other.getTree().getAudience(),
                        "cross-audience edge " + skill.getId() + " -> " + edge);
                ADJACENCY.computeIfAbsent(skill.getId(), k -> new HashSet<>()).add(edge);
                ADJACENCY.computeIfAbsent(edge, k -> new HashSet<>()).add(skill.getId());
            }
        }
    }

    @Test
    void nodeCountsMatchGeneratedLayout() {
        assertEquals(163, BY_ID.size(),
                "60 legacy notables + wizard_core + 100 fillers + 10 goblin/elf + 2 Apparition forks");
        long fillers = BY_ID.values().stream().filter(s -> s.getSize() == Skill.Size.SMALL).count();
        assertEquals(100, fillers, "expected exactly 100 filler nodes");
    }

    @Test
    void everyWebReachableFromItsRoots() {
        for (SkillTreeId.Audience audience : SkillTreeId.Audience.values()) {
            Set<String> visited = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            List<Skill> web = BY_ID.values().stream()
                    .filter(s -> s.getTree().getAudience() == audience).toList();
            web.stream().filter(Skill::isRoot).forEach(s -> { visited.add(s.getId()); queue.add(s.getId()); });
            if (web.isEmpty()) continue;
            assertFalse(queue.isEmpty(), audience + " web has no root node");
            while (!queue.isEmpty()) {
                for (String n : ADJACENCY.getOrDefault(queue.poll(), Set.of())) {
                    if (visited.add(n)) queue.add(n);
                }
            }
            for (Skill skill : web) {
                assertTrue(visited.contains(skill.getId()),
                        "skill '" + skill.getId() + "' unreachable from any " + audience + " web root");
            }
        }
    }

    @Test
    void noEdgeCrossesASealedBorder() {
        for (Skill skill : BY_ID.values()) {
            if (!SPOKE_ORDER.contains(skill.getTree().getId())) continue;
            for (String edge : skill.getEdges()) {
                Skill other = BY_ID.get(edge);
                if (!SPOKE_ORDER.contains(other.getTree().getId())) continue;
                if (radius(skill) < HUB_RADIUS || radius(other) < HUB_RADIUS) continue; // hub/spoke zone
                int sa = sector(skill);
                int sb = sector(other);
                if (sa == sb) continue;
                String pair = SPOKE_ORDER.get(sa) + "|" + SPOKE_ORDER.get(sb);
                String reversed = SPOKE_ORDER.get(sb) + "|" + SPOKE_ORDER.get(sa);
                assertTrue(OPEN_PATHWAY_PAIRS.contains(pair) || OPEN_PATHWAY_PAIRS.contains(reversed),
                        "edge " + skill.getId() + " -> " + edge + " crosses a sealed border ("
                                + SPOKE_ORDER.get(sa) + "|" + SPOKE_ORDER.get(sb) + ")");
            }
        }
    }

    /** §3.4: every legacy edge survives as a path whose interior nodes are all fillers. */
    @Test
    void legacyConnectivityContractHolds() {
        for (String[] legacy : LEGACY_EDGES) {
            assertTrue(fillerPathExists(legacy[0], legacy[1]),
                    "legacy edge " + legacy[0] + " - " + legacy[1] + " has no all-filler path");
        }
    }

    private static boolean fillerPathExists(String from, String to) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        visited.add(from);
        queue.add(from);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String next : ADJACENCY.getOrDefault(current, Set.of())) {
                if (next.equals(to)) return true;
                Skill node = BY_ID.get(next);
                if (node.getSize() != Skill.Size.SMALL) continue; // interiors must be fillers
                if (visited.add(next)) queue.add(next);
            }
        }
        return false;
    }

    @Test
    void fillersFollowTheContentRule() {
        for (Skill skill : BY_ID.values()) {
            if (skill.getSize() != Skill.Size.SMALL) continue;
            assertEquals(1, skill.getEffects().size(), skill.getId() + ": fillers carry exactly one effect");
            assertEquals(1, skill.getMaxLevel(), skill.getId() + ": fillers are single-level");
            assertEquals(1, skill.getPointCost(), skill.getId() + ": fillers cost 1");
        }
    }

    @Test
    void newNodeLangKeysResolve() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(LANG_FILE)).getAsJsonObject();
        for (String tree : SPOKE_ORDER) {
            assertTrue(lang.has("skilltree.region." + tree + ".constellation"),
                    "missing constellation key for " + tree);
        }
        for (Skill skill : BY_ID.values()) {
            String name = skill.getDisplayName();
            if (name.startsWith("skill.")) {
                assertTrue(lang.has(name), skill.getId() + ": display key '" + name + "' unresolved in en_us");
            }
        }
    }

    private static double radius(Skill s) {
        return Math.hypot(s.getX(), s.getY());
    }

    /** 60° sector index into {@link #SPOKE_ORDER} (index 0 centered at −90°, clockwise, y-down). */
    private static int sector(Skill s) {
        double deg = Math.toDegrees(Math.atan2(s.getY(), s.getX()));
        int idx = (int) Math.floor((deg + 90 + 30) / 60.0);
        return Math.floorMod(idx, 6);
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
