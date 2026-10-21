package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.creature.profile.CreatureReaction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Everything a creature noticed at its last look around, as plain data.
 *
 * <p>{@code EcologySensing} builds one every sense interval from one bounded query; {@link EcologyBrain} decides from
 * it alone. Because a perception is a value, a decision can be tested by writing one down — no level, no entity,
 * no ticking ({@code EcologyBrainTest}). Distances are {@link #FAR} when nothing was seen.
 *
 * @param night            the sky is dark
 * @param exposed          the creature stands under open sky in daylight
 * @param health           current health as a fraction of max
 * @param bonded           it has a bond owner; a kept creature does not keep territory or a herd
 * @param fed              a predator that has eaten recently
 * @param distanceFromHome blocks from its home anchor
 * @param threat           the nearest thing that hunts it or fills it with dread
 * @param prey             the nearest thing it would hunt
 * @param kin              its own kind nearby: count and the distance to their centre
 * @param player           the nearest person it can perceive
 * @param item             the nearest thing on the ground it wants (treasure or food)
 * @param stimulus         a reaction still in force, if any
 */
@NullMarked
public record Perception(boolean night, boolean exposed, float health, boolean bonded, boolean fed,
                         double distanceFromHome, Sighting threat, Sighting prey, Kin kin, PlayerView player,
                         Sighting item, @Nullable Stimulus stimulus) {

    public static final double FAR = Double.MAX_VALUE;

    /** Something seen at a distance and place, or nothing. */
    public record Sighting(double distance, @Nullable Vec3 at) {
        public static final Sighting NONE = new Sighting(FAR, null);

        public boolean within(double radius) {
            return at != null && distance <= radius;
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

    /** A {@link CreatureReaction} answer still in force, and where its cause was. */
    public record Stimulus(CreatureReaction.Response response, Vec3 source) {}

    /** A calm day with nothing around: the baseline tests modify. */
    public static Perception calm() {
        return new Perception(false, false, 1.0f, false, false, 0, Sighting.NONE, Sighting.NONE, Kin.NONE,
                PlayerView.NONE, Sighting.NONE, null);
    }

    public Perception withNight(boolean value) {
        return new Perception(value, exposed, health, bonded, fed, distanceFromHome, threat, prey, kin, player, item, stimulus);
    }

    public Perception withExposed(boolean value) {
        return new Perception(night, value, health, bonded, fed, distanceFromHome, threat, prey, kin, player, item, stimulus);
    }

    public Perception withHealth(float value) {
        return new Perception(night, exposed, value, bonded, fed, distanceFromHome, threat, prey, kin, player, item, stimulus);
    }

    public Perception withBonded(boolean value) {
        return new Perception(night, exposed, health, value, fed, distanceFromHome, threat, prey, kin, player, item, stimulus);
    }

    public Perception withFed(boolean value) {
        return new Perception(night, exposed, health, bonded, value, distanceFromHome, threat, prey, kin, player, item, stimulus);
    }

    public Perception withHome(double value) {
        return new Perception(night, exposed, health, bonded, fed, value, threat, prey, kin, player, item, stimulus);
    }

    public Perception withThreat(Sighting value) {
        return new Perception(night, exposed, health, bonded, fed, distanceFromHome, value, prey, kin, player, item, stimulus);
    }

    public Perception withPrey(Sighting value) {
        return new Perception(night, exposed, health, bonded, fed, distanceFromHome, threat, value, kin, player, item, stimulus);
    }

    public Perception withKin(Kin value) {
        return new Perception(night, exposed, health, bonded, fed, distanceFromHome, threat, prey, value, player, item, stimulus);
    }

    public Perception withPlayer(PlayerView value) {
        return new Perception(night, exposed, health, bonded, fed, distanceFromHome, threat, prey, kin, value, item, stimulus);
    }

    public Perception withItem(Sighting value) {
        return new Perception(night, exposed, health, bonded, fed, distanceFromHome, threat, prey, kin, player, value, stimulus);
    }

    public Perception withStimulus(@Nullable Stimulus value) {
        return new Perception(night, exposed, health, bonded, fed, distanceFromHome, threat, prey, kin, player, item, value);
    }
}
