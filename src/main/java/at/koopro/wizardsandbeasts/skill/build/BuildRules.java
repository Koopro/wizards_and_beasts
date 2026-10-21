package at.koopro.wizardsandbeasts.skill.build;

import at.koopro.wizardsandbeasts.module.Module;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The one allocation rule builds add to the web: how a capstone is reached.
 *
 * <p>Three conditions, checked in the order a player can do something about them last:
 * <ol>
 *   <li>{@code build_module_disabled} — the build's mechanics live in a module the server switched off. Nothing
 *       the player does changes it, so it is said first.</li>
 *   <li>{@code capstone_taken} — the player already holds another build's capstone. <b>One mastery per
 *       wizard</b>: a capstone is what makes "I am a Duelist" a statement rather than a tally, and two of them
 *       make it a shrug. Unlearning the first one (cheap, see {@link RespecPricing}) reopens the choice.</li>
 *   <li>{@code capstone_shallow} — fewer than {@link BuildPathway#capstoneDepth} of the build's advanced nodes
 *       are allocated. A capstone is a reward for having walked the road, not a node you can path straight to
 *       through adjacency alone.</li>
 * </ol>
 *
 * <p>Pure: no player, no registry. {@code SkillSystemAPI.evaluateUnlock} feeds it the allocation map, the loaded
 * archetypes and the module switch.
 */
@NullMarked
public final class BuildRules {

    public static final String BUILD_MODULE_DISABLED = "build_module_disabled";
    public static final String CAPSTONE_TAKEN = "capstone_taken";
    public static final String CAPSTONE_SHALLOW = "capstone_shallow";

    private BuildRules() {}

    /** The archetype whose capstone {@code nodeId} is, if any. */
    public static Optional<Archetype> capstoneOwner(String nodeId, Collection<Archetype> archetypes) {
        for (Archetype archetype : archetypes) {
            if (archetype.capstone().equals(nodeId)) {
                return Optional.of(archetype);
            }
        }
        return Optional.empty();
    }

    /**
     * Why {@code nodeId} may not be allocated as a capstone, or {@code null} when it may (or is not a capstone, or
     * is already held — further levels and re-checks never trip the gate).
     */
    public static @Nullable String capstoneRefusal(String nodeId, Map<String, Integer> allocated,
                                                   Collection<Archetype> archetypes, Predicate<Module> enabled) {
        Optional<Archetype> owner = capstoneOwner(nodeId, archetypes);
        if (owner.isEmpty() || allocated.getOrDefault(nodeId, 0) > 0) {
            return null;
        }
        Archetype archetype = owner.get();
        if (!modulesOn(archetype, enabled)) {
            return BUILD_MODULE_DISABLED;
        }
        if (heldCapstone(allocated, archetypes).isPresent()) {
            return CAPSTONE_TAKEN;
        }
        if (allocatedIn(archetype, BuildTier.ADVANCED, allocated) < archetype.pathway().capstoneDepth()) {
            return CAPSTONE_SHALLOW;
        }
        return null;
    }

    /** True when every module the build lists is on. */
    public static boolean modulesOn(Archetype archetype, Predicate<Module> enabled) {
        for (Module module : archetype.pathway().modules()) {
            if (!enabled.test(module)) {
                return false;
            }
        }
        return true;
    }

    /** The archetype whose capstone the player holds. At most one can be allocated through the web. */
    public static Optional<Archetype> heldCapstone(Map<String, Integer> allocated, Collection<Archetype> archetypes) {
        for (Archetype archetype : archetypes) {
            if (allocated.getOrDefault(archetype.capstone(), 0) > 0) {
                return Optional.of(archetype);
            }
        }
        return Optional.empty();
    }

    /** How many of a tier's nodes the player holds at level ≥ 1. The capstone tier counts 0 or 1. */
    public static int allocatedIn(Archetype archetype, BuildTier tier, Map<String, Integer> allocated) {
        if (tier == BuildTier.CAPSTONE) {
            return allocated.getOrDefault(archetype.capstone(), 0) > 0 ? 1 : 0;
        }
        int count = 0;
        for (String node : archetype.pathway().nodes(tier)) {
            if (allocated.getOrDefault(node, 0) > 0) {
                count++;
            }
        }
        return count;
    }

    /** Number of nodes in a tier; the capstone tier is always one. */
    public static int sizeOf(Archetype archetype, BuildTier tier) {
        return tier == BuildTier.CAPSTONE ? 1 : archetype.pathway().nodes(tier).size();
    }
}
