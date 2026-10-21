package at.koopro.wizardsandbeasts.skill.build;

import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillEffect;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.vocation.VocationDefinition;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped builds, checked against the shipped web: every node a pathway names exists, every capstone is a
 * keystone hanging off its own road and belongs to one build only, every build has a node in every tier, and —
 * the balance check — every capstone is reachable under the point cap with room left for a second build.
 */
class ArchetypeDataTest {

    private static final Path ROOT = Path.of("src", "main", "resources", "data", "wizards_and_beasts");
    private static final Map<String, Skill> NODES = new HashMap<>();
    private static final Map<String, Set<String>> ADJ = new HashMap<>();
    private static final List<Archetype> BUILDS = new ArrayList<>();
    /** A capstone should cost no more than this from Polaris, so a 60-point wizard can still dabble elsewhere. */
    private static final int MAX_CAPSTONE_ROUTE = 40;

    @BeforeAll
    static void load() throws IOException {
        try (Stream<Path> files = Files.walk(ROOT.resolve("skill_nodes"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                Skill skill = Skill.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
                        .getOrThrow(AssertionError::new);
                NODES.put(skill.getId(), skill);
            }
        }
        for (Skill skill : NODES.values()) {
            for (String edge : skill.getEdges()) {
                ADJ.computeIfAbsent(skill.getId(), k -> new HashSet<>()).add(edge);
                ADJ.computeIfAbsent(edge, k -> new HashSet<>()).add(skill.getId());
            }
        }
        try (Stream<Path> files = Files.list(ROOT.resolve("vocations"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                VocationDefinition def = VocationDefinition.CODEC.parse(JsonOps.INSTANCE,
                                JsonParser.parseString(Files.readString(file)))
                        .getOrThrow(msg -> new AssertionError(file + ": " + msg));
                def.archetype().ifPresent(BUILDS::add);
            }
        }
    }

    @Test
    void theSevenBuildsShip() {
        Set<String> ids = new HashSet<>();
        BUILDS.forEach(b -> ids.add(b.id().getPath()));
        assertEquals(Set.of("duelist", "wandlore", "magizoologist", "dark_arts", "scholar", "herbologist", "explorer"),
                ids);
    }

    @Test
    void everyPathwayNodeExistsAndEveryTierHasOne() {
        for (Archetype build : BUILDS) {
            for (BuildTier tier : List.of(BuildTier.EARLY, BuildTier.MID, BuildTier.ADVANCED)) {
                List<String> nodes = build.pathway().nodes(tier);
                assertFalse(nodes.isEmpty(), build.id() + " has no " + tier + " nodes");
                for (String node : nodes) {
                    assertTrue(NODES.containsKey(node), build.id() + " names missing node " + node);
                }
            }
            assertTrue(build.pathway().capstoneDepth() >= 1
                            && build.pathway().capstoneDepth() <= build.pathway().advanced().size(),
                    build.id() + ": capstoneDepth must be reachable from its advanced list");
        }
    }

    @Test
    void capstonesAreKeystonesOnTheirOwnRoadAndBelongToOneBuild() {
        Set<String> seen = new HashSet<>();
        for (Archetype build : BUILDS) {
            Skill capstone = NODES.get(build.capstone());
            assertTrue(capstone != null, build.id() + ": capstone " + build.capstone() + " is not a node");
            assertEquals(Skill.Size.KEYSTONE, capstone.getSize(), build.id() + ": a capstone is drawn as a keystone");
            assertTrue(seen.add(build.capstone()), build.capstone() + " is the capstone of two builds");
            boolean onRoad = ADJ.getOrDefault(build.capstone(), Set.of()).stream().anyMatch(build::contains);
            assertTrue(onRoad, build.id() + ": the capstone must hang off one of the build's own nodes");
            for (Archetype other : BUILDS) {
                if (other != build) {
                    assertFalse(other.pathway().nodes(BuildTier.EARLY).contains(build.capstone())
                                    || other.pathway().nodes(BuildTier.MID).contains(build.capstone())
                                    || other.pathway().nodes(BuildTier.ADVANCED).contains(build.capstone()),
                            build.capstone() + " is a capstone and also a road node of " + other.id());
                }
            }
        }
    }

    /**
     * New capstones grant a mechanic (an ability flag a system reads), never a bare percentage — the brief's whole
     * point. The Dark Arts capstone is the existing Avada Kedavra node, which teaches a spell.
     */
    @Test
    void capstonesGrantMechanicsNotPercentages() {
        for (Archetype build : BUILDS) {
            Skill capstone = NODES.get(build.capstone());
            boolean mechanic = capstone.getEffects().stream().anyMatch(e ->
                    e instanceof SkillEffect.UnlockAbility || e instanceof SkillEffect.LearnSpell
                            || e instanceof SkillEffect.GrantAbility);
            boolean percentOnly = capstone.getEffects().stream().allMatch(e ->
                    e instanceof SkillEffect.GameplayBonus || e instanceof SkillEffect.CategoryDamageBonus
                            || e instanceof SkillEffect.CategoryCooldownReduction
                            || e instanceof SkillEffect.PassiveAttribute);
            assertTrue(mechanic && !percentOnly, build.id() + ": capstone must do something, not add a number");
        }
    }

    /**
     * Cheapest cost (node point costs, level 1) from Polaris to the capstone, plus the cheapest advanced nodes the
     * depth rule still needs. Under {@link #MAX_CAPSTONE_ROUTE}, so every build fits the 60-point cap with a third of
     * it left over.
     */
    @Test
    void everyCapstoneFitsUnderThePointCapWithRoomToSpare() {
        for (Archetype build : BUILDS) {
            Map<String, Integer> dist = new HashMap<>();
            Map<String, String> prev = new HashMap<>();
            PriorityQueue<String> queue = new PriorityQueue<>(Comparator.comparingInt(dist::get));
            dist.put("wizard_core", NODES.get("wizard_core").getPointCost());
            queue.add("wizard_core");
            while (!queue.isEmpty()) {
                String here = queue.poll();
                for (String next : ADJ.getOrDefault(here, Set.of())) {
                    int cost = dist.get(here) + NODES.get(next).getPointCost();
                    if (cost < dist.getOrDefault(next, Integer.MAX_VALUE)) {
                        dist.put(next, cost);
                        prev.put(next, here);
                        queue.remove(next);
                        queue.add(next);
                    }
                }
            }
            Set<String> route = new HashSet<>();
            for (String at = build.capstone(); at != null; at = prev.get(at)) {
                route.add(at);
            }
            int total = dist.get(build.capstone());
            int advancedOnRoute = (int) build.pathway().advanced().stream().filter(route::contains).count();
            int stillNeeded = Math.max(0, build.pathway().capstoneDepth() - advancedOnRoute);
            List<Integer> extra = build.pathway().advanced().stream().filter(n -> !route.contains(n))
                    .map(n -> dist.get(n) == null ? 99 : NODES.get(n).getPointCost()).sorted().toList();
            for (int i = 0; i < stillNeeded; i++) {
                total += extra.get(i);
            }
            assertTrue(total <= MAX_CAPSTONE_ROUTE && total <= SkillSystemAPI.MAX_SKILL_POINTS,
                    build.id() + ": capstone costs at least " + total + " points");
        }
    }
}
