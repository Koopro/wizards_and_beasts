package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.creature.profile.CreatureReaction;
import org.junit.jupiter.api.Test;

import static at.koopro.wizardsandbeasts.creature.ecology.EcologyCapability.*;
import static at.koopro.wizardsandbeasts.creature.ecology.EcologyIntent.*;
import static at.koopro.wizardsandbeasts.creature.ecology.EcologyScenario.*;

/** The ecology policy, one decision at a time. */
class EcologyBrainTest {

    @Test
    void aCreatureWithNoEcologyDoesNothingEcological() {
        creature().sees(predatorAt(2)).hurtTo(0.1f).with(person(2)).decides(NONE);
    }

    @Test
    void preyRunsFromAPredatorInRangeAndIgnoresOneFarOff() {
        creature(PREY).sees(predatorAt(8)).decides(FLEE_PREDATOR);
        creature(PREY).sees(predatorAt(8)).revealing(PREY);
        creature(PREY).sees(predatorAt(14)).decides(NONE); // beyond 0.75 of the sense radius
        creature(FEAR_SENSITIVE).sees(predatorAt(4)).decides(FLEE_PREDATOR);
    }

    @Test
    void aCowardRunsWhenBadlyHurtAndSomethingIsNear() {
        creature(COWARDLY).hurtTo(0.2f).with(person(6)).decides(FLEE_HURT);
        creature(COWARDLY).hurtTo(0.2f).decides(NONE); // nobody to run from
        creature(COWARDLY).hurtTo(0.9f).with(person(6)).decides(NONE);
    }

    @Test
    void avoidanceFollowsTrustAndCorruptionFollowsFurther() {
        creature(HUMAN_AVOIDANT).with(person(8)).decides(FLEE_PLAYER);
        creature(HUMAN_AVOIDANT).with(trustedPerson(8)).decides(NONE);
        creature(HUMAN_AVOIDANT).with(person(12)).decides(NONE);
        creature(MAGICAL_CORRUPTION_SENSITIVE).with(corruptedPerson(15)).decides(FLEE_PLAYER);
        creature(MAGICAL_CORRUPTION_SENSITIVE).with(corruptedPerson(15)).revealing(MAGICAL_CORRUPTION_SENSITIVE);
        creature(HUMAN_AVOIDANT).bonded().with(person(2)).decides(NONE);
    }

    @Test
    void territoryIsWarnedThenDefendedAndAnAggressiveCreatureDoesNotWarn() {
        creature(TERRITORIAL).with(person(12)).decides(WARN_INTRUDER);
        creature(TERRITORIAL).with(person(6)).decides(DEFEND_TERRITORY);
        creature(TERRITORIAL, AGGRESSIVE).with(person(12)).decides(DEFEND_TERRITORY);
        creature(TERRITORIAL).with(trustedPerson(6)).decides(NONE);
        creature(TERRITORIAL).bonded().with(person(6)).decides(NONE);
    }

    @Test
    void aPredatorHuntsWhenHungryAndANocturnalOneOnlyAtNight() {
        creature(PREDATOR).seesPrey(10).decides(HUNT);
        creature(PREDATOR).fed().seesPrey(10).decides(NONE);
        creature(PREDATOR).bonded().seesPrey(10).decides(NONE);
        creature(PREDATOR, NOCTURNAL).inDaylight().seesPrey(10).decides(REST);
        creature(PREDATOR, NOCTURNAL).atNight().seesPrey(10).decides(HUNT);
    }

    @Test
    void aNocturnalCreatureFindsCoverThenRestsAndAQuietApproachDoesNotWakeIt() {
        creature(NOCTURNAL).inDaylight().underOpenSky().decides(SEEK_SHELTER);
        creature(NOCTURNAL).inDaylight().decides(REST);
        creature(NOCTURNAL).atNight().decides(NONE);
        // Asleep, a creature that avoids people does not notice a sneaking one beside it...
        creature(NOCTURNAL, HUMAN_AVOIDANT).inDaylight().with(sneakingPerson(2)).decides(REST);
        // ...nor a loud one at a distance, but one walking right up wakes it and it runs.
        creature(NOCTURNAL, HUMAN_AVOIDANT).inDaylight().with(person(8)).decides(REST);
        creature(NOCTURNAL, HUMAN_AVOIDANT).inDaylight().with(person(3)).decides(FLEE_PLAYER);
    }

    @Test
    void herdHomeAndGround() {
        creature(HERD).kinCentredAt(14).decides(REGROUP);
        creature(HERD).kinCentredAt(6).decides(NONE);
        creature(HERD).bonded().kinCentredAt(14).decides(NONE);
        creature(TREE_DWELLING).awayFromHome(20).decides(RETURN_HOME);
        creature(TREASURE_SEEKER).seesItem(8).decides(SEEK_ITEM);
        creature(SCAVENGER).seesItem(8).revealing(SCAVENGER);
    }

    @Test
    void curiosityNeedsAQuietPersonAndFriendlinessDoesNot() {
        creature(CURIOUS).with(sneakingPerson(8)).decides(INVESTIGATE);
        creature(CURIOUS).with(person(8)).decides(NONE);
        creature(CURIOUS).with(sneakingPerson(2)).decides(NONE); // close enough already
        creature(HUMAN_FRIENDLY).with(person(10)).decides(APPROACH_PLAYER);
        creature(HUMAN_FRIENDLY).with(corruptedPerson(10)).decides(NONE);
    }

    @Test
    void aReactionInForceComesFirstAndSurvivalComesBeforeAppetite() {
        creature(PREDATOR).seesPrey(5).reacting(CreatureReaction.Response.FLEE).decides(FLEE_STIMULUS);
        creature(CURIOUS).reacting(CreatureReaction.Response.INVESTIGATE).decides(INVESTIGATE);
        creature(HERD).reacting(CreatureReaction.Response.IGNORE).kinCentredAt(14).decides(REGROUP);
        creature(PREY, PREDATOR).sees(predatorAt(5)).seesPrey(5).decides(FLEE_PREDATOR);
        creature(TREASURE_SEEKER, PREY).sees(predatorAt(5)).seesItem(2).decides(FLEE_PREDATOR);
    }
}
