package at.koopro.wizardsandbeasts.bestiary.journal;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Progressive discovery: what a page may show at each depth, and never more. */
class JournalRevealTest {

    private static final JournalLayout LAYOUT = JournalLayout.DEFAULT;

    @Test
    void anUndiscoveredCreatureHasNoPageAtAll() {
        assertTrue(JournalReveal.reveal(LAYOUT, DiscoveryTier.UNKNOWN).isEmpty(),
                "an undiscovered creature's page must not show even its headings");
        assertEquals(null, IllustrationStage.forTier(DiscoveryTier.UNKNOWN), "not even its silhouette");
    }

    @Test
    void nothingOpensBeforeItsDepthAndEverythingOpensAtIt() {
        for (DiscoveryTier tier : DiscoveryTier.values()) {
            if (tier == DiscoveryTier.UNKNOWN) continue;
            for (JournalReveal.SectionView view : JournalReveal.reveal(LAYOUT, tier)) {
                JournalLayout.Slot slot = slot(view.section());
                boolean open = tier.atLeast(slot.reveal());
                assertEquals(open, view.state() == JournalReveal.State.OPEN,
                        view.section() + " at " + tier + " (opens at " + slot.reveal() + ")");
                if (view.state() == JournalReveal.State.PARTIAL) {
                    assertTrue(slot.partial().isPresent() && tier.atLeast(slot.partial().get()) && !open,
                            view.section() + " partial at " + tier);
                }
            }
        }
    }

    @Test
    void everyPageShowsTheSameHeadingsSoALockedOneGivesNothingAway() {
        for (DiscoveryTier tier : List.of(DiscoveryTier.ENCOUNTERED, DiscoveryTier.OBSERVED, DiscoveryTier.STUDIED,
                DiscoveryTier.KNOWN)) {
            assertEquals(LAYOUT.slots().size(), JournalReveal.reveal(LAYOUT, tier).size(),
                    "a page at " + tier + " drops a heading, which would say whether the creature has anything under it");
        }
    }

    @Test
    void theRevealIsProgressiveNotAllOrNothing() {
        int previous = -1;
        for (DiscoveryTier tier : List.of(DiscoveryTier.ENCOUNTERED, DiscoveryTier.OBSERVED, DiscoveryTier.STUDIED,
                DiscoveryTier.KNOWN)) {
            int open = (int) JournalReveal.reveal(LAYOUT, tier).stream()
                    .filter(v -> v.state() == JournalReveal.State.OPEN).count();
            assertTrue(open > previous, "depth " + tier + " opened nothing new");
            assertTrue(tier == DiscoveryTier.KNOWN || open < LAYOUT.slots().size(), "the whole page opened at " + tier);
            previous = open;
        }
        assertEquals(LAYOUT.slots().size(), previous, "a known creature's page is not complete");
    }

    @Test
    void signatureAndHabitatArePartialBeforeTheyAreOpen() {
        assertEquals(JournalReveal.State.PARTIAL, JournalReveal.stateOf(LAYOUT, DiscoveryTier.ENCOUNTERED, JournalSection.HABITAT));
        assertEquals(JournalReveal.State.OPEN, JournalReveal.stateOf(LAYOUT, DiscoveryTier.OBSERVED, JournalSection.HABITAT));
        assertEquals(JournalReveal.State.LOCKED, JournalReveal.stateOf(LAYOUT, DiscoveryTier.OBSERVED, JournalSection.SIGNATURE));
        assertEquals(JournalReveal.State.PARTIAL, JournalReveal.stateOf(LAYOUT, DiscoveryTier.STUDIED, JournalSection.SIGNATURE));
        assertEquals(JournalReveal.State.OPEN, JournalReveal.stateOf(LAYOUT, DiscoveryTier.KNOWN, JournalSection.SIGNATURE));
        assertEquals(JournalReveal.State.LOCKED, JournalReveal.stateOf(LAYOUT, DiscoveryTier.OBSERVED, JournalSection.HARVESTING));
    }

    @Test
    void theStagesFollowTheTiers() {
        assertEquals(IllustrationStage.SILHOUETTE, IllustrationStage.forTier(DiscoveryTier.ENCOUNTERED));
        assertEquals(IllustrationStage.SKETCH, IllustrationStage.forTier(DiscoveryTier.OBSERVED));
        assertEquals(IllustrationStage.PORTRAIT, IllustrationStage.forTier(DiscoveryTier.STUDIED));
        assertEquals(IllustrationStage.STUDIED, IllustrationStage.forTier(DiscoveryTier.KNOWN));
        assertEquals(IllustrationStage.SKETCH, IllustrationStage.STUDIED.previous().previous());
        assertEquals(null, IllustrationStage.SIGNATURE.previous(), "a signature never falls back to a plate");
        EnumSet<JournalSection> all = EnumSet.allOf(JournalSection.class);
        LAYOUT.slots().forEach(s -> all.remove(s.section()));
        assertTrue(all.isEmpty(), "sections the default layout never shows: " + all);
    }

    private static JournalLayout.Slot slot(JournalSection section) {
        return LAYOUT.slots().stream().filter(s -> s.section() == section).findFirst().orElseThrow();
    }
}
