package at.koopro.wizardsandbeasts.heritage;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the data the first-join gate cannot open without.
 *
 * <p>{@code HeritageSelectionScreen} is a hard gate: {@code shouldCloseOnEsc()} is false, ESC is
 * swallowed at the root, and the only exit is committing an available heritage. That makes its data
 * preconditions unusually load-bearing — if they stop holding, a new player is locked in a screen
 * they cannot dismiss and cannot complete, with nothing logged. These are cheap invariants and the
 * failure mode they cover is total, the same argument as {@code WandBondReachabilityTest}.
 */
class HeritageGateReachabilityTest {

    @Test
    void atLeastOneHeritageCanActuallyBeChosen() {
        List<Heritage> selectable = Arrays.stream(Heritage.values())
                .filter(Heritage::isAlphaAvailable)
                .toList();
        assertFalse(selectable.isEmpty(),
                "no heritage is alpha-available — the first-join gate would be unpassable and every "
                        + "new player would be trapped in a screen with no exit");
    }

    @Test
    void everySelectableHeritageHasALineageToCommit() {
        // The screen seeds the lineage cycler with getSubtypes().get(0) and sends the variant id in
        // HeritageSelectC2SPayload. A selectable heritage with no lineage would throw on selection.
        for (Heritage heritage : Heritage.values()) {
            if (!heritage.isAlphaAvailable()) {
                continue;
            }
            List<HeritageVariant> subtypes = heritage.getSubtypes();
            assertNotNull(subtypes, heritage.getId() + " has null subtypes");
            assertFalse(subtypes.isEmpty(),
                    heritage.getId() + " is selectable but has no lineage — the cycler has nothing "
                            + "to show and the commit payload has no variant id");
        }
    }

    @Test
    void everyHeritageHasALineageAtAll() {
        // Locked heritages are still browsable, and the dossier reads the first subtype for its
        // power band, so even a browse-only heritage needs one.
        for (Heritage heritage : Heritage.values()) {
            assertFalse(heritage.getSubtypes().isEmpty(),
                    heritage.getId() + " has no lineage — browsing it would fail before it renders");
        }
    }

    @Test
    void everyLineageBelongsToItsOwnHeritage() {
        // The cycler hands back a HeritageVariant that the screen commits alongside the heritage id.
        // A variant filed under the wrong parent would send a mismatched pair the server rejects.
        for (Heritage heritage : Heritage.values()) {
            for (HeritageVariant variant : heritage.getSubtypes()) {
                assertTrue(variant.getParentHeritage() == heritage,
                        variant.getId() + " is listed under " + heritage.getId()
                                + " but reports parent " + variant.getParentHeritage().getId());
            }
        }
    }

    @Test
    void heritageIdsAreUnique() {
        long distinct = Arrays.stream(Heritage.values()).map(Heritage::getId).distinct().count();
        assertTrue(distinct == Heritage.values().length,
                "duplicate heritage id — lookup by id would silently resolve to the wrong one");
    }
}
