package at.koopro.wizardsandbeasts.skill.build;

import at.koopro.wizardsandbeasts.module.Module;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * What a player's web says about them, in the terms a player uses: "I am a Duelist", "I am leaning Beastmaster".
 *
 * <p>Read by the character sheet and {@code /wandb skill build}. Derived every time from the allocation map — there
 * is no stored "build" to drift out of step with the nodes.
 *
 * <h2>Which build is "current"</h2>
 * <ol>
 *   <li>{@link Status#MASTER} — the player holds a capstone. That archetype, whatever else they took.</li>
 *   <li>{@link Status#DECLARED} — no capstone, but a declared vocation that is a build.</li>
 *   <li>{@link Status#LEANING} — neither, and one pathway clearly leads ({@link #LEANING_THRESHOLD}).</li>
 *   <li>{@link Status#UNDECIDED} — nothing yet.</li>
 * </ol>
 */
@NullMarked
public record BuildProfile(@Nullable Archetype current, Status status, List<PathwayProgress> pathways) {

    public enum Status { MASTER, DECLARED, LEANING, UNDECIDED }

    /** Weighted pathway score at which an undeclared player is said to be leaning toward a build. */
    public static final int LEANING_THRESHOLD = 3;
    /** How many next choices the sheet lists per pathway; more is a node list, not advice. */
    public static final int MAX_CHOICES = 3;

    /**
     * One build's progress.
     *
     * @param counts       allocated nodes per tier, indexed by {@link BuildTier#ordinal()}
     * @param sizes        nodes per tier, same indexing
     * @param nextChoices  pathway nodes the player could allocate next (adjacent, not yet taken), in tier order
     * @param capstoneRefusal why the capstone is not open yet ({@link BuildRules} reason), or null when it is open
     *                        or already held
     */
    public record PathwayProgress(Archetype archetype, int[] counts, int[] sizes, int score,
                                  List<String> nextChoices, @Nullable String capstoneRefusal) {
        public int count(BuildTier tier) { return counts[tier.ordinal()]; }
        public int size(BuildTier tier) { return sizes[tier.ordinal()]; }
        public boolean capstoneHeld() { return count(BuildTier.CAPSTONE) > 0; }
        /** Advanced nodes still needed before the capstone opens; 0 once the depth is met. */
        public int advancedStillNeeded() {
            return Math.max(0, archetype.pathway().capstoneDepth() - count(BuildTier.ADVANCED));
        }
    }

    public Optional<PathwayProgress> progressOf(Identifier archetypeId) {
        return pathways.stream().filter(p -> p.archetype().id().equals(archetypeId)).findFirst();
    }

    /**
     * @param allocated  node id → level
     * @param archetypes every loaded build
     * @param declared   the player's declared vocation, if any
     * @param neighbours web adjacency
     * @param isRoot     web roots
     * @param enabled    module switch; a build whose modules are off is left out entirely
     */
    public static BuildProfile compute(Map<String, Integer> allocated, Collection<Archetype> archetypes,
                                       Optional<Identifier> declared,
                                       Function<String, Collection<String>> neighbours,
                                       Predicate<String> isRoot, Predicate<Module> enabled) {
        List<Archetype> live = archetypes.stream().filter(a -> BuildRules.modulesOn(a, enabled)).toList();
        List<PathwayProgress> progress = new ArrayList<>();
        for (Archetype archetype : live) {
            progress.add(progressFor(archetype, allocated, live, neighbours, isRoot, enabled));
        }
        progress.sort(Comparator.comparingInt(PathwayProgress::score).reversed()
                .thenComparing(p -> p.archetype().id().toString()));

        Optional<Archetype> master = BuildRules.heldCapstone(allocated, live);
        if (master.isPresent()) {
            return new BuildProfile(master.get(), Status.MASTER, active(progress));
        }
        Optional<Archetype> declaredBuild = declared.flatMap(id -> live.stream()
                .filter(a -> a.id().equals(id)).findFirst());
        if (declaredBuild.isPresent()) {
            return new BuildProfile(declaredBuild.get(), Status.DECLARED, active(progress));
        }
        if (!progress.isEmpty() && progress.get(0).score() >= LEANING_THRESHOLD
                && (progress.size() < 2 || progress.get(0).score() > progress.get(1).score())) {
            return new BuildProfile(progress.get(0).archetype(), Status.LEANING, active(progress));
        }
        return new BuildProfile(null, Status.UNDECIDED, active(progress));
    }

    /** Pathways the player has set foot on; an untouched build is not an "active pathway". */
    private static List<PathwayProgress> active(List<PathwayProgress> all) {
        return all.stream().filter(p -> p.score() > 0).toList();
    }

    private static PathwayProgress progressFor(Archetype archetype, Map<String, Integer> allocated,
                                               Collection<Archetype> live,
                                               Function<String, Collection<String>> neighbours,
                                               Predicate<String> isRoot, Predicate<Module> enabled) {
        BuildTier[] tiers = BuildTier.values();
        int[] counts = new int[tiers.length];
        int[] sizes = new int[tiers.length];
        int score = 0;
        for (BuildTier tier : tiers) {
            counts[tier.ordinal()] = BuildRules.allocatedIn(archetype, tier, allocated);
            sizes[tier.ordinal()] = BuildRules.sizeOf(archetype, tier);
            score += counts[tier.ordinal()] * weight(tier);
        }

        List<String> choices = new ArrayList<>();
        for (BuildTier tier : tiers) {
            List<String> nodes = tier == BuildTier.CAPSTONE
                    ? List.of(archetype.capstone()) : archetype.pathway().nodes(tier);
            for (String node : nodes) {
                if (choices.size() >= MAX_CHOICES) break;
                if (allocated.getOrDefault(node, 0) > 0) continue;
                if (!isRoot.test(node) && !touchesAllocated(node, allocated, neighbours)) continue;
                if (tier == BuildTier.CAPSTONE
                        && BuildRules.capstoneRefusal(node, allocated, live, enabled) != null) continue;
                choices.add(node);
            }
        }
        String refusal = BuildRules.capstoneRefusal(archetype.capstone(), allocated, live, enabled);
        return new PathwayProgress(archetype, counts, sizes, score, List.copyOf(choices), refusal);
    }

    private static boolean touchesAllocated(String node, Map<String, Integer> allocated,
                                            Function<String, Collection<String>> neighbours) {
        for (String next : neighbours.apply(node)) {
            if (allocated.getOrDefault(next, 0) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Deeper nodes say more about a build than entry nodes everyone passes through. */
    private static int weight(BuildTier tier) {
        return switch (tier) {
            case EARLY -> 1;
            case MID -> 2;
            case ADVANCED -> 3;
            case CAPSTONE -> 10;
        };
    }
}
