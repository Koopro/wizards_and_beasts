package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionPresentationState;
import org.jspecify.annotations.NullMarked;

/**
 * The twirl: a wizard screwing themselves out of the world.
 *
 * <p>Apparition had no body language at all. The only thing drawn during a charge was the destination ring,
 * which sits on the ground somewhere else entirely and reads as a targeting reticle — so from the outside a
 * wizard about to Apparate was a wizard standing still, and the wind-up the whole risk model is built around
 * was invisible to everyone including the person doing it.
 *
 * <p>Whole-avatar, not limbs. A spin assembled from six independently rotated parts is six limbs orbiting
 * their own pivots, which is why {@link PlayerModelPart#BODY} exists.
 *
 * <h2>Why the angle is a curve and not a speed</h2>
 *
 * <p>The pass keeps no timer. Given the server's own {@code elapsed} it computes the total angle turned so
 * far as a closed-form function of it, which means the spin is a pure function of state that arrived on the
 * wire — it cannot drift from the charge it is drawing, and two clients watching the same wizard see the same
 * rotation. Accumulating a local speed per frame would reintroduce exactly the drift the presentation packets
 * were built to remove.
 *
 * <p>The curve is quadratic in progress, so the twirl starts imperceptibly and is at full rate by the moment
 * the window opens. Past that it continues at the rate it reached, because a held Deliberation is a wizard
 * holding themselves together, not a wizard slowing down.
 */
@NullMarked
public final class ApparitionPosePass extends ProceduralPosePass {

    /**
     * Transform band, above {@code HeritageProportionPass}. A whole-body spin belongs with the transforms
     * rather than with locomotion: it is not something a walking player also does.
     */
    public static final int PRIORITY = 320;

    /** Turns completed by the time the Deliberation window opens. */
    private static final float WINDUP_TURNS = 1.0f;
    private static final float DEGREES_PER_TURN = 360.0f;

    /**
     * How much shorter a wizard is at the moment of vanishing, as a fraction.
     *
     * <p>Ten percent, with half of it going back into width, so the silhouette compresses rather than
     * shrinking. Small on purpose: the squash exists to sell the spin as something happening <i>to</i> the
     * body, and anything larger reads as the player model breaking.
     */
    private static final float SQUASH_MAX = 0.10f;

    public ApparitionPosePass() {
        super("apparition", PRIORITY);
    }

    @Override
    public void pose(PoseBuilder builder, PoseContext context) {
        ClientApparitionPresentationState.Charge charge =
                ClientApparitionPresentationState.charge(context.state().id);
        if (charge == null) {
            return;
        }
        // No viable spot means the Determination clock has not started -- the wizard is still looking for
        // somewhere to go, and looking about costs nothing and shows nothing.
        if (charge.destination() == null || charge.elapsed() <= 0) {
            return;
        }

        float elapsed = charge.elapsed() + context.partialTicks();
        float degrees = spinDegrees(elapsed, charge.windowOpen());
        float squash = squash(elapsed, charge.windowOpen());

        // Added rather than set: a wizard Apparating off a broom keeps the flight attitude and spins on top
        // of it, the same way a cast rides over a glide. The scale ops multiply for the same reason -- a
        // heritage's proportions are somebody's body, not something this pass gets to overwrite.
        builder.get(PlayerModelPart.BODY)
                .addRotDeg(PoseTarget.Y_ROT, degrees)
                .add(PoseTarget.Y_SCALE, -squash)
                .add(PoseTarget.X_SCALE, squash * 0.5f)
                .add(PoseTarget.Z_SCALE, squash * 0.5f);
    }

    /**
     * How compressed the body is {@code elapsed} ticks in, ramping to {@link #SQUASH_MAX} as the window
     * opens and holding there.
     *
     * <p>Linear where the spin is quadratic. Both peak at the same moment, but a squash that accelerated the
     * same way would arrive all at once in the last few ticks and read as a stutter rather than a wind-up.
     */
    static float squash(float elapsed, int windowOpen) {
        if (elapsed <= 0.0f) {
            return 0.0f;
        }
        if (windowOpen <= 0) {
            return SQUASH_MAX;
        }
        return SQUASH_MAX * Math.min(1.0f, elapsed / windowOpen);
    }

    /**
     * Total degrees turned {@code elapsed} ticks into a charge whose window opens at {@code windowOpen}.
     *
     * <p>Package-visible and free of every Minecraft type on purpose, so the curve itself can be tested
     * without a client.
     */
    static float spinDegrees(float elapsed, int windowOpen) {
        if (elapsed <= 0.0f) {
            return 0.0f;
        }
        float full = WINDUP_TURNS * DEGREES_PER_TURN;
        if (windowOpen <= 0) {
            return full;
        }
        float progress = elapsed / windowOpen;
        if (progress <= 1.0f) {
            return full * progress * progress;
        }
        // The derivative of the curve above at the moment the window opens, so the rate is continuous
        // across the transition and the spin does not visibly kick.
        float rateAtOpen = 2.0f * full / windowOpen;
        return full + rateAtOpen * (elapsed - windowOpen);
    }
}
