package at.koopro.wizardsandbeasts.module;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which modules lean on which — only where the game's own gates already make it so. Every edge below names the
 * gate that enforces it; none is a design wish. A new edge needs the same: a line of code that refuses (or loses
 * something) when the dependency is off, cited here, and a GameTest that proves it.
 *
 * <ul>
 *   <li>{@link Kind#REQUIRES}: the dependent cannot run at all without the dependency. Enabling it while the
 *       dependency is off is refused; disabling the dependency disables the dependent too (said first, never
 *       silently).</li>
 *   <li>{@link Kind#PARTIAL}: the dependent runs, but loses one named part. Changes are allowed and warned about.</li>
 * </ul>
 *
 * <p>Pure: no server, no world. Shared by {@code ModuleStateService} (which enforces it), the Control Center (which
 * explains it) and the profile planner.
 */
@NullMarked
public final class ModuleDependencies {

    public enum Kind { REQUIRES, PARTIAL }

    /**
     * @param evidence where the game enforces it (class and method), for the docs and the detail page
     */
    public record Edge(Module dependent, Module dependency, Kind kind, String evidence) {

        /** "Cannot enable Apparition because Player abilities is disabled." */
        public String blockedKey() {
            return key("blocked");
        }

        /** "Disabling Player abilities will also disable Apparition." / "… the Ministry stops issuing fines." */
        public String effectKey() {
            return key("effect");
        }

        private String key(String suffix) {
            return "module.wizards_and_beasts.dependency." + dependent.name().toLowerCase(Locale.ROOT) + "."
                    + dependency.name().toLowerCase(Locale.ROOT) + "." + suffix;
        }
    }

    private static final List<Edge> EDGES = List.of(
            new Edge(Module.APPARITION, Module.PLAYER_ABILITIES, Kind.REQUIRES,
                    "ApparitionServerLogic.evaluateStart refuses unless both are on"),
            new Edge(Module.MINISTRY, Module.GRINGOTTS, Kind.PARTIAL,
                    "MinistryFines.isActive: no vault to bill, no fines"),
            new Edge(Module.BESTIARY, Module.MAGIZOOLOGY, Kind.PARTIAL,
                    "BestiaryHarvestLootModifier.isActive: study-gated drops need both"),
            new Edge(Module.MAGIZOOLOGY, Module.BESTIARY, Kind.PARTIAL,
                    "BestiaryHarvestLootModifier.isActive: study-gated drops need both"));

    private ModuleDependencies() {}

    public static List<Edge> all() {
        return EDGES;
    }

    /** What {@code module} leans on. */
    public static List<Edge> dependenciesOf(Module module) {
        return EDGES.stream().filter(e -> e.dependent() == module).toList();
    }

    /** What leans on {@code module}. */
    public static List<Edge> dependantsOf(Module module) {
        return EDGES.stream().filter(e -> e.dependency() == module).toList();
    }

    /**
     * The answer to "may {@code module} move to {@code target}, given {@code states}?".
     *
     * @param blockedBy   REQUIRES edges whose dependency is off — the change is refused while any exist
     * @param cascade     modules that must go off with it (REQUIRES dependants currently on), in safe order
     * @param weakened    PARTIAL edges that will lose their part (warned, allowed)
     */
    public record Check(List<Edge> blockedBy, List<Module> cascade, List<Edge> weakened) {

        public boolean allowed() {
            return blockedBy.isEmpty();
        }

        /** Whether the administrator should be told something before this goes through. */
        public boolean hasConsequences() {
            return !cascade.isEmpty() || !weakened.isEmpty();
        }
    }

    public static Check check(Map<Module, ModuleState> states, Module module, ModuleState target) {
        boolean opening = target.grantsAccess();
        boolean wasOpen = states.getOrDefault(module, ModuleState.DISABLED).grantsAccess();
        List<Edge> blocked = new ArrayList<>();
        List<Edge> weakened = new ArrayList<>();
        List<Module> cascade = new ArrayList<>();
        if (opening) {
            for (Edge edge : dependenciesOf(module)) {
                boolean dependencyOn = states.getOrDefault(edge.dependency(), ModuleState.DISABLED).grantsAccess();
                if (!dependencyOn) {
                    if (edge.kind() == Kind.REQUIRES) {
                        blocked.add(edge);
                    } else {
                        weakened.add(edge);
                    }
                }
            }
        } else if (wasOpen) {
            collectCascade(states, module, cascade, weakened, EnumSet.of(module));
        }
        return new Check(List.copyOf(blocked), List.copyOf(cascade), List.copyOf(weakened));
    }

    /** Depth-first: a dependant's own dependants go off before it does, so nothing ever runs without its base. */
    private static void collectCascade(Map<Module, ModuleState> states, Module closing, List<Module> cascade,
                                       List<Edge> weakened, Set<Module> seen) {
        for (Edge edge : dependantsOf(closing)) {
            Module dependant = edge.dependent();
            if (!states.getOrDefault(dependant, ModuleState.DISABLED).grantsAccess()) {
                continue;
            }
            if (edge.kind() == Kind.PARTIAL) {
                weakened.add(edge);
                continue;
            }
            if (seen.add(dependant)) {
                collectCascade(states, dependant, cascade, weakened, seen);
                cascade.add(dependant);
            }
        }
    }
}
