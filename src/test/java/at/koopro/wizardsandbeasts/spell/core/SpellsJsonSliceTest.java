package at.koopro.wizardsandbeasts.spell.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JSON slice of the registry: swapped whole, never mutated in place.
 *
 * <p>{@code Spells.replaceJsonSpells} is written for two callers on two threads — the datapack
 * reload on the server, and {@code SpellDefinitionsSyncS2CPayload} on the client — so the properties
 * that matter are that Java spells survive a swap, that the previous slice is gone rather than
 * merged, and that the published table is not writable by anything holding a reference to it.
 */
class SpellsJsonSliceTest {

    @AfterEach
    void clearSlice() {
        Spells.clearJsonSpells();
    }

    @Test
    void replaceJsonSpells_leavesJavaSpellsAlone() {
        Spells.replaceJsonSpells(List.of(new StubSpell("test_a")));

        assertSame(Spells.PROTEGO, Spells.byId(Spells.PROTEGO.getId()),
                "A JSON swap must not touch the Java-registered spells.");
    }

    @Test
    void replaceJsonSpells_dropsThePreviousSlice() {
        Spells.replaceJsonSpells(List.of(new StubSpell("test_a"), new StubSpell("test_b")));
        assertNotNull(Spells.byId("test_a"));

        Spells.replaceJsonSpells(List.of(new StubSpell("test_b")));

        assertNull(Spells.byId("test_a"), "The previous slice must be replaced, not merged into.");
        assertNotNull(Spells.byId("test_b"));
    }

    @Test
    void replaceJsonSpells_tracksExactlyTheSuppliedIds() {
        Spells.replaceJsonSpells(List.of(new StubSpell("test_a"), new StubSpell("test_b")));

        assertEquals(Set.of("test_a", "test_b"), Spells.jsonSpellIds());
    }

    @Test
    void clearJsonSpells_removesEveryJsonSpellAndNothingElse() {
        int javaCount = Spells.count();
        Spells.replaceJsonSpells(List.of(new StubSpell("test_a")));

        Spells.clearJsonSpells();

        assertNull(Spells.byId("test_a"));
        assertEquals(javaCount, Spells.count());
        assertTrue(Spells.jsonSpellIds().isEmpty());
    }

    @Test
    void replaceJsonSpells_initialisesEverySpellBeforePublishing() {
        StubSpell spell = new StubSpell("test_a");

        Spells.replaceJsonSpells(List.of(spell));

        // getProperties() is only non-null once init() has run. Anything that can reach the spell
        // through the registry must therefore never see it uninitialised.
        assertNotNull(Spells.byId("test_a"));
        assertNotNull(Spells.byId("test_a").getProperties());
    }

    private static final class StubSpell extends Spell {
        private StubSpell(String id) {
            super(id, "Stub " + id, SpellCategory.UTILITY, 20, 0f, 0xFFFFFF);
        }

        @Override
        protected SpellProperties buildProperties() {
            return SpellProperties.self().build();
        }

        @Override
        protected SpellRequirement buildRequirement() {
            return SpellRequirement.none();
        }
    }
}
