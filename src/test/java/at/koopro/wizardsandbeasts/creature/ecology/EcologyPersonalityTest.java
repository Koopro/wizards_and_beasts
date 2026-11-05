package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.creature.ecology.EcologyPersonality.Axis;
import at.koopro.wizardsandbeasts.creature.ecology.EcologyPersonality.Range;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.creature.ecology.EcologyCapability.*;
import static at.koopro.wizardsandbeasts.creature.ecology.EcologyIntent.*;
import static at.koopro.wizardsandbeasts.creature.ecology.EcologyScenario.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Individual temperament: deterministic, inside its species' range, and never able to override what a species is. */
class EcologyPersonalityTest {

    /** Every corner of the personality space: each of the five axes at 0 or at 1. */
    private static List<EcologyPersonality> corners() {
        List<EcologyPersonality> out = new ArrayList<>();
        for (int bits = 0; bits < 32; bits++) {
            out.add(new EcologyPersonality(bits & 1, (bits >> 1) & 1, (bits >> 2) & 1, (bits >> 3) & 1, (bits >> 4) & 1));
        }
        return out;
    }

    @Test
    void theSameCreatureIsTheSameCharacterAndDifferentOnesDiffer() {
        UUID id = UUID.fromString("0f7c8a7e-3d1b-4c55-9a8e-2b6f1d4e9c01");
        Map<Axis, Range> ranges = EcologyPersonality.rangesFor(EnumSet.of(PREY), Map.of());
        assertEquals(EcologyPersonality.of(id, ranges), EcologyPersonality.of(id, ranges));
        assertNotEquals(EcologyPersonality.of(id, ranges),
                EcologyPersonality.of(UUID.fromString("0f7c8a7e-3d1b-4c55-9a8e-2b6f1d4e9c02"), ranges));
    }

    @Test
    void everyIndividualFallsInsideItsSpeciesRange() {
        Map<Axis, Range> ranges = EcologyPersonality.rangesFor(EnumSet.of(PREY, HERD),
                Map.of(Axis.CURIOSITY, new Range(0.2f, 0.25f)));
        float lowest = 1, highest = 0;
        for (int i = 0; i < 2000; i++) {
            EcologyPersonality p = EcologyPersonality.of(new UUID(i * 7919L, ~i * 104729L), ranges);
            assertTrue(p.boldness() >= 0.1f && p.boldness() <= 0.45f, "prey are timid: " + p);
            assertTrue(p.sociability() >= 0.6f && p.sociability() <= 0.95f, "herds are sociable: " + p);
            assertTrue(p.curiosity() >= 0.2f && p.curiosity() <= 0.25f, "a stated range wins: " + p);
            assertTrue(p.activity() >= 0.3f && p.activity() <= 0.7f, "the default range: " + p);
            lowest = Math.min(lowest, p.boldness());
            highest = Math.max(highest, p.boldness());
        }
        assertTrue(highest - lowest > 0.3f, "individuals actually vary: " + lowest + ".." + highest);
    }

    @Test
    void factorsStayWithinBoundsAndANeutralCreatureIsUnscaled() {
        EcologyPersonality n = EcologyPersonality.NEUTRAL;
        assertEquals(1.0f, n.raise(0.5f), 1e-6);
        assertEquals(1.0f, n.lower(0.5f), 1e-6);
        assertEquals(0.8f, n.raise(0), 1e-6);
        assertEquals(1.2f, n.raise(1), 1e-6);
        assertEquals(1.2f, n.raise(5), 1e-6, "clamped");
        assertEquals(0.8f, n.lower(1), 1e-6);
    }

    @Test
    void noTemperamentStopsPreyFleeingAPredatorAtItsSide() {
        for (EcologyPersonality p : corners()) {
            creature(PREY).personality(p).sees(predatorAt(8)).decides(FLEE_PREDATOR);
        }
    }

    @Test
    void noTemperamentStopsAnAggressiveCreatureDefendingItsGround() {
        for (EcologyPersonality p : corners()) {
            creature(TERRITORIAL, AGGRESSIVE).personality(p).with(person(10)).decides(DEFEND_TERRITORY);
        }
    }

    @Test
    void noTemperamentGivesASpeciesABehaviourItDoesNotHave() {
        for (EcologyPersonality p : corners()) {
            EcologyIntent friendly = creature(HUMAN_FRIENDLY).personality(p).with(person(10)).decision().intent();
            assertNotEquals(FLEE_PLAYER, friendly, "a friendly creature never runs from people: " + p);
            EcologyIntent plain = creature(HERD).personality(p).feeling(EcologyNeed.CURIOSITY, 1)
                    .with(sneakingPerson(8)).seesPrey(4).decision().intent();
            assertNotEquals(INVESTIGATE, plain, "only a curious species investigates: " + p);
            assertNotEquals(HUNT, plain, "only a predator hunts: " + p);
            EcologyIntent kept = creature(TERRITORIAL).bonded().personality(p).with(person(4)).decision().intent();
            assertNotEquals(DEFEND_TERRITORY, kept, "a kept creature holds no ground: " + p);
        }
    }
}
