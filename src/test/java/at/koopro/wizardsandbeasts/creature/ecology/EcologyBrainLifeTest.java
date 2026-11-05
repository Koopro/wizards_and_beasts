package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.creature.profile.CreatureReaction;
import org.junit.jupiter.api.Test;

import static at.koopro.wizardsandbeasts.creature.ecology.EcologyCapability.*;
import static at.koopro.wizardsandbeasts.creature.ecology.EcologyIntent.*;
import static at.koopro.wizardsandbeasts.creature.ecology.EcologyNeed.*;
import static at.koopro.wizardsandbeasts.creature.ecology.EcologyScenario.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Daily life in the ecology policy: hazards, needs, rhythm, weather, kin, and the hysteresis of carrying on. */
class EcologyBrainLifeTest {

    @Test
    void aHazardComesBeforeEverythingElse() {
        creature(PREY).inHazard().sees(predatorAt(2)).decides(FLEE_HAZARD);
        creature(GRAZER).feeling(HUNGER, 0.9f).seesForage(3).inHazard().decides(FLEE_HAZARD);
        creature().inHazard().decides(NONE); // no ecology, no ecological answer
    }

    @Test
    void aNeedIsActedOnOnlyWhenPressingAndWhenThereIsSomethingToAnswerIt() {
        creature(GRAZER).feeling(HUNGER, 0.7f).seesForage(6).decides(FORAGE);
        creature(GRAZER).feeling(HUNGER, 0.7f).seesForage(6).revealing(GRAZER);
        creature(BROWSER).feeling(HUNGER, 0.7f).seesForage(6).revealing(BROWSER);
        creature(GRAZER).feeling(HUNGER, 0.3f).seesForage(6).decides(NONE);
        creature(GRAZER).feeling(HUNGER, 0.9f).decides(NONE); // nothing to eat in sight
        creature(GRAZER).feeling(THIRST, 0.7f).seesWater(8).decides(DRINK);
        creature(GRAZER).feeling(THIRST, 0.7f).decides(NONE);
    }

    @Test
    void theMostPressingNeedWins() {
        creature(GRAZER).feeling(HUNGER, 0.6f).feeling(THIRST, 0.9f).seesForage(4).seesWater(10).decides(DRINK);
        creature(GRAZER).feeling(HUNGER, 0.9f).feeling(THIRST, 0.6f).seesForage(4).seesWater(10).decides(FORAGE);
        // Food on the ground answers hunger too.
        creature(SCAVENGER).feeling(HUNGER, 0.7f).seesItem(5).decides(FORAGE);
        creature(SCAVENGER).feeling(HUNGER, 0.7f).seesCarrion(10).decides(SCAVENGE);
    }

    @Test
    void aKeptCreatureLeavesItsPersonOnlyForAnUrgentNeed() {
        creature(GRAZER).bonded().feeling(HUNGER, 0.7f).seesForage(6).decides(NONE);
        creature(GRAZER).bonded().feeling(HUNGER, 0.85f).seesForage(6).decides(FORAGE);
        creature(EcologyCapability.SOCIAL).bonded().feeling(EcologyNeed.SOCIAL, 0.95f).nearestKinAt(10).decides(NONE);
    }

    @Test
    void eachRhythmSleepsInItsOwnHours() {
        creature(DIURNAL).atNight().decides(SLEEP);
        creature(DIURNAL).atNight().revealing(DIURNAL);
        creature(DIURNAL).inDaylight().decides(NONE);
        creature(DIURNAL).bonded().atNight().decides(NONE);
        creature(CREPUSCULAR).atDayTime(6000).decides(SLEEP);   // midday
        creature(CREPUSCULAR).atDayTime(18000).decides(SLEEP);  // the dead of night
        creature(CREPUSCULAR).atDayTime(12500).decides(NONE);   // dusk: about
        creature(CREPUSCULAR).atDayTime(23500).decides(NONE);   // dawn: about
        creature(HERD).feeling(ENERGY, 0.95f).decides(SLEEP);   // worn out, whatever the hour
        creature(HERD).feeling(ENERGY, 0.5f).decides(NONE);
    }

