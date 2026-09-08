package at.koopro.wizardsandbeasts.heritage.werewolf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static at.koopro.wizardsandbeasts.heritage.werewolf.FeralTargetPriority.ANIMAL;
import static at.koopro.wizardsandbeasts.heritage.werewolf.FeralTargetPriority.HOSTILE;
import static at.koopro.wizardsandbeasts.heritage.werewolf.FeralTargetPriority.OTHER;
import static at.koopro.wizardsandbeasts.heritage.werewolf.FeralTargetPriority.PLAYER;
import static at.koopro.wizardsandbeasts.heritage.werewolf.FeralTargetPriority.VILLAGER_OR_GOLEM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The feral werewolf's aggression rules, tested on their own.
 *
 * <p>{@link FeralTargetSelection} and {@link FeralTargetPriority} are deliberately free of level,
 * entity and registry, so none of this needs a Minecraft bootstrap. The behaviour they encode — who a
 * wolf goes for, and what is worth abandoning a chase over — could otherwise only be checked by standing
 * in a field at midnight and watching.
 */
class FeralTargetSelectionTest {

    private static FeralTargetSelection.Candidate at(FeralTargetPriority priority, double distance) {
        return new FeralTargetSelection.Candidate(priority, distance);
    }

    // -- the ordering itself ---------------------------------------------------------------------

    @Test
    void bands_rankPlayersOverVillagersOverAnimalsOverHostiles() {
        assertTrue(PLAYER.outranks(VILLAGER_OR_GOLEM));
        assertTrue(VILLAGER_OR_GOLEM.outranks(ANIMAL));
        assertTrue(ANIMAL.outranks(HOSTILE));
        assertTrue(HOSTILE.outranks(OTHER));
    }

    @Test
    void bands_doNotOutrankThemselves() {
        for (FeralTargetPriority priority : FeralTargetPriority.values()) {
            assertFalse(priority.outranks(priority), priority + " should not outrank itself");
        }
    }

    /**
     * The invariant the band spacing exists for: distance is subtracted from the band weight, so the
     * bands have to be further apart than the widest distance the search can ever report — see
     * {@link FeralTargetPriority#MAX_SCORED_DISTANCE}. This is what fails if somebody re-tunes the
     * weights, raises the aggro-radius cap, or widens the rage or leash multipliers without re-tuning
     * the other side. It caught a real spacing bug on the way in.
     */
    @Test
    void bands_areSpacedFurtherApartThanAnyReachableDistance() {
        double worstCaseDistance = FeralTargetPriority.MAX_SCORED_DISTANCE;
        FeralTargetPriority[] all = FeralTargetPriority.values();
        for (int i = 0; i + 1 < all.length; i++) {
            int gap = all[i].weight() - all[i + 1].weight();
            assertTrue(gap > 0, all[i] + " must outweigh " + all[i + 1]);
            // A near candidate in the lower band must never outscore a far one in the higher band.
            assertTrue(FeralTargetSelection.score(at(all[i], worstCaseDistance))
                            > FeralTargetSelection.score(at(all[i + 1], 0.0)),
                    "a point-blank " + all[i + 1] + " outscored a distant " + all[i]);
        }
    }

    // -- picking fresh ---------------------------------------------------------------------------

    @Test
    void pickBest_prefersTheHigherBandEvenWhenFurther() {
        List<FeralTargetSelection.Candidate> candidates = List.of(
                at(ANIMAL, 1.0),      // a chicken underfoot
                at(PLAYER, 20.0));    // a person across the clearing
        assertEquals(1, FeralTargetSelection.pickBestIndex(candidates));
    }

    @Test
    void pickBest_prefersTheNearerWithinABand() {
        List<FeralTargetSelection.Candidate> candidates = List.of(
                at(ANIMAL, 18.0),
                at(ANIMAL, 3.0),
                at(ANIMAL, 9.0));
        assertEquals(1, FeralTargetSelection.pickBestIndex(candidates));
    }

