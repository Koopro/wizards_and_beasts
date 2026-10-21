package at.koopro.wizardsandbeasts.skill.build;

import at.koopro.wizardsandbeasts.module.Module;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The build layer's pure rules on a toy web:
 * <pre>
 *   root ─ a1 ─ a2 ─ a3 ─ CAP_A
 *     └─── b1 ─ b2 ─ CAP_B
 * </pre>
 * Build A: early a1, mid a2, advanced a3 (+ b2 as a shared advanced node), depth 2. Build B: early b1, advanced b2,
 * depth 1, needs DARK_ARTS.
 */
class BuildRulesTest {

    private static final Archetype A = new Archetype(Identifier.fromNamespaceAndPath("wizards_and_beasts", "a"),
            "a.name", new BuildPathway(List.of("a1"), List.of("a2"), List.of("a3", "b2"), 2, List.of()), "cap_a");
    private static final Archetype B = new Archetype(Identifier.fromNamespaceAndPath("wizards_and_beasts", "b"),
            "b.name", new BuildPathway(List.of("b1"), List.of(), List.of("b2"), 1, List.of(Module.DARK_ARTS)), "cap_b");
    private static final List<Archetype> BOTH = List.of(A, B);

    private static final Map<String, Set<String>> EDGES = Map.of(
            "root", Set.of("a1", "b1"),
            "a1", Set.of("root", "a2"),
            "a2", Set.of("a1", "a3"),
            "a3", Set.of("a2", "cap_a"),
            "cap_a", Set.of("a3"),
            "b1", Set.of("root", "b2"),
            "b2", Set.of("b1", "cap_b"),
            "cap_b", Set.of("b2"));
    private static final Function<String, java.util.Collection<String>> NEIGHBOURS =
            id -> EDGES.getOrDefault(id, Set.of());

    private static Map<String, Integer> alloc(String... ids) {
        Map<String, Integer> map = new HashMap<>();
        for (String id : ids) {
            map.put(id, 1);
        }
        return map;
    }

    // ── capstone gate ───────────────────────────────────────────────────────────────────────────

    @Test
    void aCapstoneNeedsItsDepthOfAdvancedNodes() {
        assertEquals(BuildRules.CAPSTONE_SHALLOW,
                BuildRules.capstoneRefusal("cap_a", alloc("root", "a1", "a2", "a3"), BOTH, m -> true),
                "one advanced node is short of a depth of two");
        assertNull(BuildRules.capstoneRefusal("cap_a", alloc("root", "a1", "a2", "a3", "b1", "b2"), BOTH, m -> true),
                "a shared advanced node counts toward every build that lists it");
    }

    @Test
    void oneMasteryPerWizard() {
        Map<String, Integer> masterOfB = alloc("root", "b1", "b2", "cap_b", "a1", "a2", "a3");
        assertEquals(BuildRules.CAPSTONE_TAKEN, BuildRules.capstoneRefusal("cap_a", masterOfB, BOTH, m -> true));
        assertEquals(Optional.of(B), BuildRules.heldCapstone(masterOfB, BOTH));
    }

    @Test
    void aSealedBuildSaysSoFirst() {
        assertEquals(BuildRules.BUILD_MODULE_DISABLED,
                BuildRules.capstoneRefusal("cap_b", alloc("root"), BOTH, m -> m != Module.DARK_ARTS),
                "module first: nothing the player does opens it");
        assertNull(BuildRules.capstoneRefusal("cap_b", alloc("root", "b1", "b2"), BOTH, m -> true));
    }

    @Test
    void ordinaryNodesAndHeldCapstonesNeverTripTheGate() {
        assertNull(BuildRules.capstoneRefusal("a2", alloc(), BOTH, m -> false));
        assertNull(BuildRules.capstoneRefusal("cap_a", alloc("cap_a"), BOTH, m -> false),
                "a held capstone is grandfathered; the gate is for new allocations");
    }

    @Test
    void tiersAreReadFromThePathway() {
        assertEquals(Optional.of(BuildTier.EARLY), A.tierOf("a1"));
        assertEquals(Optional.of(BuildTier.ADVANCED), A.tierOf("b2"));
        assertEquals(Optional.of(BuildTier.CAPSTONE), A.tierOf("cap_a"));
        assertTrue(A.tierOf("b1").isEmpty());
        assertEquals(2, BuildRules.allocatedIn(A, BuildTier.ADVANCED, alloc("a3", "b2", "a1")));
    }

    // ── connectivity ────────────────────────────────────────────────────────────────────────────

    private static Set<String> stranded(String node, Map<String, Integer> allocated) {
        return WebConnectivity.strandedBy(node, allocated, NEIGHBOURS, "root"::equals, EDGES::containsKey);
    }

