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
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
        assertEquals(168, BY_ID.size(),
                "60 legacy notables + wizard_core + 100 fillers + 10 goblin/elf + 2 Apparition forks"
                        + " + 5 keystones");
        long fillers = BY_ID.values().stream().filter(s -> s.getSize() == Skill.Size.SMALL).count();
        assertEquals(100, fillers, "expected exactly 100 filler nodes");
        // One keystone per wizard tree except dark_arts, which is not expanded while its module
        // ships disabled. Pinned because a keystone is the payoff a whole branch routes toward:
        // silently dropping one would leave a tree with nothing at the end of it.
        long keystones = BY_ID.values().stream().filter(s -> s.getSize() == Skill.Size.KEYSTONE).count();
        assertEquals(5, keystones, "expected one keystone each in spell_mastery, wandlore,"
                + " magizoology, herbology and alchemy");
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

    // -- effects reach a real system, and the tooltip can name them ------------------------------

    /**
     * Every effect a shipped node declares must be one that some system actually consumes.
     *
     * <p>This is the test that would have caught the seventeen {@code <spell>_unlock} nodes. Each
     * declared a {@code spell_damage_bonus} or {@code spell_cooldown_reduction};
     * {@code SkillEffectCache} computed both correctly, and the only methods that exposed them had
     * no callers, because cast time read {@code PlayerSkillBonusData} — which carries per-category
     * maps and nothing else. The nodes cost points and did nothing, and nothing in the build said so.
     *
     * <p>Asks {@link SkillEffectSummary#isImplemented} rather than keeping a list here, so the
     * allowlist cannot drift away from the class that decides whether a tooltip line is printable.
     */
    @Test
    void everyShippedEffectTypeReachesARealSystem() {
        List<String> problems = new ArrayList<>();
        for (Skill skill : BY_ID.values()) {
            for (SkillEffect effect : skill.getEffects()) {
                if (!SkillEffectSummary.isImplemented(effect.type())) {
                    problems.add(skill.getId() + " declares '" + effect.type().getSerializedName()
                            + "', which no system consumes — the node would cost points and do nothing");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * Only three attribute ids are wired ({@code Skill#deriveNodeEffects}); any other silently
     * applies nothing, and the tooltip deliberately prints no line for it. Either way the node is
     * inert, so it must not ship.
     */
    @Test
    void everyPassiveAttributeNamesAWiredAttribute() {
        Set<String> wired = Set.of("max_health", "movement_speed", "armor");
        List<String> problems = new ArrayList<>();
        for (Skill skill : BY_ID.values()) {
            for (SkillEffect effect : skill.getEffects()) {
                if (effect instanceof SkillEffect.PassiveAttribute passive
                        && !wired.contains(passive.attributeId())) {
                    problems.add(skill.getId() + ": attributeId '" + passive.attributeId()
                            + "' is not one of " + wired);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * Every lang key the effect summary will ask for has to exist, or the tooltip renders a raw key
     * where the reason to buy the node should be.
     *
     * <p>Keys are derived here the same way {@link SkillEffectSummary} derives them. That is a
     * deliberate duplication of two short string concatenations: the alternative is rendering
     * {@code Component}s in a test and reading them back, which proves less and needs a live
     * language table.
     */
    @Test
    void everyEffectLineResolvesInEnUs() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(LANG_FILE)).getAsJsonObject();
        Set<String> required = new HashSet<>();
        for (Skill skill : BY_ID.values()) {
            for (SkillEffect effect : skill.getEffects()) {
                switch (effect) {
                    case SkillEffect.SpellDamageBonus e -> {
                        required.add("skill.wizards_and_beasts.effect.spell_damage");
                        required.add("spell.wizards_and_beasts." + e.spellId() + ".name");
                    }
                    case SkillEffect.SpellCooldownReduction e -> {
                        required.add("skill.wizards_and_beasts.effect.spell_cooldown");
                        required.add("spell.wizards_and_beasts." + e.spellId() + ".name");
                    }
                    case SkillEffect.CategoryDamageBonus e -> {
                        required.add("skill.wizards_and_beasts.effect.category_damage");
                        required.add(categoryKey(e.category().name()));
                    }
                    case SkillEffect.CategoryCooldownReduction e -> {
                        required.add("skill.wizards_and_beasts.effect.category_cooldown");
                        required.add(categoryKey(e.category().name()));
                    }
                    case SkillEffect.PassiveAttribute e ->
                            required.add("skill.wizards_and_beasts.effect.attribute." + e.attributeId());
                    case SkillEffect.GameplayBonus e -> {
                        required.add("skill.wizards_and_beasts.effect.gameplay_bonus");
                        required.add("skill.wizards_and_beasts.stat."
                                + e.stat().name().toLowerCase(Locale.ROOT));
                    }
                    case SkillEffect.UnlockAbility e -> {
                        required.add("skill.wizards_and_beasts.effect.unlock_ability");
                        required.add("skill.wizards_and_beasts.ability." + e.abilityId());
                    }
                    default -> { }
                }
            }
        }
        List<String> missing = required.stream().filter(key -> !lang.has(key)).sorted().toList();
        assertTrue(missing.isEmpty(), "unresolved effect-summary lang keys: " + missing);
    }

    private static String categoryKey(String category) {
        return "spell.wizards_and_beasts.category." + category.toLowerCase(Locale.ROOT);
    }
}
