package at.koopro.wizardsandbeasts.module.profile;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleDependencies;
import at.koopro.wizardsandbeasts.module.ModuleState;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a target set of module states into the ordered changes that reach it without ever leaving a module running
 * while something it requires is off: closings first (dependants before what they depend on), then openings
 * (dependencies before their dependants). Pure — no server — so a profile can be previewed anywhere and tested
 * headless.
 */
@NullMarked
public final class ModuleProfilePlanner {

    /** One state change. */
    public record Step(Module module, ModuleState from, ModuleState to) {}

    /**
     * @param steps    the changes, in a safe order
     * @param locked   modules the target names but cannot move ({@code COMING_SOON} on either side)
     * @param broken   REQUIRES edges the finished state would violate — a profile with any is refused, not applied
     */
    public record Plan(List<Step> steps, List<Module> locked, List<ModuleDependencies.Edge> broken) {
        public boolean applicable() {
            return broken.isEmpty();
        }
    }

    private ModuleProfilePlanner() {}

    public static Plan plan(Map<Module, ModuleState> current, Map<Module, ModuleState> target) {
        Map<Module, ModuleState> end = new EnumMap<>(Module.class);
        end.putAll(current);
        List<Step> closings = new ArrayList<>();
        List<Step> openings = new ArrayList<>();
        List<Module> locked = new ArrayList<>();
        for (Map.Entry<Module, ModuleState> entry : target.entrySet()) {
            Module module = entry.getKey();
            ModuleState from = current.getOrDefault(module, ModuleState.DISABLED);
            ModuleState to = entry.getValue();
            if (from == to) {
                continue;
            }
            if (!from.isOperatorSettable() || !to.isOperatorSettable()) {
                locked.add(module);
                continue;
            }
            end.put(module, to);
            (to.grantsAccess() ? openings : closings).add(new Step(module, from, to));
        }
        // Closings: a module with more (transitive) dependants goes later, so dependants close first.
        closings.sort((a, b) -> Integer.compare(depth(a.module(), true), depth(b.module(), true)));
        // Openings: dependencies first.
        openings.sort((a, b) -> Integer.compare(depth(a.module(), false), depth(b.module(), false)));

        List<ModuleDependencies.Edge> broken = new ArrayList<>();
        for (ModuleDependencies.Edge edge : ModuleDependencies.all()) {
            if (edge.kind() == ModuleDependencies.Kind.REQUIRES
                    && end.getOrDefault(edge.dependent(), ModuleState.DISABLED).grantsAccess()
                    && !end.getOrDefault(edge.dependency(), ModuleState.DISABLED).grantsAccess()) {
                broken.add(edge);
            }
        }
        List<Step> steps = new ArrayList<>(closings);
        steps.addAll(openings);
        return new Plan(List.copyOf(steps), List.copyOf(locked), List.copyOf(broken));
    }

    /**
     * How far down the REQUIRES chain a module sits: counting its dependants ({@code up}) or its dependencies. The
     * graph is small and acyclic for REQUIRES (a test pins that), so plain recursion is fine.
     */
    private static int depth(Module module, boolean up) {
        int best = 0;
        for (ModuleDependencies.Edge edge : up ? ModuleDependencies.dependantsOf(module) : ModuleDependencies.dependenciesOf(module)) {
            if (edge.kind() == ModuleDependencies.Kind.REQUIRES) {
                best = Math.max(best, 1 + depth(up ? edge.dependent() : edge.dependency(), up));
            }
        }
        return best;
    }
}
