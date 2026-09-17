package at.koopro.wizardsandbeasts.wand.allegiance;

import at.koopro.wizardsandbeasts.wand.registry.WandTemperament;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceRules.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wand–wizard relationship, rule by rule. One scenario per test; names are the scenarios.
 */
class WandAllegianceRulesTest {

    private static final WandTemperament ORDINARY = WandTemperament.NEUTRAL;
    private static final CastCircumstances CALM = new CastCircumstances(false, false, false);
    private static final UUID HARRY = UUID.nameUUIDFromBytes("harry".getBytes());
    private static final UUID DRACO = UUID.nameUUIDFromBytes("draco".getBytes());

    private static WandTemperament temperament(float growth, int extraWins, float transferBonus, float darkCost,
                                               boolean danger, boolean backfire, float foreign, float passedOn,
                                               boolean death, float loyalCooldown) {
        return new WandTemperament(growth, extraWins, transferBonus, darkCost, danger, backfire, foreign, passedOn,
                death, loyalCooldown);
    }

    // ── state ───────────────────────────────────────────────────────────────────────────────────

    @Test
    void anotherWizardsWand_isUnfamiliarWhateverItsBond() {
        assertEquals(WandBondState.UNFAMILIAR, state(false, 1.0f));
        assertEquals(WandBondState.UNFAMILIAR, state(false, 0.0f));
    }

    @Test
    void theMastersBond_readsAsRisingStates() {
        assertEquals(WandBondState.RELUCTANT, state(true, 0.0f));
        assertEquals(WandBondState.RELUCTANT, state(true, Math.nextDown(RELUCTANT_BELOW)));
        assertEquals(WandBondState.ACCEPTING, state(true, RELUCTANT_BELOW));
        assertEquals(WandBondState.LOYAL, state(true, ACCEPTING_BELOW));
        assertEquals(WandBondState.MASTERED, state(true, LOYAL_BELOW));
    }

    // ── first bond ──────────────────────────────────────────────────────────────────────────────

    /** "The wand chooses the wizard": a wand that chose you is never reluctant about it. */
    @Test
    void aChosenWand_startsAcceptingAndAPerfectMatchStartsLoyal() {
        assertEquals(WandBondState.ACCEPTING, state(true, startingBond(0.65f, 0.65f)));
        assertEquals(WandBondState.LOYAL, state(true, startingBond(1.0f, 0.65f)));
        assertTrue(startingBond(0.9f, 0.65f) > startingBond(0.7f, 0.65f), "a better match starts closer");
    }

