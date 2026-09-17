package at.koopro.wizardsandbeasts.heritage.appearance;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lookup and miss-handling contract for {@link HeritageAppearanceRegistry}.
 *
 * <p>A missing entry is a legal, expected state — the registry is an override layer over the shipped
 * hardcoded form registries, and most heritages will carry no entry for a long time. So the two
 * things worth pinning are that a miss returns null rather than throwing, and that it logs at most
 * once per identifier: the lookup runs from a render path, and one log line per frame per player is
 * how a debug aid becomes a performance bug.
 */
class HeritageAppearanceRegistryTest {

    private static HeritageAppearance entry(String id, Heritage heritage, @org.jspecify.annotations.Nullable HeritageVariant variant) {
        return new HeritageAppearance(
                Identifier.fromNamespaceAndPath("wizards_and_beasts", id),
                heritage.getId(),
                variant == null ? Optional.empty() : Optional.of(variant.getId()),
                List.of(),
                Provenance.extrapolated());
    }

    @BeforeEach
    void reset() {
        HeritageAppearanceRegistry.clear();
    }

    @AfterEach
    void tearDown() {
        HeritageAppearanceRegistry.clear();
    }

    @Test
    void heritageWideEntry_resolvesForEveryVariant() {
        HeritageAppearance wide = entry("veela", Heritage.VEELA, null);
        HeritageAppearanceRegistry.setClientEntries(List.of(wide));

        assertSame(wide, HeritageAppearanceRegistry.clientResolve(Heritage.VEELA, HeritageVariant.VEELA_HALF));
        assertSame(wide, HeritageAppearanceRegistry.clientResolve(Heritage.VEELA, HeritageVariant.VEELA_QUARTER));
        assertSame(wide, HeritageAppearanceRegistry.clientResolve(Heritage.VEELA, null));
    }

    @Test
    void variantEntry_winsOverTheHeritageWideEntry() {
        HeritageAppearance wide = entry("veela", Heritage.VEELA, null);
        HeritageAppearance specific = entry("veela_full", Heritage.VEELA, HeritageVariant.VEELA_FULL);
        HeritageAppearanceRegistry.setClientEntries(List.of(wide, specific));

        assertSame(specific, HeritageAppearanceRegistry.clientResolve(Heritage.VEELA, HeritageVariant.VEELA_FULL));
        assertSame(wide, HeritageAppearanceRegistry.clientResolve(Heritage.VEELA, HeritageVariant.VEELA_HALF),
                "a variant entry must not shadow the heritage-wide entry for other variants");
    }

    @Test
    void conditionEntry_resolvesOnTheServerAndNeverShadowsAHeritage() {
        HeritageAppearance lycanthropy = new HeritageAppearance(
                Identifier.fromNamespaceAndPath("wizards_and_beasts", "lycanthropy"), "", Optional.empty(),
                List.of(), Provenance.extrapolated(), Optional.of("lycanthropy"));
        HeritageAppearanceRegistry.replaceAll(Map.of(lycanthropy.id(), lycanthropy));

        assertSame(lycanthropy, HeritageAppearanceRegistry.resolveCondition(
                at.koopro.wizardsandbeasts.heritage.MagicalCondition.LYCANTHROPY));
        assertNull(HeritageAppearanceRegistry.resolveCondition(
                at.koopro.wizardsandbeasts.heritage.MagicalCondition.OBSCURUS));
        assertNull(HeritageAppearanceRegistry.resolve(Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD),
                "a condition entry is not the Wizardkind entry: a bitten wizard's lineage still looks like itself");
    }

    @Test
    void missingEntry_returnsNullAndDoesNotThrow() {
        HeritageAppearanceRegistry.setClientEntries(List.of());
        assertNull(HeritageAppearanceRegistry.clientResolve(Heritage.VAMPIRE, HeritageVariant.VAMPIRE_TURNED));
    }

    @Test
    void nullHeritage_resolvesToNullWithoutLoggingAMiss() {
        HeritageAppearanceRegistry.setClientEntries(List.of());
        assertNull(HeritageAppearanceRegistry.clientResolve(null, null));
        assertEquals(0, HeritageAppearanceRegistry.reportedMissCount(),
                "a player who has not chosen a heritage yet is not a missing entry");
    }

    @Test
    void repeatedMisses_areLoggedOncePerIdentifier() {
        HeritageAppearanceRegistry.setClientEntries(List.of());

        for (int i = 0; i < 100; i++) {
            HeritageAppearanceRegistry.clientResolve(Heritage.VAMPIRE, HeritageVariant.VAMPIRE_TURNED);
        }
        assertEquals(1, HeritageAppearanceRegistry.reportedMissCount(),
                "a render-path miss must log once, not once per frame");

        HeritageAppearanceRegistry.clientResolve(Heritage.GOBLIN, HeritageVariant.GOBLIN_COMMON);
        assertEquals(2, HeritageAppearanceRegistry.reportedMissCount(),
                "a different identifier is a different miss");
    }

    @Test
    void reload_clearsTheMissLogSoItReportsAfresh() {
        HeritageAppearanceRegistry.setClientEntries(List.of());
        HeritageAppearanceRegistry.clientResolve(Heritage.VAMPIRE, HeritageVariant.VAMPIRE_TURNED);
        assertEquals(1, HeritageAppearanceRegistry.reportedMissCount());

        HeritageAppearanceRegistry.setClientEntries(List.of());
        assertEquals(0, HeritageAppearanceRegistry.reportedMissCount());
    }

    @Test
    void serverAndClientMaps_areIndependent() {
        HeritageAppearance serverSide = entry("giant", Heritage.GIANT, null);
        HeritageAppearanceRegistry.replaceAll(Map.of(serverSide.id(), serverSide));

        assertNotNull(HeritageAppearanceRegistry.resolve(Heritage.GIANT, null));
        assertNull(HeritageAppearanceRegistry.clientResolve(Heritage.GIANT, null),
                "the client must not read the server-populated map; on a dedicated server it is empty");
    }

    @Test
    void getAll_reflectsTheLoadedSet() {
        HeritageAppearance a = entry("giant", Heritage.GIANT, null);
        HeritageAppearance b = entry("goblin", Heritage.GOBLIN, null);
        HeritageAppearanceRegistry.replaceAll(Map.of(a.id(), a, b.id(), b));

        assertEquals(2, HeritageAppearanceRegistry.getAll().size());
        assertTrue(HeritageAppearanceRegistry.getAll().contains(a));
        assertSame(a, HeritageAppearanceRegistry.get(a.id()));
    }
}