    @Test
    void pickBest_putsVillagersAndGolemsAboveAnimalsAndHostiles() {
        List<FeralTargetSelection.Candidate> candidates = List.of(
                at(HOSTILE, 2.0),
                at(ANIMAL, 4.0),
                at(VILLAGER_OR_GOLEM, 30.0));
        assertEquals(2, FeralTargetSelection.pickBestIndex(candidates));
    }

    @Test
    void pickBest_treatsHostilesAsTheLastResort() {
        List<FeralTargetSelection.Candidate> candidates = List.of(
                at(HOSTILE, 1.0),
                at(OTHER, 1.0));
        assertEquals(0, FeralTargetSelection.pickBestIndex(candidates));
    }

    @Test
    void pickBest_returnsMinusOneForAnEmptyNight() {
        assertEquals(-1, FeralTargetSelection.pickBestIndex(List.of()));
    }

    @Test
    void pickBest_breaksTiesTowardsTheEarlierEntry() {
        // Stability matters: two identical cows at identical range must not make the wolf oscillate.
        List<FeralTargetSelection.Candidate> candidates = List.of(at(ANIMAL, 5.0), at(ANIMAL, 5.0));
        assertEquals(0, FeralTargetSelection.pickBestIndex(candidates));
    }

    // -- switching -------------------------------------------------------------------------------

    @Test
    void switch_takesAnyTargetWhenThereIsNone() {
        assertTrue(FeralTargetSelection.shouldSwitch(null, at(HOSTILE, 30.0)));
    }

    @Test
    void switch_happensWhenAHigherBandAppears() {
        assertTrue(FeralTargetSelection.shouldSwitch(at(ANIMAL, 2.0), at(PLAYER, 25.0)));
        assertTrue(FeralTargetSelection.shouldSwitch(at(HOSTILE, 1.0), at(VILLAGER_OR_GOLEM, 30.0)));
    }

    @Test
    void switch_doesNotHappenForACloserTargetInTheSameBand() {
        // The high-aggression rule: a nearer sheep does not distract a wolf already running one down.
        assertFalse(FeralTargetSelection.shouldSwitch(at(ANIMAL, 25.0), at(ANIMAL, 1.0)));
    }

    @Test
    void switch_doesNotHappenForALowerBandHowever_closeItIs() {
        assertFalse(FeralTargetSelection.shouldSwitch(at(PLAYER, 30.0), at(ANIMAL, 0.5)));
    }

    @Test
    void switch_doesNothingWhenThereIsNothingToSwitchTo() {
        assertFalse(FeralTargetSelection.shouldSwitch(at(ANIMAL, 5.0), null));
        assertFalse(FeralTargetSelection.shouldSwitch(null, null));
    }

    /**
     * A dead, removed or out-of-leash target reaches the decision as {@code null} — validity is
     * expressed by the caller passing null rather than by a second flag, so "the target died" and
     * "there was never a target" are one branch.
     */
    @Test
    void switch_treatsAnInvalidatedTargetAsNoTarget() {
        assertTrue(FeralTargetSelection.shouldSwitch(null, at(OTHER, 40.0)));
    }

    // -- candidate sanitising --------------------------------------------------------------------

    @Test
    void candidate_rejectsAMissingBand() {
        assertThrows(IllegalArgumentException.class, () -> at(null, 1.0));
    }

    @Test
    void candidate_clampsNegativeDistanceToZero() {
        assertEquals(0.0, at(ANIMAL, -5.0).distance());
    }

    @Test
    void candidate_sortsNaNDistanceLastRatherThanThrowing() {
        // Degenerate world state must not stop a werewolf mid-hunt; it must only lose the tie.
        List<FeralTargetSelection.Candidate> candidates = List.of(
                at(PLAYER, Double.NaN),
                at(OTHER, 10.0));
        assertEquals(1, FeralTargetSelection.pickBestIndex(candidates));
    }
}