    // ── growth ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void successfulCasts_deepenTheBondTowardMastery() {
        float bond = startingBond(0.65f, 0.65f);
        int casts = 0;
        while (state(true, bond) != WandBondState.MASTERED && casts < 10_000) {
            bond = bondAfterSuccessfulCast(bond, ORDINARY, CALM);
            casts++;
        }
        assertEquals(WandBondState.MASTERED, state(true, bond));
        assertTrue(casts > 50 && casts < 400, "mastery should take a campaign, not a moment or a lifetime: " + casts);
    }

    @Test
    void aPhoenixFeather_bondsSlowerThanADragonHeartstring() {
        WandTemperament phoenix = temperament(0.5f, 1, 0, 0, false, false, 1, 1, false, 0.9f);
        WandTemperament dragon = temperament(1.5f, -1, 0.25f, 0, false, false, 1, 1, false, 1);
        assertTrue(bondAfterSuccessfulCast(0.5f, phoenix, CALM) < bondAfterSuccessfulCast(0.5f, ORDINARY, CALM));
        assertTrue(bondAfterSuccessfulCast(0.5f, dragon, CALM) > bondAfterSuccessfulCast(0.5f, ORDINARY, CALM));
    }

    /** Blackthorn: a bond forged only in danger. */
    @Test
    void aBlackthornWand_growsOnlyThroughDanger() {
        WandTemperament blackthorn = temperament(1, 1, 0, 0, true, false, 1, 1, false, 1);
        assertEquals(0.5f, bondAfterSuccessfulCast(0.5f, blackthorn, CALM));
        assertTrue(bondAfterSuccessfulCast(0.5f, blackthorn, new CastCircumstances(false, true, false)) > 0.5f);
    }

    /** Unicorn hair and rowan: the Dark Arts cost the bond, and gain it nothing. */
    @Test
    void aWandThatResentsTheDarkArts_losesBondToThem() {
        WandTemperament unicorn = temperament(1, 1, 0, 0.03f, false, false, 1, 1, false, 1);
        CastCircumstances dark = new CastCircumstances(true, false, false);
        assertEquals(0.47f, bondAfterSuccessfulCast(0.5f, unicorn, dark), 1.0e-6f);
        assertTrue(bondAfterSuccessfulCast(0.5f, ORDINARY, dark) > 0.5f, "an ordinary wand does not care");
    }

    /** Thestral tail hair: loyal, but never mastered by one who has not seen death. */
    @Test
    void aThestralCore_stopsShortOfMasteryWithoutAWitnessOfDeath() {
        WandTemperament thestral = temperament(1, 0, 0, 0, false, false, 1, 1, true, 1);
        float bond = Math.nextDown(LOYAL_BELOW);
        for (int i = 0; i < 100; i++) {
            bond = bondAfterSuccessfulCast(bond, thestral, CALM);
        }
        assertEquals(WandBondState.LOYAL, state(true, bond));
        float witnessed = bondAfterSuccessfulCast(bond, thestral, new CastCircumstances(false, false, true));
        assertEquals(WandBondState.MASTERED, state(true, witnessed));
    }

    /** A bond already mastered (an older save) is not taken away by the cap. */
    @Test
    void theDeathWitnessCap_neverLowersAnExistingBond() {
        WandTemperament thestral = temperament(1, 0, 0, 0, false, false, 1, 1, true, 1);
        assertEquals(1.0f, bondAfterSuccessfulCast(1.0f, thestral, CALM));
    }

    // ── neglect ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void neglect_coolsABondSlowlyAndNeverBreaksIt() {
        assertEquals(0.9f, bondAfterNeglect(0.9f, NEGLECT_GRACE_TICKS), "inside the grace nothing happens");
        assertTrue(bondAfterNeglect(0.9f, NEGLECT_GRACE_TICKS + 5 * TICKS_PER_DAY) < 0.9f);
        assertEquals(RELUCTANT_BELOW, bondAfterNeglect(0.9f, NEGLECT_GRACE_TICKS + 1000 * TICKS_PER_DAY));
        assertEquals(0.2f, bondAfterNeglect(0.2f, NEGLECT_GRACE_TICKS + 1000 * TICKS_PER_DAY), "never raises a bond");
    }

    // ── winning a wand ──────────────────────────────────────────────────────────────────────────

    @Test
    void anOrdinaryWand_isWonByTwoDefeatsInARow() {
        int needed = winsToTransfer(ORDINARY, false);
        assertEquals(2, needed);
        DefeatOutcome first = defeat(WandBondHistory.EMPTY, DRACO, needed);
        assertFalse(first.transferred());
        DefeatOutcome second = defeat(first.history(), DRACO, needed);
        assertTrue(second.transferred());
    }

    @Test
    void aDifferentChallenger_startsTheTallyAgain() {
        int needed = winsToTransfer(ORDINARY, false);
        DefeatOutcome draco = defeat(WandBondHistory.EMPTY, DRACO, needed);
        DefeatOutcome harry = defeat(draco.history(), HARRY, needed);
        assertFalse(harry.transferred());
        assertEquals(1, harry.history().challengerWins());
    }

    @Test
    void theMastersSuccessfulCast_answersTheChallenge() {
        WandBondHistory challenged = defeat(WandBondHistory.EMPTY, DRACO, 2).history();
        WandBondHistory reasserted = challenged.withMasterCast(100L);
        assertEquals(0, reasserted.challengerWins());
        assertFalse(defeat(reasserted, DRACO, 2).transferred(), "the tally starts over after the master casts");
    }

    @Test
    void temperament_setsHowHardAWandIsToWin() {
        WandTemperament dragon = temperament(1.5f, -1, 0.25f, 0, false, false, 1, 1, false, 1);
        WandTemperament unicornAsh = temperament(1, 2, 0, 0, false, false, 1, 1, false, 1);
        assertEquals(1, winsToTransfer(dragon, false), "dragon heartstring can change allegiance if won");
        assertEquals(4, winsToTransfer(unicornAsh, false));
        assertEquals(1, winsToTransfer(temperament(1, -9, 0, 0, false, false, 1, 1, false, 1), false), "never below one");
    }

    @Test
    void theElderWand_goesWithTheFirstDefeat() {
        assertEquals(1, winsToTransfer(temperament(1, 3, 0, 0, false, false, 1, 1, false, 1), true));
    }

    @Test
    void aWonWand_startsReluctantUnlessItsCoreBondsStronglyWithItsCurrentOwner() {
        assertEquals(WandBondState.RELUCTANT, state(true, bondAfterTransfer(ORDINARY)));
        WandTemperament dragon = temperament(1.5f, -1, 0.25f, 0, false, false, 1, 1, false, 1);
        assertEquals(WandBondState.ACCEPTING, state(true, bondAfterTransfer(dragon)));
        WandTemperament extreme = temperament(1, 0, 5.0f, 0, false, false, 1, 1, false, 1);
        assertEquals(WandBondState.ACCEPTING, state(true, bondAfterTransfer(extreme)), "a won wand is never loyal at once");
    }

    // ── power ───────────────────────────────────────────────────────────────────────────────────

    @Test
    void power_risesWithTheRelationship() {
        float previous = 0.0f;
        for (WandBondState state : WandBondState.values()) {
            float power = powerMultiplier(state, ORDINARY, false, false);
            assertTrue(power > previous, state + " should lend more power than the state before it");
            previous = power;
        }
        assertEquals(1.0f, powerMultiplier(WandBondState.ACCEPTING, ORDINARY, false, false));
    }

    /** Ash: cleaves to its first master; a stranger or a later master gets less of it. */
    @Test
    void anAshWand_servesAStrangerAndALaterMasterLess() {
        WandTemperament ash = temperament(1, 1, 0, 0, false, false, 0.8f, 0.8f, false, 1);
        assertTrue(powerMultiplier(WandBondState.UNFAMILIAR, ash, false, false)
                < powerMultiplier(WandBondState.UNFAMILIAR, ORDINARY, false, false));
        assertTrue(powerMultiplier(WandBondState.LOYAL, ash, true, false)
                < powerMultiplier(WandBondState.LOYAL, ash, false, false));
        assertEquals(powerMultiplier(WandBondState.LOYAL, ORDINARY, true, false),
                powerMultiplier(WandBondState.LOYAL, ORDINARY, false, false), "an ordinary wand does not mind");
    }

    /** The Elder Wand is extraordinary only for its master — and worse than an ordinary wand for anyone else. */
    @Test
    void theElderWand_isExtraordinaryOnlyWhenMastered() {
        assertEquals(ELDER_MASTERED_POWER, powerMultiplier(WandBondState.MASTERED, ORDINARY, false, true), 1.0e-6f);
        for (WandBondState state : new WandBondState[]{WandBondState.UNFAMILIAR, WandBondState.RELUCTANT,
                WandBondState.ACCEPTING, WandBondState.LOYAL}) {
            assertTrue(powerMultiplier(state, ORDINARY, false, true) < powerMultiplier(state, ORDINARY, false, false),
                    "an unmastered Elder Wand should serve worse than an ordinary wand at " + state);
        }
        assertTrue(powerMultiplier(WandBondState.MASTERED, ORDINARY, false, true)
                > powerMultiplier(WandBondState.MASTERED, ORDINARY, false, false));
    }

    @Test
    void cooldown_isLongerForAStrangerAndShorterForALoyalPhoenix() {
        WandTemperament phoenix = temperament(0.5f, 1, 0, 0, false, false, 1, 1, false, 0.9f);
        assertTrue(cooldownMultiplier(WandBondState.UNFAMILIAR, ORDINARY) > 1.0f);
        assertEquals(1.0f, cooldownMultiplier(WandBondState.LOYAL, ORDINARY));
        assertEquals(0.9f, cooldownMultiplier(WandBondState.LOYAL, phoenix));
        assertEquals(1.0f, cooldownMultiplier(WandBondState.ACCEPTING, phoenix), "only once loyal");
    }

    // ── failure ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void aBrokenWand_alwaysBackfires() {
        assertTrue(backfires(WandBondState.MASTERED, ORDINARY, BROKEN_AT));
        assertFalse(backfires(WandBondState.MASTERED, ORDINARY, Math.nextUp(BROKEN_AT)));
    }

    /** Hawthorn: spells backfire when badly handled — read as "in a hand it does not know". */
    @Test
    void aHawthornWand_backfiresOnlyInAStrangersHand() {
        WandTemperament hawthorn = temperament(1, 0, 0, 0, false, true, 1, 1, false, 1);
        assertTrue(backfires(WandBondState.UNFAMILIAR, hawthorn, 1.0f));
        assertFalse(backfires(WandBondState.RELUCTANT, hawthorn, 1.0f),
                "a newly won hawthorn must still be castable, or its bond could never grow");
        assertFalse(backfires(WandBondState.UNFAMILIAR, ORDINARY, 1.0f));
    }

    @Test
    void explosions_wearAHeldWandDownToBroken() {
        float integrity = 1.0f;
        integrity = integrityAfterExplosion(integrity, 24.0f);
        assertEquals(Condition.WORN, condition(integrity));
        integrity = integrityAfterExplosion(integrity, 24.0f);
        assertEquals(Condition.DAMAGED, condition(integrity));
        integrity = integrityAfterExplosion(integrity, 24.0f);
        assertEquals(Condition.BROKEN, condition(integrity));
        assertEquals(0.0f, integrityAfterExplosion(0.05f, 100.0f));
    }

    @Test
    void temperament_combinesWoodAndCoreEitherWayRound() {
        WandTemperament ash = temperament(1, 1, 0, 0, false, false, 0.8f, 0.8f, false, 1);
        WandTemperament phoenix = temperament(0.5f, 1, 0, 0, false, false, 1, 1, false, 0.9f);
        assertEquals(ash.combine(phoenix), phoenix.combine(ash));
        assertEquals(ORDINARY, ORDINARY.combine(ORDINARY));
        WandTemperament both = ash.combine(phoenix);
        assertEquals(2, both.extraWins());
        assertEquals(0.5f, both.bondGrowth());
        assertEquals(0.8f, both.passedOnPower());
        assertEquals(0.9f, both.loyalCooldown());
    }
}
