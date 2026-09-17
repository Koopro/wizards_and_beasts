package at.koopro.wizardsandbeasts.bestiary;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The bestiary unlock rule, tested on its own: a naturalist's notebook fills by seeing, watching, handling and finally
 * understanding a creature — never by killing it.
 */
class EncounterRuleTest {

    // -- encountering ---------------------------------------------------------------------------

    @Test
    void anEncounterOpensAPageAndNothingMore() {
        assertEquals(DiscoveryTier.ENCOUNTERED, EncounterRule.onEncountered(DiscoveryTier.UNKNOWN));
        for (DiscoveryTier tier : DiscoveryTier.values()) {
            if (tier != DiscoveryTier.UNKNOWN) {
                assertEquals(tier, EncounterRule.onEncountered(tier), tier + " changed on a repeat encounter");
            }
        }
    }

    @Test
    void killingTeachesNothingBeyondAnEncounter() {
        // A kill is routed through onEncountered; however many times it happens, it cannot deepen a page.
        DiscoveryTier tier = DiscoveryTier.UNKNOWN;
        for (int kill = 0; kill < 50; kill++) {
            tier = EncounterRule.onEncountered(tier);
        }
        assertEquals(DiscoveryTier.ENCOUNTERED, tier);
    }

    // -- watching -------------------------------------------------------------------------------

    @Test
    void thirtySecondsOfCalmWatchingIsAnObservation() {
        assertEquals(DiscoveryTier.ENCOUNTERED,
                EncounterRule.afterWatching(DiscoveryTier.ENCOUNTERED, EncounterRule.OBSERVED_TICKS - 1, true, true));
        assertEquals(DiscoveryTier.OBSERVED,
                EncounterRule.afterWatching(DiscoveryTier.ENCOUNTERED, EncounterRule.OBSERVED_TICKS, true, true));
    }

    @Test
    void watchingAloneStudiesOnlyACreatureThatAsksForNothingElse() {
        int longWatch = EncounterRule.STUDIED_BY_WATCHING_TICKS;
        assertEquals(DiscoveryTier.STUDIED,
                EncounterRule.afterWatching(DiscoveryTier.OBSERVED, longWatch, false, true));
        assertEquals(DiscoveryTier.OBSERVED,
                EncounterRule.afterWatching(DiscoveryTier.OBSERVED, longWatch * 100, true, true),
                "a creature that must be fed or handled is not studied from a distance, however long");
    }

    @Test
    void aCreatureWithASignatureIsNeverKnownByWatchingAlone() {
        int forever = EncounterRule.KNOWN_BY_WATCHING_TICKS * 100;
        assertEquals(DiscoveryTier.STUDIED, EncounterRule.afterWatching(DiscoveryTier.STUDIED, forever, false, true));
        assertEquals(DiscoveryTier.KNOWN, EncounterRule.afterWatching(DiscoveryTier.STUDIED, forever, false, false));
    }

    @Test
    void theWatchingRoutesClimbInOrder() {
        assertTrue(EncounterRule.OBSERVED_TICKS < EncounterRule.STUDIED_BY_WATCHING_TICKS);
        assertTrue(EncounterRule.STUDIED_BY_WATCHING_TICKS < EncounterRule.KNOWN_BY_WATCHING_TICKS);
    }

    // -- handling and signature -----------------------------------------------------------------

    @Test
    void aStudyActReachesStudiedFromAnyEarlierTier() {
        assertEquals(DiscoveryTier.STUDIED, EncounterRule.onStudied(DiscoveryTier.UNKNOWN));
        assertEquals(DiscoveryTier.STUDIED, EncounterRule.onStudied(DiscoveryTier.OBSERVED));
        assertEquals(DiscoveryTier.KNOWN, EncounterRule.onStudied(DiscoveryTier.KNOWN));
    }

    @Test
    void witnessingTheSignatureCompletesThePage() {
        for (DiscoveryTier tier : DiscoveryTier.values()) {
            assertEquals(DiscoveryTier.KNOWN, EncounterRule.onSignature(tier));
        }
    }

    @Test
    void noRuleEverLowersATier() {
        for (DiscoveryTier tier : DiscoveryTier.values()) {
            assertTrue(EncounterRule.onEncountered(tier).atLeast(tier));
            assertTrue(EncounterRule.onStudied(tier).atLeast(tier));
            assertTrue(EncounterRule.onSignature(tier).atLeast(tier));
            for (boolean byHand : new boolean[] {false, true}) {
                for (boolean signature : new boolean[] {false, true}) {
                    assertTrue(EncounterRule.afterWatching(tier, 0, byHand, signature).atLeast(tier));
                }
            }
        }
    }

    @Test
    void onlyManualEntriesAreOutsideAutomaticProgress() {
        for (EncounterTrigger trigger : EncounterTrigger.values()) {
            assertEquals(trigger != EncounterTrigger.MANUAL, EncounterRule.automatic(trigger));
        }
    }

    // -- the rename -----------------------------------------------------------------------------

    @Test
    void oldSaveNamesKeepTheirDepthOfKnowledge() {
        assertEquals(DiscoveryTier.UNKNOWN, DiscoveryTier.fromLegacySave("UNDISCOVERED"));
        assertEquals(DiscoveryTier.ENCOUNTERED, DiscoveryTier.fromLegacySave("SIGHTED"));
        assertEquals(DiscoveryTier.OBSERVED, DiscoveryTier.fromLegacySave("ENCOUNTERED"),
                "a saved ENCOUNTERED was index 2 and must not fall back a tier");
        assertEquals(DiscoveryTier.STUDIED, DiscoveryTier.fromLegacySave("STUDIED"));
        assertEquals(DiscoveryTier.KNOWN, DiscoveryTier.fromLegacySave("MASTERED"));
    }

    @Test
    void datapackNamesAcceptTheUnambiguousOldSpellings() {
        assertEquals(DiscoveryTier.KNOWN, DiscoveryTier.byName("MASTERED"));
        assertEquals(DiscoveryTier.ENCOUNTERED, DiscoveryTier.byName("SIGHTED"));
        assertEquals(DiscoveryTier.OBSERVED, DiscoveryTier.byName("OBSERVED"));
        assertNull(DiscoveryTier.byName("PROFICIENT"));
    }

    // -- copy contract --------------------------------------------------------------------------

    /** Both strings a tier contributes to the screen must be translation keys. */
    @Test
    void everyTierNamesTranslationKeysRatherThanEnglish() {
        for (DiscoveryTier tier : DiscoveryTier.values()) {
            String slug = tier.name().toLowerCase(Locale.ROOT);
            assertEquals("bestiary.wizards_and_beasts.tier." + slug, keyOf(tier.displayName()));
            assertEquals("bestiary.wizards_and_beasts.tier." + slug + ".hint", keyOf(tier.unlockHint()));
        }
    }

    private static String keyOf(Component component) {
        ComponentContents contents = component.getContents();
        assertInstanceOf(TranslatableContents.class, contents,
                "expected a translatable component, got " + contents.getClass().getSimpleName());
        return ((TranslatableContents) contents).getKey();
    }
}