    @Test
    void aSleepingCreatureHearsOnlyAPersonAtItsSideAndAHunterSleepsThroughItsOffHours() {
        creature(DIURNAL, HUMAN_AVOIDANT).atNight().with(person(8)).decides(SLEEP);
        creature(DIURNAL, HUMAN_AVOIDANT).atNight().with(sneakingPerson(2)).decides(SLEEP);
        creature(DIURNAL, HUMAN_AVOIDANT).atNight().with(person(3)).decides(FLEE_PLAYER);
        creature(PREDATOR, DIURNAL).atNight().seesPrey(5).decides(SLEEP);
        creature(PREDATOR, DIURNAL).inDaylight().seesPrey(5).decides(HUNT);
    }

    @Test
    void weatherShyCreaturesGetUnderCoverAndStayThereWhileItRains() {
        EcologyScenario wet = creature(WEATHER_SHY).raining().underOpenSky();
        wet.decides(SHELTER_FROM_WEATHER);
        wet.revealing(WEATHER_SHY);
        creature(WEATHER_SHY).raining().underCover().decides(NONE);
        assertTrue(creature(WEATHER_SHY).raining().underCover().sustains(SHELTER_FROM_WEATHER),
                "under cover while it still rains, it stays under cover");
        assertFalse(creature(WEATHER_SHY).underCover().sustains(SHELTER_FROM_WEATHER), "dry again: out it comes");
        creature(HERD).raining().underOpenSky().decides(NONE); // rain alone means nothing to a creature that is not shy
    }

    @Test
    void thunderSendsOnlyTheTimidToCover() {
        creature(PREY).personality(timid()).thundering().underOpenSky().decides(SHELTER_FROM_WEATHER);
        creature(PREY).personality(bold()).thundering().underOpenSky().decides(NONE);
        creature(TERRITORIAL).personality(timid()).thundering().underOpenSky().decides(NONE);
    }

    @Test
    void aCreatureOutOfItsClimateLooksForCover() {
        creature(COLD_ADAPTED).inDaylight().temperature(2.0f).underOpenSky().decides(SHELTER_FROM_WEATHER);
        creature(COLD_ADAPTED).inDaylight().temperature(2.0f).underOpenSky().revealing(COLD_ADAPTED);
        creature(COLD_ADAPTED).atNight().temperature(2.0f).underOpenSky().decides(NONE);
        creature(COLD_ADAPTED).inDaylight().temperature(0.2f).underOpenSky().decides(NONE);
        creature(WARMTH_LOVING).temperature(0.0f).underOpenSky().decides(SHELTER_FROM_WEATHER);
        creature(WARMTH_LOVING).temperature(0.8f).underOpenSky().decides(NONE);
    }

    @Test
    void solitaryCreaturesKeepTheirDistanceAndLonelyOnesSeekCompany() {
        creature(SOLITARY).nearestKinAt(6).decides(KEEP_DISTANCE);
        creature(SOLITARY).nearestKinAt(20).decides(NONE);
        creature(SOLITARY).bonded().nearestKinAt(6).decides(NONE);
        creature(EcologyCapability.SOCIAL).feeling(EcologyNeed.SOCIAL, 0.7f).nearestKinAt(10).decides(SOCIALISE);
        creature(EcologyCapability.SOCIAL).feeling(EcologyNeed.SOCIAL, 0.7f).nearestKinAt(3).decides(NONE); // in company already
        creature(EcologyCapability.SOCIAL).feeling(EcologyNeed.SOCIAL, 0.3f).nearestKinAt(10).decides(NONE);
    }

