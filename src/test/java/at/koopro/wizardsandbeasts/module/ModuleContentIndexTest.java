package at.koopro.wizardsandbeasts.module;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two halves of the index that do not need a live registry: folding tag membership into a
 * content-to-module map, and deciding accessibility from that map. Tag reading itself is Minecraft's job
 * and is exercised in-game.
 */
class ModuleContentIndexTest {

    /** Stands in for an Item/Block/EntityType — {@code index} is generic and never inspects the value. */
    private record Content(String id) {}

    @AfterEach
    void restoreShippedStates() {
        Map<Module, ModuleState> shipped = new EnumMap<>(Module.class);
        for (Module module : Module.values()) {
            shipped.put(module, ModuleDefaults.shipped(module));
        }
        ModuleManager.acceptAuthoritative(shipped);
    }

    @Test
    void index_roundTripsContentToItsModule() {
        Content wand = new Content("wand");
        Content diary = new Content("riddles_diary");

        Map<Content, Module> index = ModuleContentIndex.index(module -> switch (module) {
            case WANDS -> List.of(wand);
            case DARK_ARTS -> List.of(diary);
            default -> List.of();
        });

        assertEquals(Module.WANDS, index.get(wand));
        assertEquals(Module.DARK_ARTS, index.get(diary));
        assertEquals(2, index.size());
    }

    @Test
    void index_leavesUnclassifiedContentAbsent() {
        Map<Content, Module> index = ModuleContentIndex.index(module -> List.of());

        assertTrue(index.isEmpty());
        assertNull(index.get(new Content("butterbeer")));
    }

    @Test
    void index_isImmutable() {
        Map<Content, Module> index = ModuleContentIndex.index(module -> List.of());
        assertThrows(UnsupportedOperationException.class, () -> index.put(new Content("x"), Module.WANDS));
    }

    @Test
    void index_keepsTheFirstModuleWhenContentIsTaggedTwice() {
        Content contested = new Content("hand_of_glory");
        // WANDS is declared before DARK_ARTS, so it wins regardless of which tag the pack wrote first.
        Map<Content, Module> index = ModuleContentIndex.index(module -> switch (module) {
            case DARK_ARTS, WANDS -> List.of(contested);
            default -> List.of();
        });

        assertEquals(Module.WANDS, index.get(contested));
    }

    @Test
    void accessible_untaggedContentIsAlwaysReachable() {
        assertTrue(ModuleContentIndex.accessible(null),
                "content owned by no module must fail open, not vanish");
    }

    @Test
    void accessible_followsModuleState() {
        Map<Module, ModuleState> states = new EnumMap<>(Module.class);
        states.put(Module.WANDS, ModuleState.ENABLED);
        states.put(Module.PROFICIENCY, ModuleState.PREVIEW);
        states.put(Module.DARK_ARTS, ModuleState.DISABLED);
        states.put(Module.AZKABAN, ModuleState.COMING_SOON);
        ModuleManager.acceptAuthoritative(states);

        assertTrue(ModuleContentIndex.accessible(Module.WANDS));
        assertTrue(ModuleContentIndex.accessible(Module.PROFICIENCY), "PREVIEW counts as enabled");
        assertFalse(ModuleContentIndex.accessible(Module.DARK_ARTS));
        assertFalse(ModuleContentIndex.accessible(Module.AZKABAN));
    }

    @Test
    void accessible_newContentModulesShipReachable() {
        // The seven classification modules exist to make previously ungatable content gatable. If any of
        // them shipped off, content that is reachable today would silently disappear from existing worlds.
        for (Module module : List.of(Module.GRINGOTTS, Module.WANDWOOD, Module.MAGIZOOLOGY,
                Module.WIZARDING_FOOD, Module.ARTEFACTS, Module.FURNISHINGS, Module.SCHOLARSHIP)) {
            assertEquals(ModuleState.ENABLED, ModuleDefaults.shipped(module),
                    () -> module + " must ship ENABLED so it takes nothing away from existing worlds");
        }
    }
}
