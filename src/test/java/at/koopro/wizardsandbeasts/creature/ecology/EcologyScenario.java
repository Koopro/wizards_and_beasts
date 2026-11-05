package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.creature.profile.CreatureReaction;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A small language for testing one creature decision at a time, with no level and no entity:
 *
 * <pre>{@code
 * creature(PREY, HERD).sees(predatorAt(6)).decides(FLEE_PREDATOR);
 * creature(NOCTURNAL).inDaylight().with(person(3, sneaking())).decides(REST);
 * creature(GRAZER).feeling(HUNGER, 0.7f).seesForage(6).decides(FORAGE);
 * }</pre>
 *
 * Every scenario starts from {@link Perception#calm()} on a profile with default radii (sense 16, herd 10,
 * territory 16), no needs and a neutral personality, so a test states only what matters to it. {@link #feeling}
 * both declares a need on the profile and sets its pressure.
 */
final class EcologyScenario {

    private final EnumSet<EcologyCapability> capabilities;
    private final Map<EcologyNeed, Integer> needs = new EnumMap<>(EcologyNeed.class);
    private EcologyProfile.HuntStyle huntStyle = EcologyProfile.HuntStyle.CHASE;
    private Perception perception = Perception.calm();

    private EcologyScenario(EnumSet<EcologyCapability> capabilities) {
        this.capabilities = capabilities;
    }

    static EcologyScenario creature(EcologyCapability... capabilities) {
        EnumSet<EcologyCapability> set = EnumSet.noneOf(EcologyCapability.class);
        set.addAll(List.of(capabilities));
        return new EcologyScenario(set);
    }

    EcologyProfile profile() {
        return new EcologyProfile(capabilities, 16, 10, 16, 6000, Map.copyOf(needs), Map.of(), 12.0, huntStyle,
                Optional.empty(), Optional.empty());
    }

    EcologyScenario atNight() { perception = perception.withNight(true); return this; }
    EcologyScenario inDaylight() { perception = perception.withNight(false); return this; }
    EcologyScenario underOpenSky() {
        perception = perception.withExposed(true).withSurroundings(world().withWeather(world().raining(), world().thundering(), true));
        return this;
    }
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
    EcologyScenario reacting(CreatureReaction.Response response, CreatureReaction.Stimulus kind) {
        perception = perception.withStimulus(new Perception.Stimulus(response, Vec3.ZERO, kind));
        return this;
    }

    // ── needs, personality, surroundings ─────────────────────────────────────────────────────────

    /** Declares {@code need} on the profile and sets its pressure. */
    EcologyScenario feeling(EcologyNeed need, float pressure) {
        needs.put(need, need.defaultTicks());
        perception = perception.withNeeds(perception.needs().with(need, pressure));
        return this;
    }
    EcologyScenario stalker() { huntStyle = EcologyProfile.HuntStyle.STALK; return this; }
    EcologyScenario personality(EcologyPersonality value) { perception = perception.withPersonality(value); return this; }
    EcologyScenario seesWater(double distance) { perception = perception.withSurroundings(world().withWater(at(distance))); return this; }
    EcologyScenario seesForage(double distance) { perception = perception.withSurroundings(world().withForage(at(distance))); return this; }
    EcologyScenario seesCarrion(double distance) { perception = perception.withSurroundings(world().withCarrion(at(distance))); return this; }
    EcologyScenario nearestKinAt(double distance) { perception = perception.withSurroundings(world().withNearestKin(at(distance))); return this; }
    EcologyScenario raining() { perception = perception.withSurroundings(world().withWeather(true, false, world().skyAbove())); return this; }
    EcologyScenario thundering() { perception = perception.withSurroundings(world().withWeather(true, true, world().skyAbove())); return this; }
    EcologyScenario underCover() { perception = perception.withExposed(false).withSurroundings(world().withWeather(world().raining(), world().thundering(), false)); return this; }
    EcologyScenario temperature(float value) { perception = perception.withSurroundings(world().withTemperature(value)); return this; }
    EcologyScenario atDayTime(int ticks) { perception = perception.withSurroundings(world().withDayTime(ticks)); return this; }
    EcologyScenario inHazard() { perception = perception.withSurroundings(world().withHazard(true)); return this; }

    private Perception.Surroundings world() {
        return perception.surroundings();
    }

    Perception perception() {
        return perception;
    }

    EcologyBrain.Decision decision() {
        return EcologyBrain.decide(profile(), perception);
    }

    void decides(EcologyIntent expected) {
        assertEquals(expected, decision().intent(), "for " + capabilities + " " + needs + " perceiving " + perception);
    }

    void revealing(EcologyCapability expected) {
        assertEquals(expected, decision().cause(), "cause for " + capabilities);
    }

    boolean sustains(EcologyIntent intent) {
        return EcologyBrain.sustains(profile(), perception, intent);
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

    static EcologyPersonality bold() {
        return new EcologyPersonality(1, 0.5f, 0.5f, 0.5f, 0.5f);
    }

    static EcologyPersonality timid() {
        return new EcologyPersonality(0, 0.5f, 0.5f, 0.5f, 0.5f);
    }

    private static Perception.Sighting at(double distance) {
        return new Perception.Sighting(distance, new Vec3(distance, 0, 0));
    }
}
