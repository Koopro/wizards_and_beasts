package at.koopro.wizardsandbeasts.bestiary.journal;

import at.koopro.wizardsandbeasts.bestiary.CreatureProfile.StudyAct;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.creature.Temperament;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyCapability;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Research completion and the derived facts the journal prints. */
class ResearchAndFactsTest {

    private static final List<StudyAct> FEED_BOND = List.of(StudyAct.FEED, StudyAct.BOND);
    // Mooncalf-like: four driven behaviours (prey, herd, nocturnal, cowardly) and one descriptive (burrowing).
    private static final List<String> ECOLOGY = List.of("prey", "herd", "nocturnal", "burrowing", "cowardly");

    @Test
    void researchNeedsTheDepthTheHandsAndHalfTheBehaviours() {
        ResearchProgress full = ResearchProgress.of(DiscoveryTier.KNOWN, FEED_BOND, Set.of("feed", "bond"), ECOLOGY,
                Set.of("prey", "herd"));
        assertTrue(full.complete(), "known, fed, bonded and half its four behaviours seen");
        assertEquals(2, full.notesNeeded());

        assertFalse(ResearchProgress.of(DiscoveryTier.STUDIED, FEED_BOND, Set.of("feed", "bond"), ECOLOGY,
                Set.of("prey", "herd")).complete(), "not yet known");
        assertFalse(ResearchProgress.of(DiscoveryTier.KNOWN, FEED_BOND, Set.of("feed"), ECOLOGY,
                Set.of("prey", "herd")).complete(), "never earned its trust");
        assertFalse(ResearchProgress.of(DiscoveryTier.KNOWN, FEED_BOND, Set.of("feed", "bond"), ECOLOGY,
                Set.of("prey")).complete(), "one behaviour of four is not half");
        assertFalse(ResearchProgress.of(DiscoveryTier.KNOWN, FEED_BOND, Set.of("feed", "bond"), ECOLOGY,
                Set.of("burrowing", "prey")).complete(), "a descriptive capability is not a behaviour to watch");
    }

    @Test
    void watchingIsNotAStudyActAndACreatureWithNoEcologyNeedsNoNotes() {
        ResearchProgress plain = ResearchProgress.of(DiscoveryTier.KNOWN, List.of(StudyAct.WATCH), Set.of(), List.of(), Set.of());
        assertTrue(plain.complete(), "a creature you can only watch is complete once known");
        assertEquals(4, plain.total(), "seen, watched, studied, known");
    }

    @Test
    void aResearchedNaturalistIsLetNearWithoutCreepingButPurityStillHolds() {
        assertFalse(at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules.letsNear(
                DiscoveryTier.KNOWN, false, false, true, false, 0f, false), "walking up with hands full, unresearched");
        assertTrue(at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules.letsNear(
                DiscoveryTier.KNOWN, false, false, true, false, 0f, true), "researched: no need to creep");
        assertFalse(at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules.letsNear(
                DiscoveryTier.KNOWN, false, false, true, true, 0f, true), "a slayer, however well read");
        assertFalse(at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules.letsNear(
                DiscoveryTier.KNOWN, true, true, true, false, 90f, true), "a darkened soul, however well read");
    }

    @Test
    void temperamentIsTheMostTellingTrait() {
        assertEquals("dangerous", JournalFacts.temperament(Temperament.HOSTILE, List.of()));
        assertEquals("dangerous", JournalFacts.temperament(Temperament.NEUTRAL,
                List.of(EcologyCapability.TERRITORIAL, EcologyCapability.AGGRESSIVE)));
        assertEquals("territorial", JournalFacts.temperament(null, List.of(EcologyCapability.TERRITORIAL)));
        assertEquals("wary", JournalFacts.temperament(Temperament.PASSIVE, List.of(EcologyCapability.HUMAN_AVOIDANT)));
        assertEquals("curious", JournalFacts.temperament(null, List.of(EcologyCapability.CURIOUS)));
        assertEquals("docile", JournalFacts.temperament(Temperament.PASSIVE, List.of()));
        assertEquals("neutral", JournalFacts.temperament(null, List.of()));
        for (String t : List.of("dangerous", "territorial", "wary", "curious", "docile", "neutral")) {
            assertTrue(JournalFacts.TEMPERAMENTS.contains(t), t);
        }
    }
}
