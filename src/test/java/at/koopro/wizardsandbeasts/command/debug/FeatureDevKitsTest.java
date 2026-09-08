package at.koopro.wizardsandbeasts.command.debug;

import at.koopro.wizardsandbeasts.command.debug.dev.FeatureDevKit;
import at.koopro.wizardsandbeasts.command.debug.dev.FeatureDevKits;
import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSections;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dev-kit registry's invariants, which are all about the brigadier tree and the run order.
 *
 * <p>None of this can be checked at runtime: a duplicate literal merges silently, and a kit in the
 * wrong position produces a setup that looks like it worked.
 */
class FeatureDevKitsTest {

    @Test
    void everyKitHasAUniqueLowercaseIdAndATitle() {
        Set<String> seen = new HashSet<>();
        for (FeatureDevKit kit : FeatureDevKits.all()) {
            assertTrue(seen.add(kit.id()), "duplicate kit id: " + kit.id());
            assertFalse(kit.id().isBlank(), "kit with no id");
            assertFalse(kit.title().isBlank(), "kit with no title: " + kit.id());
            assertEquals(kit.id().toLowerCase(Locale.ROOT), kit.id(),
                    "kit id must be a lowercase command literal: " + kit.id());
            assertFalse(kit.id().contains(" "),
                    "kit id must be one command literal: " + kit.id());
        }
        assertFalse(seen.isEmpty(), "no dev kits registered");
    }

    /**
     * {@code all} is a literal on the same node as every kit id, so a kit called that would shadow
     * the node that runs every kit — and the loser would simply be unreachable, not an error.
     */
    @Test
    void noKitClaimsTheReservedAllLiteral() {
        assertFalse(FeatureDevKits.ids().contains("all"));
    }

    /**
     * A disabled module swallows its subsystem whole, so opening a gate inside one would report
     * success for a feature that still cannot run. Modules first is load-bearing, not cosmetic.
     */
    @Test
    void modulesRunFirstAndHeritageSecond() {
        List<String> ids = FeatureDevKits.ids();
        assertEquals("modules", ids.get(0), "modules must run before anything it gates");
        assertEquals("heritage", ids.get(1), "heritage gates most of what follows it");
    }

    /**
     * Where a feature has both a read-only section and a kit, the two must answer to the same word:
     * {@code /wandb debug feature brewing} and {@code /wandb debug dev kit brewing} are one feature.
     * A kit whose id matches no section is a typo, since every kit built so far has a section.
     */
    @Test
    void everyKitIdNamesAnExistingFeatureSection() {
        List<String> sections = FeatureDebugSections.ids();
        for (String id : FeatureDevKits.ids()) {
            assertTrue(sections.contains(id),
                    "dev kit '" + id + "' does not match any feature section id");
        }
    }
}