    @Test
    void aLeafUnlearnsFreely() {
        assertTrue(stranded("a3", alloc("root", "a1", "a2", "a3")).isEmpty());
    }

    @Test
    void aBridgeCannotBeUnlearnedUnderWhatHangsFromIt() {
        assertEquals(Set.of("a2", "a3"), stranded("a1", alloc("root", "a1", "a2", "a3")));
        assertEquals(Set.of("a1", "b1"), stranded("root", alloc("root", "a1", "b1")));
    }

    @Test
    void aLevelOffAMultiLevelNodeNeverStrands() {
        Map<String, Integer> allocated = alloc("root", "a1", "a2");
        allocated.put("a1", 2);
        assertTrue(stranded("a1", allocated).isEmpty());
    }

    @Test
    void deletedIdsAreNotCountedAsStranded() {
        Map<String, Integer> allocated = alloc("root", "a1", "ghost_node");
        assertTrue(stranded("a1", allocated).isEmpty(), "the login migration owns ids that no longer resolve");
    }

    // ── pricing ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void unlearningIsPricedPerPointAndNeverAboveStartingOver() {
        assertEquals(29L * 3, RespecPricing.unlearnFee(3, 29, 493, true));
        assertEquals(493L, RespecPricing.unlearnFee(40, 29, 493, true), "capped at the full respec fee");
        assertEquals(0L, RespecPricing.unlearnFee(3, 29, 493, false), "no vault, no fee");
        assertEquals(0L, RespecPricing.unlearnFee(3, 0, 493, true));
    }

    @Test
    void theFirstFullRespecIsFree() {
        assertEquals(0L, RespecPricing.fullRespecFee(true, 493, true));
        assertEquals(493L, RespecPricing.fullRespecFee(false, 493, true));
        assertEquals(0L, RespecPricing.fullRespecFee(false, 493, false));
    }

    // ── profile ─────────────────────────────────────────────────────────────────────────────────

    private static BuildProfile profile(Map<String, Integer> allocated, Optional<Identifier> declared) {
        return BuildProfile.compute(allocated, BOTH, declared, NEIGHBOURS, "root"::equals, m -> true);
    }

    @Test
    void aCapstoneMakesTheMaster() {
        BuildProfile p = profile(alloc("root", "b1", "b2", "cap_b", "a1", "a2"), Optional.of(A.id()));
        assertEquals(BuildProfile.Status.MASTER, p.status());
        assertEquals(B, p.current(), "mastery outranks a declaration");
    }

    @Test
    void aDeclarationWithoutCapstoneIsDeclared() {
        BuildProfile p = profile(alloc("root", "b1"), Optional.of(A.id()));
        assertEquals(BuildProfile.Status.DECLARED, p.status());
        assertEquals(A, p.current());
    }

    @Test
    void aClearLeadIsLeaningAndATieIsUndecided() {
        assertEquals(BuildProfile.Status.LEANING, profile(alloc("root", "a1", "a2"), Optional.empty()).status());
        assertEquals(BuildProfile.Status.UNDECIDED, profile(alloc("root", "a1"), Optional.empty()).status(),
                "one early node is a first step, not a build");
        assertEquals(BuildProfile.Status.UNDECIDED, profile(alloc("root"), Optional.empty()).status());
    }

    @Test
    void nextChoicesAreAdjacentAndTheCapstoneOnlyWhenOpen() {
        BuildProfile p = profile(alloc("root", "a1", "a2", "a3"), Optional.empty());
        BuildProfile.PathwayProgress a = p.progressOf(A.id()).orElseThrow();
        assertFalse(a.nextChoices().contains("cap_a"), "depth 2 not met: the capstone is not a next choice yet");
        assertEquals(BuildRules.CAPSTONE_SHALLOW, a.capstoneRefusal());
        assertEquals(1, a.advancedStillNeeded());
        assertTrue(a.nextChoices().isEmpty() || !a.nextChoices().contains("b2"),
                "b2 is not adjacent to anything allocated");

        BuildProfile deeper = profile(alloc("root", "a1", "a2", "a3", "b1", "b2"), Optional.empty());
        assertTrue(deeper.progressOf(A.id()).orElseThrow().nextChoices().contains("cap_a"));
    }

    @Test
    void aSealedBuildIsLeftOffTheSheet() {
        BuildProfile p = BuildProfile.compute(alloc("root", "b1", "b2"), BOTH, Optional.empty(), NEIGHBOURS,
                "root"::equals, m -> m != Module.DARK_ARTS);
        assertTrue(p.progressOf(B.id()).isEmpty());
    }
}
