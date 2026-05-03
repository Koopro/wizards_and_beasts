package at.koopro.wizardsandbeasts.brew;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the contract of the {@link Brews} registry: id resolution, the
 * {@code wizards_and_beasts:}-namespace fallback, and clear semantics. Uses brews with empty
 * effect lists because the in-memory record permits them — only the JSON
 * pipeline rejects empty effects (see {@link at.koopro.wizardsandbeasts.brew.def.BrewDefinition#toBrew}).
 */
class BrewsRegistryTest {

    @AfterEach
    void cleanRegistry() {
        Brews.clear();
    }

    @Test
    void register_byId_clear_count_workInLoop() {
        Brew a = Brews.register(new Brew("wizards_and_beasts:test_a", "Test A", 0, List.of(), null));
        Brew b = Brews.register(new Brew("wizards_and_beasts:test_b", "Test B", 0, List.of(), null));
        assertEquals(2, Brews.count());
        assertSame(a, Brews.byId("wizards_and_beasts:test_a"));
        assertSame(b, Brews.byId("wizards_and_beasts:test_b"));
        Brews.clear();
        assertEquals(0, Brews.count());
        assertNull(Brews.byId("wizards_and_beasts:test_a"));
    }

    @Test
    void byId_bareId_fallsBackToNeoNamespace() {
        Brews.register(new Brew("wizards_and_beasts:wiggenweld", "Wiggenweld", 0, List.of(), null));
        assertNotNull(Brews.byId("wiggenweld"),
                "Bare ids must resolve under the wizards_and_beasts: namespace, mirroring Spells.byId");
    }

    @Test
    void byId_bareId_doesNotMatchOtherNamespace() {
        Brews.register(new Brew("addon:fancy", "Fancy", 0, List.of(), null));
        assertNull(Brews.byId("fancy"),
                "Bare ids must NOT silently match non-wizards_and_beasts: namespaces.");
        assertNotNull(Brews.byId("addon:fancy"));
    }

    @Test
    void byId_null_returnsNull() {
        assertNull(Brews.byId(null));
    }
}
