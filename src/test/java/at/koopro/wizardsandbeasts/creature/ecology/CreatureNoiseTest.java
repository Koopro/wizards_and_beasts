package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.creature.ecology.CreatureStimuli.Loudness;
import at.koopro.wizardsandbeasts.creature.profile.CreatureReaction.Response;
import org.junit.jupiter.api.Test;

import static at.koopro.wizardsandbeasts.creature.ecology.EcologyCapability.*;
import static at.koopro.wizardsandbeasts.creature.ecology.EcologyScenario.*;
import static at.koopro.wizardsandbeasts.creature.profile.CreatureReaction.Stimulus.NOISE;
import static at.koopro.wizardsandbeasts.creature.profile.CreatureReaction.Stimulus.PLAYER;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** What an undeclared creature does about a noise or a running person, by loudness and temperament. */
class CreatureNoiseTest {

    private static Response heard(EcologyScenario creature, EcologyPersonality me,
                                  at.koopro.wizardsandbeasts.creature.profile.CreatureReaction.Stimulus kind,
                                  Loudness loudness) {
        return CreatureStimuli.heard(creature.profile(), kind, loudness, me);
    }

    @Test
    void aCrashScattersPreyRaisesTheTerritorialAndDrawsTheCurious() {
        assertEquals(Response.FLEE, heard(creature(PREY), bold(), NOISE, Loudness.LOUD));
        assertEquals(Response.BECOME_ALERT, heard(creature(TERRITORIAL), bold(), NOISE, Loudness.LOUD));
        assertEquals(Response.INVESTIGATE, heard(creature(CURIOUS), bold(), NOISE, Loudness.LOUD));
        assertEquals(Response.IGNORE, heard(creature(HERD), bold(), NOISE, Loudness.LOUD));
    }

    @Test
    void anOrdinaryNoiseFrightensOnlyTheTimid() {
        assertEquals(Response.FLEE, heard(creature(PREY), timid(), NOISE, Loudness.MEDIUM));
        assertEquals(Response.IGNORE, heard(creature(PREY), bold(), NOISE, Loudness.MEDIUM));
        assertEquals(Response.INVESTIGATE, heard(creature(CURIOUS), bold(), NOISE, Loudness.MEDIUM));
        assertEquals(Response.INVESTIGATE, heard(creature(CURIOUS), bold(), NOISE, Loudness.SOFT));
        assertEquals(Response.IGNORE, heard(creature(PREY), timid(), NOISE, Loudness.SOFT));
    }

    @Test
    void aRunningPersonFrightensTheShyAndNeverDrawsTheCurious() {
        assertEquals(Response.FLEE, heard(creature(HUMAN_AVOIDANT), bold(), PLAYER, Loudness.MEDIUM));
        assertEquals(Response.FLEE, heard(creature(PREY), timid(), PLAYER, Loudness.MEDIUM));
        assertEquals(Response.IGNORE, heard(creature(PREY), bold(), PLAYER, Loudness.MEDIUM));
        assertEquals(Response.BECOME_ALERT, heard(creature(TERRITORIAL), bold(), PLAYER, Loudness.MEDIUM));
        assertEquals(Response.IGNORE, heard(creature(CURIOUS), bold(), PLAYER, Loudness.MEDIUM));
    }
}
