package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.creature.profile.CreatureReaction;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A small language for testing one creature decision at a time, with no level and no entity:
 *
 * <pre>{@code
 * creature(PREY, HERD).sees(predatorAt(6)).decides(FLEE_PREDATOR);
 * creature(NOCTURNAL).inDaylight().with(person(3, sneaking())).decides(REST);
 * }</pre>
 *
 * Every scenario starts from {@link Perception#calm()} on a profile with default radii (sense 16, herd 10,
 * territory 16), so a test states only what matters to it.
 */
final class EcologyScenario {

    private final EcologyProfile profile;
    private Perception perception = Perception.calm();

    private EcologyScenario(EcologyProfile profile) {
        this.profile = profile;
    }

    static EcologyScenario creature(EcologyCapability... capabilities) {
        EnumSet<EcologyCapability> set = EnumSet.noneOf(EcologyCapability.class);
        set.addAll(List.of(capabilities));
        return new EcologyScenario(new EcologyProfile(set, 16, 10, 16, 6000));
    }

    EcologyScenario atNight() { perception = perception.withNight(true); return this; }
    EcologyScenario inDaylight() { perception = perception.withNight(false); return this; }
    EcologyScenario underOpenSky() { perception = perception.withExposed(true); return this; }
    EcologyScenario hurtTo(float fraction) { perception = perception.withHealth(fraction); return this; }
    EcologyScenario bonded() { perception = perception.withBonded(true); return this; }
    EcologyScenario fed() { perception = perception.withFed(true); return this; }
    EcologyScenario awayFromHome(double blocks) { perception = perception.withHome(blocks); return this; }
    EcologyScenario sees(Threat threat) { perception = perception.withThreat(threat.sighting); return this; }
    EcologyScenario seesPrey(double distance) { perception = perception.withPrey(at(distance)); return this; }
    EcologyScenario seesItem(double distance) { perception = perception.withItem(at(distance)); return this; }
    EcologyScenario kinCentredAt(double distance) {
        perception = perception.withKin(new Perception.Kin(3, distance, new Vec3(distance, 0, 0)));
        return this;
    }
    EcologyScenario with(Perception.PlayerView person) { perception = perception.withPlayer(person); return this; }
    EcologyScenario reacting(CreatureReaction.Response response) {
        perception = perception.withStimulus(new Perception.Stimulus(response, Vec3.ZERO));
        return this;
    }

    EcologyBrain.Decision decision() {
        return EcologyBrain.decide(profile, perception);
    }

    void decides(EcologyIntent expected) {
        assertEquals(expected, decision().intent(), "for " + profile.capabilities() + " perceiving " + perception);
    }

    void revealing(EcologyCapability expected) {
        assertEquals(expected, decision().cause(), "cause for " + profile.capabilities());
    }

    // ── perception parts ─────────────────────────────────────────────────────────────────────────

    record Threat(Perception.Sighting sighting) {}

    static Threat predatorAt(double distance) {
        return new Threat(at(distance));
    }

    static Perception.PlayerView person(double distance) {
        return new Perception.PlayerView(distance, new Vec3(distance, 0, 0), false, false, false);
    }

    static Perception.PlayerView trustedPerson(double distance) {
        return new Perception.PlayerView(distance, new Vec3(distance, 0, 0), true, false, true);
    }

    static Perception.PlayerView sneakingPerson(double distance) {
        return new Perception.PlayerView(distance, new Vec3(distance, 0, 0), false, false, true);
    }

    static Perception.PlayerView corruptedPerson(double distance) {
        return new Perception.PlayerView(distance, new Vec3(distance, 0, 0), false, true, false);
    }

    private static Perception.Sighting at(double distance) {
        return new Perception.Sighting(distance, new Vec3(distance, 0, 0));
    }
}