    @Test
    void aStalkerCreepsThenChargesAndAHungerDrivenHunterWaitsForHunger() {
        creature(PREDATOR).stalker().seesPrey(12).decides(STALK);
        creature(PREDATOR).stalker().seesPrey(5).decides(HUNT);
        creature(PREDATOR).seesPrey(12).decides(HUNT);
        creature(PREDATOR).feeling(HUNGER, 0.3f).seesPrey(5).decides(NONE);
        creature(PREDATOR).feeling(HUNGER, 0.7f).seesPrey(5).decides(HUNT);
    }

    @Test
    void scavengersGoToRemainsAndGroomersGroomOnlyWhenAtEase() {
        creature(SCAVENGER).seesCarrion(10).decides(SCAVENGE);
        creature(SCAVENGER).seesCarrion(30).decides(NONE);
        creature(GROOMING).decides(GROOM);
        creature(GROOMING).sees(predatorAt(10)).decides(NONE);
        creature(GROOMING).with(person(5)).decides(NONE);
        creature(GROOMING).with(trustedPerson(5)).decides(GROOM);
        creature(GROOMING).feeling(SAFETY, 0.9f).decides(NONE);
    }

    @Test
    void boredomAndNerveLetACuriousCreatureInspectEvenALoudPerson() {
        EcologyPersonality nosy = new EcologyPersonality(0.6f, 0.9f, 0.5f, 0.5f, 0.5f);
        creature(CURIOUS).with(person(8)).decides(NONE);
        creature(CURIOUS).feeling(CURIOSITY, 0.7f).personality(nosy).with(person(8)).decides(INVESTIGATE);
        creature(CURIOUS).feeling(CURIOSITY, 0.7f).personality(timid()).with(person(8)).decides(NONE);
        // A fright puts curiosity off.
        creature(CURIOUS).feeling(SAFETY, 0.9f).with(sneakingPerson(8)).decides(NONE);
    }

    @Test
    void aNoiseInvestigatedTeachesCuriosityAndASpellTeachesMagicalSense() {
        creature(CURIOUS).reacting(CreatureReaction.Response.INVESTIGATE, CreatureReaction.Stimulus.NOISE).revealing(CURIOUS);
        creature(CURIOUS).reacting(CreatureReaction.Response.INVESTIGATE, CreatureReaction.Stimulus.SPELL).revealing(MAGICAL_SENSE);
    }

    @Test
    void temperamentMovesTheFleeRadiusAndAFrightWidensIt() {
        creature(PREY).personality(timid()).sees(predatorAt(13)).decides(FLEE_PREDATOR); // 12 × 1.2
        creature(PREY).sees(predatorAt(13)).decides(NONE);
        creature(PREY).personality(bold()).sees(predatorAt(10)).decides(NONE);         // 12 × 0.8
        creature(PREY).personality(bold()).sees(predatorAt(9)).decides(FLEE_PREDATOR);
        creature(PREY).feeling(SAFETY, 0.9f).sees(predatorAt(14)).decides(FLEE_PREDATOR);
    }

    @Test
    void carryingOnIsLooserThanStarting() {
        // Fled from inside 12 blocks, a predator is fled until it is out of sense range (16).
        assertTrue(creature(PREY).sees(predatorAt(14)).sustains(FLEE_PREDATOR));
        assertFalse(creature(PREY).sees(predatorAt(17)).sustains(FLEE_PREDATOR));
        // A meal started at 0.55 goes on until hunger is nearly gone.
        assertTrue(creature(GRAZER).feeling(HUNGER, 0.2f).seesForage(1).sustains(FORAGE));
        assertFalse(creature(GRAZER).feeling(HUNGER, 0.05f).seesForage(1).sustains(FORAGE));
        // A solitary creature keeps moving off a little past its personal space.
        assertTrue(creature(SOLITARY).nearestKinAt(13).sustains(KEEP_DISTANCE));
        assertFalse(creature(SOLITARY).nearestKinAt(15).sustains(KEEP_DISTANCE));
        assertFalse(creature(PREY).sustains(NONE));
    }
}
