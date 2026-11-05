package at.koopro.wizardsandbeasts.creature.ecology;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import static at.koopro.wizardsandbeasts.creature.ecology.EcologyNeed.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Needs as pressures advanced by elapsed time: only declared ones move, and each moves its own way. */
class EcologyNeedsTest {

    private static EcologyProfile needing(Map<EcologyNeed, Integer> needs) {
        return new EcologyProfile(EnumSet.of(EcologyCapability.GRAZER, EcologyCapability.HERD), 16, 10, 16, 6000,
                needs, Map.of(), 12, EcologyProfile.HuntStyle.CHASE, Optional.empty(), Optional.empty());
    }

    @Test
    void aNeedGrowsByElapsedTimeOverItsDeclaredTicksAndStopsAtOne() {
        EcologyNeeds needs = new EcologyNeeds();
        EcologyProfile profile = needing(Map.of(HUNGER, 1000, THIRST, 500));
        needs.advance(profile, EcologyPersonality.NEUTRAL, 250, false, false);
        assertEquals(0.25f, needs.get(HUNGER), 1e-6);
        assertEquals(0.5f, needs.get(THIRST), 1e-6);
        needs.advance(profile, EcologyPersonality.NEUTRAL, 10_000, false, false);
        assertEquals(1.0f, needs.get(HUNGER));
        assertEquals(0.0f, needs.get(ENERGY), "an undeclared need never moves");
        needs.advance(profile, EcologyPersonality.NEUTRAL, -5, false, false);
        assertEquals(1.0f, needs.get(HUNGER), "time going backwards changes nothing");
    }

    @Test
    void restingAndCompanyRecoverFourTimesFasterThanTheyDrain() {
        EcologyNeeds needs = new EcologyNeeds();
        EcologyProfile profile = needing(Map.of(ENERGY, 1000, SOCIAL, 1000));
        needs.set(ENERGY, 1.0f);
        needs.set(SOCIAL, 1.0f);
        needs.advance(profile, EcologyPersonality.NEUTRAL, 125, true, true);
        assertEquals(0.5f, needs.get(ENERGY), 1e-6);
        assertEquals(0.5f, needs.get(SOCIAL), 1e-6);
        needs.advance(profile, EcologyPersonality.NEUTRAL, 100, false, false);
        assertEquals(0.6f, needs.get(ENERGY), 1e-6);
        assertEquals(0.6f, needs.get(SOCIAL), 1e-6);
    }

    @Test
    void temperamentChangesHowFastLonelinessAndTirednessBuild() {
        EcologyProfile profile = needing(Map.of(ENERGY, 1000, SOCIAL, 1000));
        EcologyNeeds gregarious = new EcologyNeeds();
        EcologyNeeds loner = new EcologyNeeds();
        gregarious.advance(profile, new EcologyPersonality(0.5f, 0.5f, 1, 0.5f, 1), 800, false, false);
        loner.advance(profile, new EcologyPersonality(0.5f, 0.5f, 0, 0.5f, 0), 800, false, false);
        assertEquals(1.0f, gregarious.get(SOCIAL), 1e-6, "a sociable one gets lonely sooner (800 / 0.8)");
        assertEquals(800f / 1200f, loner.get(SOCIAL), 1e-6);
        assertEquals(800f / 1200f, gregarious.get(ENERGY), 1e-6, "an energetic one tires later");
        assertEquals(1.0f, loner.get(ENERGY), 1e-6);
    }

    @Test
    void aFrightFadesAndOnlyAlarmsACreatureWithTheNeed() {
        EcologyNeeds needs = new EcologyNeeds();
        needs.alarm(needing(Map.of(HUNGER, 1000)));
        assertEquals(0.0f, needs.get(SAFETY));
        EcologyProfile wary = needing(Map.of(SAFETY, 1000));
        needs.alarm(wary);
        assertEquals(1.0f, needs.get(SAFETY));
        needs.advance(wary, EcologyPersonality.NEUTRAL, 400, false, false);
        assertEquals(0.6f, needs.get(SAFETY), 1e-6);
    }

    @Test
    void satisfyingClampsAtZeroAndTheViewMirrorsTheValues() {
        EcologyNeeds needs = new EcologyNeeds();
        needs.set(HUNGER, 0.3f);
        needs.satisfy(HUNGER, 0.5f);
        assertEquals(0.0f, needs.get(HUNGER));
        needs.set(MAGIC, 0.4f);
        assertEquals(0.4f, needs.view().get(MAGIC), 1e-6);
        assertEquals(0.4f, needs.view().magic(), 1e-6);
        assertEquals(0.9f, EcologyNeeds.View.NONE.with(THIRST, 0.9f).thirst(), 1e-6);
        assertEquals(Map.of(MAGIC, 0.4f), needs.saved());
    }
}
