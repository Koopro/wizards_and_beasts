package at.koopro.wizardsandbeasts.pose;

import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The collision box that goes with each flight attitude.
 *
 * <h2>Why this is not a rotation</h2>
 *
 * <p>The obvious ask — "rotate the hitbox with the model" — is not expressible in Minecraft. An
 * entity's box is an {@code AABB}, which is <em>axis-aligned by definition</em>, and the only thing
 * that describes it is {@link EntityDimensions}: one width, one height, and a <b>square</b>
 * footprint. There is no vanilla or NeoForge hook that gives an entity an oriented box, so a player
 * lying flat cannot have a 0.6 x 0.6 x 1.8 box pointing along their facing. Nothing in the game has
 * one; boats and shulkers included.
 *
 * <p>Vanilla hits the same wall and answers it by <b>shrinking</b> rather than rotating. A player
 * gliding on an elytra is drawn horizontal and collides as {@code Pose.FALL_FLYING}, which is
 * {@code 0.6 x 0.6} — the torso, with the limbs outside the box. Swimming and crawling are the same
 * box for the same reason. That is the closest an axis-aligned box gets to a prone body, and copying
 * it exactly is better than inventing a number: it means a broom at speed fits through everything an
 * elytra fits through, which is the comparison a player will actually make.
 *
 * <p>So {@link #PRONE} is vanilla's elytra box, and only {@link FlightPoseState#PROPELLED} uses it.
 * {@code GLIDE} leans at 35 degrees and {@code HOVER} is upright — neither is lying down, and giving
 * a leaning player a crouch-height box would let them slip into gaps their model plainly does not
 * fit through.
 */
@NullMarked
public final class FlightHitbox {

    /**
     * The player's standing width, which the prone box keeps.
     *
     * <p>Vanilla's prone poses do the same — an {@code EntityDimensions} footprint is square, so
     * there is no "along the body" axis to widen even if it were wanted.
     */
    private static final float WIDTH = 0.6F;

    /**
     * Vanilla's {@code Pose.FALL_FLYING} / {@code Pose.SWIMMING} box, to the digit.
     *
     * <p>Deliberately not tuned. The moment this stops matching vanilla it becomes a number someone
     * has to justify, and there is no measurement that would justify one — the box is already a
     * compromise, and the useful property is that it is the <em>same</em> compromise the rest of the
     * game makes. The height is read from {@link Avatar#SWIMMING_BB_HEIGHT}, the constant vanilla
     * builds {@code Pose.FALL_FLYING} and {@code Pose.SWIMMING} from, so it cannot drift from it.
     */
    public static final EntityDimensions PRONE =
            EntityDimensions.scalable(WIDTH, Avatar.SWIMMING_BB_HEIGHT);

    private FlightHitbox() {}

    /**
     * The box for this flight state, or {@code null} when the state does not change the player's box.
     *
     * @param state the player's current flight attitude, or {@code null} when they are not posed
     */
    @Nullable
    public static EntityDimensions forState(@Nullable FlightPoseState state) {
        return state == FlightPoseState.PROPELLED ? PRONE : null;
    }
}
