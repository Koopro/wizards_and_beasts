package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.client.pose.FlightPoseConstants.PartRotation;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

/**
 * Pose values for {@link GesturePosePass}. The pass is the machinery; the numbers are art and live here.
 *
 * <p>Conventions as {@link CastPoseConstants}: degrees, written for a right-handed player, {@code xRot} negative swings
 * a limb forward. The wand arm's inward turn is negative {@code yRot}; the free arm is written as the LEFT arm of a
 * right-handed player (inward is positive {@code yRot}). The pass mirrors both for a left hand.
 */
@NullMarked
public final class GesturePoseConstants {

    private GesturePoseConstants() {}

    /**
     * One moment of the inspect.
     *
     * @param freeShare 0..1 how much the steadying hand is up
     */
    public record InspectPose(PartRotation wandArm, PartRotation freeArm, float freeShare,
                              float headPitch, float headYaw, float chestPitch) {}

    /** The wand held across before the eyes. */
    static final PartRotation INSPECT_UP = new PartRotation(-80f, -40f, 0f);
    /** The arm on its way down to the ready hold at the end. */
    static final PartRotation INSPECT_DOWN = new PartRotation(-32f, -10f, 0f);
    /** The free hand at the tip. */
    static final PartRotation INSPECT_STEADY = new PartRotation(-64f, 32f, 0f);

    /** Phase ends, as fractions of the gesture: up, turn, down. */
    static final float RAISE_END = 0.2f;
    static final float TURN_END = 0.8f;
    /** The steadying hand drops away across this window. */
    static final float STEADY_DROP_START = 0.5f;
    static final float STEADY_DROP_END = 0.65f;
    /** Arm roll during the turn: one slow turn and back, degrees. */
    static final float TURN_ROLL_DEG = 14f;
    /** The wand's own turn about its length over the gesture (WandRenderer), degrees. */
    static final float WAND_ROLL_DEG = 180f;

    public static InspectPose inspectAt(float progress) {
        float up = ClientCastAnimationState.smoothstep(progress / RAISE_END);
        float turn = Mth.clamp((progress - RAISE_END) / (TURN_END - RAISE_END), 0f, 1f);
        float down = ClientCastAnimationState.smoothstep((progress - TURN_END) / (1f - TURN_END));

        PartRotation held = new PartRotation(
                INSPECT_UP.xRot(),
                INSPECT_UP.yRot() + 14f * Mth.sin(turn * Mth.PI),
                Mth.sin(turn * Mth.TWO_PI) * TURN_ROLL_DEG);
        // Rising from wherever the ready hold put it is the KindFade's job; inside the gesture the arm goes up,
        // turns, and comes down toward the ready hold.
        PartRotation arm = INSPECT_DOWN.lerp(held, up).lerp(INSPECT_DOWN, down);

        float steady = up * (1f - ClientCastAnimationState.smoothstep(
                (progress - STEADY_DROP_START) / (STEADY_DROP_END - STEADY_DROP_START)));
        float look = up * (1f - down);
        return new InspectPose(arm, INSPECT_STEADY, steady,
                16f * look, -8f * look + 10f * Mth.sin(turn * Mth.PI) * look, 4f * look);
    }

    /** The wand's own turn about its length: none while raising, a half turn across the look, back on the way down. */
    public static float inspectRoll(float progress) {
        float turn = Mth.clamp((progress - RAISE_END) / (TURN_END - RAISE_END), 0f, 1f);
        return Mth.sin(turn * Mth.PI) * WAND_ROLL_DEG;
    }
}
