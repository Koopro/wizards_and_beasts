package at.koopro.wizardsandbeasts.creature.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

/**
 * Something a creature notices, and what it does about it.
 *
 * <p>The audit found no path by which any creature responds to a spell being cast, to fire, to light,
 * or to another creature dying beside it. Damage retaliation and {@code FEARFUL} panic exist; nothing
 * else does. A Niffler has no opinion about Lumos and a herd does not scatter when one of them falls.
 *
 * <p>This is the framework for that, and deliberately only the framework. A reaction is a
 * {@link Stimulus} paired with a {@link Response} and a radius, declared per creature. Nothing here
 * assumes a creature reacts to everything, or to anything: a creature with no reactions declared
 * behaves exactly as it did.
 *
 * <p>Kept separate from {@code CreatureAbility} on purpose. An ability is something a creature
 * <em>does</em> — it has its own dispatch codec, its own tick and hurt hooks, and thirty-three
 * creatures use {@code enrage} alone. A reaction is something a creature <em>notices</em>. Folding
 * reactions into abilities would have meant every stimulus growing an ability type, and every ability
 * learning about stimuli it does not care about.
 *
 * @param stimulus what happened
 * @param response what this creature does about it
 * @param radius   how far away it can notice, in blocks
 */
@NullMarked
public record CreatureReaction(Stimulus stimulus, Response response, double radius) {

    public static final double DEFAULT_RADIUS = 12.0;

    public static final Codec<CreatureReaction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Stimulus.CODEC.fieldOf("stimulus").forGetter(CreatureReaction::stimulus),
            Response.CODEC.fieldOf("response").forGetter(CreatureReaction::response),
            Codec.DOUBLE.optionalFieldOf("radius", DEFAULT_RADIUS).forGetter(CreatureReaction::radius)
    ).apply(instance, CreatureReaction::new));

    public CreatureReaction {
        radius = Math.max(1.0, Math.min(64.0, radius));
    }

    /** What a creature can notice. Extend only when something can actually raise the event. */
    public enum Stimulus implements StringRepresentable {
        /** A spell was cast nearby. Raised from the cast pipeline. */
        SPELL("spell"),
        /** Fire or lava appeared, or something nearby caught light. */
        FIRE("fire"),
        /** A bright light source arrived — a Lumos field, a torch placed. */
        LIGHT("light"),
        /** The light level fell away. */
        DARKNESS("darkness"),
        /** A player came within the radius. */
        PLAYER("player"),
        /** Another creature died within the radius. */
        CREATURE_DEATH("creature_death"),
        /** This creature took damage. Distinct from the vanilla retaliation target goal. */
        DAMAGE("damage"),
        /** A broader world change: weather, a block placed, a structure disturbed. */
        ENVIRONMENT("environment");

        public static final Codec<Stimulus> CODEC = StringRepresentable.fromEnum(Stimulus::values);

        private final String name;

        Stimulus(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** What a creature does about it. */
    public enum Response implements StringRepresentable {
        /** Explicitly nothing. Worth declaring: it records that the author considered it. */
        IGNORE("ignore"),
        /** Run from the source. */
        FLEE("flee"),
        /** Target the source. */
        ATTACK("attack"),
        /** Move towards it without hostility. */
        INVESTIGATE("investigate"),
        /** Freeze briefly. */
        STUN("stun"),
        /** Raise awareness without moving — the creature is now watching. */
        BECOME_ALERT("become_alert"),
        /** Hand off to the creature's own abilities, which decide. */
        SPECIAL("special");

        public static final Codec<Response> CODEC = StringRepresentable.fromEnum(Response::values);

        private final String name;

        Response(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
