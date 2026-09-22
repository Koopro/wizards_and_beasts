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
        assertEquals(84, BY_ID.size(),
                "the 2026-09-17 education rework: 84 nodes that each do something nameable, down from 107");
        // Zero, and it should stay zero. The 34 pathway nodes the webs used to be padded with each printed the
        // same sentence as several siblings; the rework replaced them with fewer, named nodes. A SMALL node is
        // still legal — pathwaysFollowTheContentRule and noSmallNodeGrantsARawAttribute constrain what one may
        // do — but nothing ships as one today.
        long fillers = BY_ID.values().stream().filter(s -> s.getSize() == Skill.Size.SMALL).count();
        assertEquals(0, fillers, "the education rework removed every pathway node");
        // One keystone per wizard tree except dark_arts, which is not expanded while its module
        // ships disabled. Pinned because a keystone is the payoff a whole branch routes toward:
        // silently dropping one would leave a tree with nothing at the end of it.
        long keystones = BY_ID.values().stream().filter(s -> s.getSize() == Skill.Size.KEYSTONE).count();
        assertEquals(12, keystones, "Polaris plus three in spell_mastery (wandlight, nonverbal casting and the"
                + " Patronus), one each in dark_arts, wandlore, magizoology and herbology, two in alchemy, and"
                + " one each in the two heritage webs");
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

    /**
     * No two nodes a player can see at once may sit on the same point.
     *
     * <p>This is the assertion whose absence let the 2026-09-17 layout regression ship. Deleting the
     * 34 pathway connectors removed the nodes that had been carrying the intermediate radii, so every
     * survivor in a spoke inherited its spoke's single anchor: 57 of 84 nodes landed on r=140 and 54
     * shared a position with a sibling. The Lumos line and the Defence line each drew as one dot, and
     * every other invariant here still passed, because none of them looks at whether two nodes are
     * distinguishable on screen.
     *
     * <p>Partitioned by {@link SkillTreeId.Audience} rather than asserted globally, because the
     * overlap between the two heritage webs is deliberate: {@code elf_bond} and {@code goblin_craft}
     * occupy the same coordinates on purpose, and a goblin never sees the elf web. Audience is the
     * grouping the schema already carries, so the exemption is expressed in terms of who can see what
     * rather than as a hand-maintained allowlist of colliding ids — which would have to be edited
     * every time a node moved, and would quietly re-admit exactly this bug.
     */
    @Test
    void everyNodeAPlayerCanSeeAtOnceHasItsOwnPosition() {
        Map<SkillTreeId.Audience, Map<String, List<String>>> byAudience = new HashMap<>();
        for (Skill skill : BY_ID.values()) {
            String point = String.format(Locale.ROOT, "%.1f,%.1f", skill.getX(), skill.getY());
            byAudience
                    .computeIfAbsent(skill.getTree().getAudience(), a -> new HashMap<>())
                    .computeIfAbsent(point, p -> new ArrayList<>())
                    .add(skill.getId());
        }

        List<String> collisions = new ArrayList<>();
        for (Map.Entry<SkillTreeId.Audience, Map<String, List<String>>> audience : byAudience.entrySet()) {
            for (Map.Entry<String, List<String>> point : audience.getValue().entrySet()) {
                if (point.getValue().size() > 1) {
                    List<String> ids = new ArrayList<>(point.getValue());
                    ids.sort(null);
                    collisions.add(audience.getKey() + " at (" + point.getKey() + "): "
                            + String.join(", ", ids));
                }
            }
        }
        collisions.sort(null);
        assertTrue(collisions.isEmpty(),
                "nodes visible together must not share a coordinate; regenerate with"
                        + " tools/skill_web_layout.py:\n  " + String.join("\n  ", collisions));
    }

    /**
     * Nodes are far enough apart to be separately clickable.
     *
     * <p>Distinct coordinates are not sufficient on their own: two nodes 3px apart are technically
     * distinct and still one blob to a player aiming at them. The largest sprite the chart draws is
     * the 26px keystone ({@code SkillTreeScreen.baseSpritePx}), so two centres closer than that
     * overlap outright, and the hit radius is half the sprite. Twenty-eight design units is that
     * width plus a little air, checked within an audience for the same reason as above.
     */
    @Test
    void nodesVisibleTogetherAreFarEnoughApartToClick() {
        double minimum = 28.0;
        Map<SkillTreeId.Audience, List<Skill>> byAudience = new HashMap<>();
        for (Skill skill : BY_ID.values()) {
            byAudience.computeIfAbsent(skill.getTree().getAudience(), a -> new ArrayList<>()).add(skill);
        }

        List<String> tooClose = new ArrayList<>();
        for (Map.Entry<SkillTreeId.Audience, List<Skill>> entry : byAudience.entrySet()) {
            List<Skill> web = entry.getValue();
            for (int i = 0; i < web.size(); i++) {
                for (int j = i + 1; j < web.size(); j++) {
                    Skill a = web.get(i);
                    Skill b = web.get(j);
                    double gap = Math.hypot(a.getX() - b.getX(), a.getY() - b.getY());
                    if (gap < minimum) {
                        tooClose.add(String.format(Locale.ROOT, "%s: %s / %s are %.1f apart",
                                entry.getKey(), a.getId(), b.getId(), gap));
                    }
                }
            }
        }
        tooClose.sort(null);
        assertTrue(tooClose.isEmpty(),
                "nodes visible together must be at least " + minimum + " apart:\n  "
                        + String.join("\n  ", tooClose));
    }

    /**
     * Progression runs outward: a prerequisite is always the node nearer Polaris.
     *
     * <p>The chart is polar and the hub is the origin, so radius is what a player reads as "how far
     * into this discipline am I". Before the repair the ladder ran backwards in two webs — wandlore's
     * depth-2 nodes sat at r=232 while its depth-5 keystone sat at r=140 — which draws a branch that
     * appears to grow back towards the centre as it advances. Depth here is BFS distance from
     * {@code wizard_core} over the same undirected adjacency the allocation gate uses.
     */
    @Test
    void everyBranchGrowsAwayFromPolaris() {
        Map<String, Integer> depth = new HashMap<>();
        depth.put("wizard_core", 0);
        Deque<String> queue = new ArrayDeque<>();
        queue.add("wizard_core");
        while (!queue.isEmpty()) {
            String current = queue.poll();
            List<String> neighbours = new ArrayList<>(ADJACENCY.getOrDefault(current, Set.of()));
            neighbours.sort(null);
            for (String neighbour : neighbours) {
                if (BY_ID.containsKey(neighbour) && !depth.containsKey(neighbour)) {
                    depth.put(neighbour, depth.get(current) + 1);
                    queue.add(neighbour);
                }
            }
        }

        List<String> inverted = new ArrayList<>();
        for (Skill skill : BY_ID.values()) {
            if (!SPOKE_ORDER.contains(skill.getTree().getId())) continue;
            Integer here = depth.get(skill.getId());
            if (here == null) continue;
            for (String edge : skill.getEdges()) {
                Skill other = BY_ID.get(edge);
                Integer there = depth.get(edge);
                if (other == null || there == null || there.equals(here)) continue;
                if (!SPOKE_ORDER.contains(other.getTree().getId())) continue;
                Skill nearer = here < there ? skill : other;
                Skill further = here < there ? other : skill;
                if (radius(nearer) >= radius(further)) {
                    inverted.add(String.format(Locale.ROOT,
                            "%s (depth %d, r=%.0f) sits no closer to Polaris than %s (depth %d, r=%.0f)",
                            nearer.getId(), Math.min(here, there), radius(nearer),
                            further.getId(), Math.max(here, there), radius(further)));
                }
            }
        }
        inverted.sort(null);
        assertTrue(inverted.isEmpty(),
                "a prerequisite must sit nearer the hub than what it unlocks:\n  "
                        + String.join("\n  ", inverted));
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

    /**
     * Every wizard web hangs off Polaris, and no node in it needs a detour through another discipline.
     *
     * <p>This replaced the §3.4 legacy-edge contract, which held that every pre-web edge survived as a path
     * whose interior nodes were all fillers. The 2026-09-17 education rework deleted the fillers, so that
     * contract protected a shape the data deliberately no longer has. What it was protecting <em>for</em> still
     * matters: no tree may be an island, and none may be reachable only by buying into its neighbour.
     */
    @Test
    void everyWizardTreeHangsFromTheHubWithoutCrossingAnother() {
        for (String tree : SPOKE_ORDER) {
            List<Skill> web = BY_ID.values().stream()
                    .filter(node -> node.getTree().getId().equals(tree)).toList();
            if (web.isEmpty()) {
                continue;
            }
            Set<String> reached = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            for (String neighbour : ADJACENCY.getOrDefault("wizard_core", Set.of())) {
                Skill node = BY_ID.get(neighbour);
                if (node != null && node.getTree().getId().equals(tree) && reached.add(neighbour)) {
                    queue.add(neighbour);
                }
            }
            assertFalse(queue.isEmpty(), tree + " has no trunk node joined to Polaris");
            while (!queue.isEmpty()) {
                for (String next : ADJACENCY.getOrDefault(queue.poll(), Set.of())) {
                    Skill node = BY_ID.get(next);
                    if (node != null && node.getTree().getId().equals(tree) && reached.add(next)) {
                        queue.add(next);
                    }
                }
            }
            for (Skill node : web) {
                assertTrue(reached.contains(node.getId()),
                        node.getId() + " is only reachable by leaving " + tree);
            }
        }
    }

    @Test
    void pathwaysFollowTheContentRule() {
        for (Skill skill : BY_ID.values()) {
            if (skill.getSize() != Skill.Size.SMALL) continue;
            assertEquals(1, skill.getEffects().size(), skill.getId() + ": pathways carry exactly one effect");
            assertEquals(1, skill.getMaxLevel(), skill.getId() + ": pathways are single-level");
            assertEquals(1, skill.getPointCost(), skill.getId() + ": pathways cost 1");
        }
    }

    /**
     * No small node may grant a raw attribute.
     *
     * <p>This is the shape rule that keeps the purge from being undone one node at a time. What made
     * the old web filler was not the node count on its own — it was that 41 of the 100 small nodes
     * each paid +0.5 max health or +0.5 armour, so the cheapest thing to do with a point was buy the
     * fourteenth half-heart. An attribute is the only effect type with no theme attached to it, and
     * therefore the only one that can be pasted onto a connector without anybody having to decide
     * what that connector is <em>for</em>. Pathways must pay in their region's own currency instead:
     * harvest luck in Herbology, misfires in Wandlore, beast resistance in Magizoology.
     *
     * <p>Notables and keystones are deliberately unrestricted — {@code herbal_vitality} and
     * {@code goblin_steelheart} are toughness nodes on purpose, and each pays a whole heart or a
     * whole point of armour per level rather than a half.
     */
    @Test
    void noSmallNodeGrantsARawAttribute() {
        List<String> problems = new ArrayList<>();
        for (Skill skill : BY_ID.values()) {
            if (skill.getSize() != Skill.Size.SMALL) continue;
            for (SkillEffect effect : skill.getEffects()) {
                if (effect instanceof SkillEffect.PassiveAttribute passive) {
                    problems.add(skill.getId() + " grants " + passive.attributeId()
                            + " — a pathway node may not pay in raw attributes");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * Every {@code <spell>_unlock} node must actually teach the spell it is named for.
     *
     * <p>The web shipped for months with {@code crucio_unlock}, {@code imperio_unlock} and
     * {@code avada_kedavra_unlock} granting a cooldown shave and nothing else: the three most
     * exciting-looking nodes in the tree were passives wearing a spell's name, and a player could
     * only find that out by spending four to six points. Naming is a promise here, so it is checked.
     */
    @Test
    void everyUnlockNodeTeachesItsSpell() {
        List<String> problems = new ArrayList<>();
        for (Skill skill : BY_ID.values()) {
            if (!skill.getId().endsWith("_unlock")) continue;
            String named = skill.getId().substring(0, skill.getId().length() - "_unlock".length());
            // A prefix match, not equality, because a node may be named for the incantation's first
            // word: `wingardium_unlock` teaches `wingardium_leviosa`.
            boolean teaches = skill.getEffects().stream()
                    .anyMatch(e -> e instanceof SkillEffect.LearnSpell learn && learn.spellId().startsWith(named));
            if (!teaches) {
                problems.add(skill.getId() + " does not teach '" + named
                        + "' — rename the node or give it a learn_spell effect");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
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
     * A node may only teach a spell the cast pipeline will actually fire.
     *
     * <p>The spell datapack ships 152 definitions of which 27 are implemented; the rest carry
     * {@code "implementationState": "coming_soon"} and exist so the canon roster is registered and
     * nameable. Wiring one into a {@code learn_spell} would spend a player's points on a spell that
     * appears in their book and is refused at the wand — the same defect as the misnamed
     * {@code *_unlock} nodes, one layer further in, and invisible in every other test because the id
     * resolves and the lang key exists.
     *
     * <p>The six bespoke Java spells have no JSON at all and are allowlisted by name, because the
     * datapack cannot answer for them.
     */
    @Test
    void everyTaughtSpellIsImplemented() throws IOException {
        Set<String> javaSpells = Set.of("avada_kedavra", "expecto_patronum", "imperio",
                "obscurus_grasp", "obscurus_surge", "protego");
        Path spellDir = Path.of("src", "main", "resources", "data", "wizards_and_beasts", "spells");
        Set<String> implemented = new HashSet<>(javaSpells);
        try (Stream<Path> files = Files.walk(spellDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject spell = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                String state = spell.has("implementationState")
                        ? spell.get("implementationState").getAsString() : "implemented";
                if (!"coming_soon".equals(state)) {
                    String name = file.getFileName().toString();
                    implemented.add(name.substring(0, name.length() - ".json".length()));
                }
            }
        }
        List<String> problems = new ArrayList<>();
        for (Skill skill : BY_ID.values()) {
            for (SkillEffect effect : skill.getEffects()) {
                if (effect instanceof SkillEffect.LearnSpell learn
                        && !implemented.contains(learn.spellId())) {
                    problems.add(skill.getId() + " teaches '" + learn.spellId()
                            + "', which is not an implemented spell");
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
