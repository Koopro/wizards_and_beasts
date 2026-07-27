package at.koopro.wizardsandbeasts.integration;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleContentIndex;
import at.koopro.wizardsandbeasts.module.ModuleDefaults;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The viewer filter's rule, tested without JEI on the classpath — which is the point of the seam. If this
 * ever needs a JEI type to compile, the decision has leaked into the adapter.
 *
 * <p>Registry-backed behaviour ({@code hiddenItems}) needs a live game and is exercised there; what
 * matters here is that the filter answers the same question {@link ModuleContentIndex} does, so a viewer
 * cannot show something the creative menu hides or vice versa.
 */
class ViewerContentFilterTest {

    @AfterEach
    void restoreShippedStates() {
        Map<Module, ModuleState> shipped = new EnumMap<>(Module.class);
        for (Module module : Module.values()) {
            shipped.put(module, ModuleDefaults.shipped(module));
        }
        ModuleManager.acceptAuthoritative(shipped);
    }

    @Test
    void hidingFollowsModuleState() {
        Map<Module, ModuleState> states = new EnumMap<>(Module.class);
        states.put(Module.WANDS, ModuleState.ENABLED);
        states.put(Module.PROFICIENCY, ModuleState.PREVIEW);
        states.put(Module.DARK_ARTS, ModuleState.DISABLED);
        states.put(Module.AZKABAN, ModuleState.COMING_SOON);
        ModuleManager.acceptAuthoritative(states);

        assertTrue(ModuleContentIndex.accessible(Module.WANDS));
        assertTrue(ModuleContentIndex.accessible(Module.PROFICIENCY), "PREVIEW content stays visible");
        assertFalse(ModuleContentIndex.accessible(Module.DARK_ARTS));
        assertFalse(ModuleContentIndex.accessible(Module.AZKABAN));
    }

    @Test
    void untaggedContentIsNeverHidden() {
        // The viewer must fail open exactly where the index does; a viewer that hid unclassified content
        // would disagree with the creative menu about what exists.
        assertTrue(ModuleContentIndex.accessible(null));
    }
}
