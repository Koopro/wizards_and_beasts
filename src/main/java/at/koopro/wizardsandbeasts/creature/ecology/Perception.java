package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.creature.profile.CreatureReaction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Everything a creature noticed at its last look around, as plain data.
 *
 * <p>{@code EcologySensing} builds one every sense interval from one bounded query, a few reads at the creature's
 * own position, and block finds cached from the SLOW tier; {@link EcologyBrain} decides from it alone. Because a
 * perception is a value, a decision can be tested by writing one down — no level, no entity, no ticking
 * ({@code EcologyBrainTest}). Distances are {@link #FAR} when nothing was seen.
 *
 * @param night            the sky is dark
 * @param exposed          the creature stands under open sky in daylight
 * @param health           current health as a fraction of max
 * @param bonded           it has a bond owner; a kept creature does not keep territory or a herd
 * @param fed              a predator that has eaten recently (for one without a hunger need)
 * @param distanceFromHome blocks from its home anchor
 * @param threat           the nearest thing that hunts it or fills it with dread
 * @param prey             the nearest thing it would hunt
 * @param kin              its own kind nearby: count and the distance to their centre
 * @param player           the nearest person it can perceive
 * @param item             the nearest thing on the ground it wants (treasure or food)
 * @param stimulus         a reaction still in force, if any
 * @param surroundings     weather, time, hazards, and the water, food and remains it knows of
 * @param needs            its needs at this look
 * @param personality      its individual temperament
 */
@NullMarked
public record Perception(boolean night, boolean exposed, float health, boolean bonded, boolean fed,
                         double distanceFromHome, Sighting threat, Sighting prey, Kin kin, PlayerView player,
                         Sighting item, @Nullable Stimulus stimulus, Surroundings surroundings,
                         EcologyNeeds.View needs, EcologyPersonality personality) {

    public static final double FAR = Double.MAX_VALUE;

    /** Something seen at a distance and place, or nothing. */
    public record Sighting(double distance, @Nullable Vec3 at) {
        public static final Sighting NONE = new Sighting(FAR, null);

        public boolean within(double radius) {
            return at != null && distance <= radius;
        }

        public boolean seen() {
            return at != null;
        }
    }

    /** Its own kind in view. */
    public record Kin(int count, double distanceToCentre, @Nullable Vec3 centre) {
        public static final Kin NONE = new Kin(0, FAR, null);
    }

    /**
     * The nearest person.
     *
     * @param trusted  lets this person near (watched them long enough, sneaking, empty-handed — {@code WildlifeRules})
     * @param shunned  dark-corrupted or a killer of its kind
     * @param sneaking moving quietly
     */
    public record PlayerView(double distance, @Nullable Vec3 at, boolean trusted, boolean shunned, boolean sneaking) {
        public static final PlayerView NONE = new PlayerView(FAR, null, false, false, false);

        public boolean within(double radius) {
            return at != null && distance <= radius;
        }
    }

    /**
     * A {@link CreatureReaction} answer still in force, where its cause was, and what kind of thing it was
     * ({@code null}: unknown, read as a spell, the first stimulus there was).
     */
    public record Stimulus(CreatureReaction.Response response, Vec3 source, CreatureReaction.@Nullable Stimulus kind) {
        public Stimulus(CreatureReaction.Response response, Vec3 source) {
            this(response, source, null);
        }
    }

    /**
     * The world around the creature, beyond who is in it.
     *
     * @param raining     rain (or snow) falls where it stands
     * @param thundering  a thunderstorm
     * @param skyAbove    nothing between it and the sky, day or night
     * @param temperature the biome's base temperature where it stands (vanilla scale: snow below 0.15, desert 2)
     * @param dayTime     time of day, 0–23999 (0 dawn, 6000 noon, 18000 midnight)
     * @param hazard      standing in fire, lava, powder snow, or burning — and not made for it
     * @param water       a place to drink it knows of: where to stand
     * @param forage      a block it can eat it knows of: where to stand
     * @param carrion     where something died lately, for a scavenger
     * @param nearestKin  the nearest of its own kind
     */
    public record Surroundings(boolean raining, boolean thundering, boolean skyAbove, float temperature, int dayTime,
                               boolean hazard, Sighting water, Sighting forage, Sighting carrion, Sighting nearestKin) {
        /** A mild, dry morning in a temperate place with nothing known: the baseline tests modify. */
        public static final Surroundings NONE = new Surroundings(false, false, false, 0.8f, 1000, false,
                Sighting.NONE, Sighting.NONE, Sighting.NONE, Sighting.NONE);

        public Surroundings withWeather(boolean rain, boolean thunder, boolean sky) {
            return new Surroundings(rain, thunder, sky, temperature, dayTime, hazard, water, forage, carrion, nearestKin);
        }

        public Surroundings withTemperature(float value) {
            return new Surroundings(raining, thundering, skyAbove, value, dayTime, hazard, water, forage, carrion, nearestKin);
        }

        public Surroundings withDayTime(int value) {
            return new Surroundings(raining, thundering, skyAbove, temperature, value, hazard, water, forage, carrion, nearestKin);
        }

        public Surroundings withHazard(boolean value) {
            return new Surroundings(raining, thundering, skyAbove, temperature, dayTime, value, water, forage, carrion, nearestKin);
        }

        public Surroundings withWater(Sighting value) {
            return new Surroundings(raining, thundering, skyAbove, temperature, dayTime, hazard, value, forage, carrion, nearestKin);
        }

        public Surroundings withForage(Sighting value) {
            return new Surroundings(raining, thundering, skyAbove, temperature, dayTime, hazard, water, value, carrion, nearestKin);
        }

        public Surroundings withCarrion(Sighting value) {
            return new Surroundings(raining, thundering, skyAbove, temperature, dayTime, hazard, water, forage, value, nearestKin);
        }

        public Surroundings withNearestKin(Sighting value) {
            return new Surroundings(raining, thundering, skyAbove, temperature, dayTime, hazard, water, forage, carrion, value);
        }
    }

    /** A calm day with nothing around: the baseline tests modify. */
    public static Perception calm() {
        return new Perception(false, false, 1.0f, false, false, 0, Sighting.NONE, Sighting.NONE, Kin.NONE,
                PlayerView.NONE, Sighting.NONE, null, Surroundings.NONE, EcologyNeeds.View.NONE,
                EcologyPersonality.NEUTRAL);
    }

    public Perception withNight(boolean value) {
        return new Perception(value, exposed, health, bonded, fed, distanceFromHome, threat, prey, kin, player, item, stimulus, surroundings, needs, personality);
    }

    public Perception withExposed(boolean value) {
        return new Perception(night, value, health, bonded, fed, distanceFromHome, threat, prey, kin, player, item, stimulus, surroundings, needs, personality);
    }

    public Perception withHealth(float value) {
        return new Perception(night, exposed, value, bonded, fed, distanceFromHome, threat, prey, kin, player, item, stimulus, surroundings, needs, personality);
    }

    public Perception withBonded(boolean value) {
        return new Perception(night, exposed, health, value, fed, distanceFromHome, threat, prey, kin, player, item, stimulus, surroundings, needs, personality);
    }

    public Perception withFed(boolean value) {
        return new Perception(night, exposed, health, bonded, value, distanceFromHome, threat, prey, kin, player, item, stimulus, surroundings, needs, personality);
    }

    public Perception withHome(double value) {
        return new Perception(night, exposed, health, bonded, fed, value, threat, prey, kin, player, item, stimulus, surroundings, needs, personality);
    }

    public Perception withThreat(Sighting value) {
        return new Perception(night, exposed, health, bonded, fed, distanceFromHome, value, prey, kin, player, item, stimulus, surroundings, needs, personality);
    }

    public Perception withPrey(Sighting value) {
        return new Perception(night, exposed, health, bonded, fed, distanceFromHome, threat, value, kin, player, item, stimulus, surroundings, needs, personality);
    }

    public Perception withKin(Kin value) {
        return new Perception(night, exposed, health, bonded, fed, distanceFromHome, threat, prey, value, player, item, stimulus, surroundings, needs, personality);
    }

    public Perception withPlayer(PlayerView value) {
        return new Perception(night, exposed, health, bonded, fed, distanceFromHome, threat, prey, kin, value, item, stimulus, surroundings, needs, personality);
    }

    public Perception withItem(Sighting value) {
        return new Perception(night, exposed, health, bonded, fed, distanceFromHome, threat, prey, kin, player, value, stimulus, surroundings, needs, personality);
    }

    public Perception withStimulus(@Nullable Stimulus value) {
        return new Perception(night, exposed, health, bonded, fed, distanceFromHome, threat, prey, kin, player, item, value, surroundings, needs, personality);
    }

    public Perception withSurroundings(Surroundings value) {
        return new Perception(night, exposed, health, bonded, fed, distanceFromHome, threat, prey, kin, player, item, stimulus, value, needs, personality);
    }

    public Perception withNeeds(EcologyNeeds.View value) {
        return new Perception(night, exposed, health, bonded, fed, distanceFromHome, threat, prey, kin, player, item, stimulus, surroundings, value, personality);
    }

    public Perception withPersonality(EcologyPersonality value) {
        return new Perception(night, exposed, health, bonded, fed, distanceFromHome, threat, prey, kin, player, item, stimulus, surroundings, needs, value);
    }
}
