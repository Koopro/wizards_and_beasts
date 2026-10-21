package at.koopro.wizardsandbeasts.skill.build;

import at.koopro.wizardsandbeasts.apparition.PlayerApparitionPoints;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchTier;
import at.koopro.wizardsandbeasts.skill.build.capstone.CounterJinx;
import at.koopro.wizardsandbeasts.skill.build.capstone.ScholarsMethod;
import at.koopro.wizardsandbeasts.skill.build.capstone.SeasonedTraveller;
import at.koopro.wizardsandbeasts.skill.build.capstone.WandkeepersHands;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure halves of the capstone mechanics; the wiring is covered by {@code BuildTests} in the game tests. */
class CapstoneRulesTest {

    @Test
    void theCounterJinxReadiesAttackSpellsOnly() {
        Map<String, Long> cooldowns = Map.of("stupefy", 150L, "lumos", 150L, "incendio", 90L, "diffindo", 300L);
        Map<String, SpellCategory> category = Map.of("stupefy", SpellCategory.COMBAT, "lumos", SpellCategory.UTILITY,
                "incendio", SpellCategory.COMBAT, "diffindo", SpellCategory.COMBAT);
        assertEquals(List.of("diffindo", "stupefy"), CounterJinx.readied(cooldowns, 100L, category::get),
                "utility spells and already-ready spells are untouched");
    }

    @Test
    void theCounterJinxHasAnInternalCooldown() {
        assertTrue(CounterJinx.ready(null, 0L));
        assertFalse(CounterJinx.ready(100L, 100L + CounterJinx.INTERNAL_COOLDOWN_TICKS - 1));
        assertTrue(CounterJinx.ready(100L, 100L + CounterJinx.INTERNAL_COOLDOWN_TICKS));
    }

    @Test
    void aStrangeWandAnswersAWandkeeperReluctantly() {
        assertEquals(WandBondState.RELUCTANT, WandkeepersHands.answeringState(WandBondState.UNFAMILIAR, true));
        assertEquals(WandBondState.UNFAMILIAR, WandkeepersHands.answeringState(WandBondState.UNFAMILIAR, false));
        assertEquals(WandBondState.LOYAL, WandkeepersHands.answeringState(WandBondState.LOYAL, true),
                "a wand that is already yours is not changed");
    }

    @Test
    void aWandkeeperMendsWearButNotASnappedWand() {
        assertTrue(WandkeepersHands.mayMend(true, 0.5f));
        assertFalse(WandkeepersHands.mayMend(true, 0.10f), "snapped: only the Elder Wand");
        assertFalse(WandkeepersHands.mayMend(true, 1.0f), "nothing to mend");
        assertFalse(WandkeepersHands.mayMend(false, 0.5f));
    }

    @Test
    void theScholarNeedsHalfThePractice() {
        assertEquals(25, ScholarsMethod.gate(50));
        assertEquals(100, ScholarsMethod.gate(200));
        assertEquals(18, ScholarsMethod.gate(35), "rounded up: half of an odd gate is never generous");
        assertEquals(0, ScholarsMethod.gate(0));
    }

    @Test
    void aSeasonedTravellerLandsOneRungLighter() {
        assertEquals(SplinchTier.CLEAN, SeasonedTraveller.soften(SplinchTier.MINOR, true));
        assertEquals(SplinchTier.MINOR, SeasonedTraveller.soften(SplinchTier.MAJOR, true));
        assertEquals(SplinchTier.MAJOR, SeasonedTraveller.soften(SplinchTier.CATASTROPHIC, true),
                "a catastrophic miss still arrives");
        assertEquals(SplinchTier.CATASTROPHIC, SeasonedTraveller.soften(SplinchTier.CATASTROPHIC, false));
    }

    @Test
    void onlyTheExplorerCapstoneLiftsTheDestinationCeiling() {
        assertEquals(PlayerApparitionPoints.MAX_POINTS + SeasonedTraveller.EXTRA_DESTINATIONS,
                PlayerApparitionPoints.SEASONED_CEILING);
    }
}
