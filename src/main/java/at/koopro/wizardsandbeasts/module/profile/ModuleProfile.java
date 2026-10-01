package at.koopro.wizardsandbeasts.module.profile;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleDefaults;
import at.koopro.wizardsandbeasts.module.ModuleState;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Named sets of module states — groundwork for one-click server profiles. Only {@link #DEFAULT} has contents today:
 * it is the build's own shipped states, so it is real and can be planned against now. The others are reserved names
 * with no states yet; defining them is a design decision for later, and {@link #defined()} keeps them from being
 * mistaken for something that works.
 *
 * <p>A profile is never applied here. {@link ModuleProfilePlanner} turns one into an ordered list of ordinary state
 * changes that respects {@code ModuleDependencies}; applying it later means running those through
 * {@code ModuleStateService}, the same door every other change uses.
 */
@NullMarked
public enum ModuleProfile {
    DEFAULT,
    RPG,
    HARDCORE,
    SANDBOX,
    MINIMAL,
    DEVELOPER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String nameKey() {
        return "module.wizards_and_beasts.profile." + id();
    }

    public String descriptionKey() {
        return nameKey() + ".desc";
    }

    /** Whether this profile has states yet. */
    public boolean defined() {
        return this == DEFAULT;
    }

    /** The states this profile asks for; empty for a profile not defined yet. */
    public Map<Module, ModuleState> states() {
        Map<Module, ModuleState> out = new EnumMap<>(Module.class);
        if (this == DEFAULT) {
            for (Module module : Module.values()) {
                out.put(module, ModuleDefaults.shipped(module));
            }
        }
        return out;
    }
}
