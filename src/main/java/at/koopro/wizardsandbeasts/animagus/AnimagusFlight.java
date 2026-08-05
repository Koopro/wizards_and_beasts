package at.koopro.wizardsandbeasts.animagus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Physics for glide-and-flap flight. Present only on forms carrying {@link AnimagusCapability#FLIGHT};
 * {@link AnimagusFormDefinition}'s codec rejects the mismatch in either direction.
 * <p>
 * This is not creative flight. A bird carries momentum and must beat its wings, so there is no
 * hover: below {@link #stallSpeed} the player descends whatever the input. There is deliberately no
 * stamina pool and no flap limit — the altitude ceiling is what bounds a climb, by scaling
 * {@link #flapImpulse} to zero rather than by an invisible wall.
 *
 * @param takeoffImpulse       upward velocity applied when leaving the ground
 * @param flapImpulse          upward velocity per wingbeat, scaled down near the ceiling
 * @param flapCooldownTicks    minimum ticks between wingbeats
 * @param maxSpeed             horizontal speed bound
 * @param stallSpeed           horizontal speed below which the form sinks regardless of input
 * @param glideDrag            per-tick horizontal velocity retention while gliding
 * @param flightCeilingOffset  blocks above takeoff Y at which {@code flapImpulse} reaches zero
 */
@NullMarked
public record AnimagusFlight(
        double takeoffImpulse,
        double flapImpulse,
        int flapCooldownTicks,
        double maxSpeed,
        double stallSpeed,
        double glideDrag,
        int flightCeilingOffset) {

    public static final Codec<AnimagusFlight> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("takeoff_impulse").forGetter(AnimagusFlight::takeoffImpulse),
            Codec.DOUBLE.fieldOf("flap_impulse").forGetter(AnimagusFlight::flapImpulse),
            Codec.INT.fieldOf("flap_cooldown_ticks").forGetter(AnimagusFlight::flapCooldownTicks),
            Codec.DOUBLE.fieldOf("max_speed").forGetter(AnimagusFlight::maxSpeed),
            Codec.DOUBLE.fieldOf("stall_speed").forGetter(AnimagusFlight::stallSpeed),
            Codec.DOUBLE.fieldOf("glide_drag").forGetter(AnimagusFlight::glideDrag),
            Codec.INT.fieldOf("flight_ceiling_offset").forGetter(AnimagusFlight::flightCeilingOffset)
    ).apply(instance, AnimagusFlight::new));
}
