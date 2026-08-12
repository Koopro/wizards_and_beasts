package at.koopro.wizardsandbeasts.client.pose;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jspecify.annotations.NullMarked;

/**
 * Everything a pass is allowed to read.
 *
 * <p>Deliberately a parameter object rather than a set of statics. The alternative — a
 * {@code CACHED_PARTIAL_TICK} field somewhere — is what the schema forbids outright, and it breaks
 * the moment two players render in the same frame.
 *
 * @param model        the humanoid model being posed
 * @param state        the render state, source of every motion value (see schema §3.6)
 * @param firstPerson  which arm this invocation poses, or {@code NONE} for third person
 * @param partialTicks true frame fraction, sourced once per frame by the layer. <b>Not</b>
 *                     {@code state.ageInTicks}: that is a clock, this is a fraction between two
 *                     ticks, and a {@code PhaseTimer} interpolated against the wrong one stutters.
 */
@NullMarked
public record PoseContext(
        PlayerModel model,
        AvatarRenderState state,
        FirstPersonContext firstPerson,
        float partialTicks) {

    /** Walk cycle position, for passes that want to damp against stride. */
    public float walkPos() {
        return state.walkAnimationPos;
    }

    public float walkSpeed() {
        return state.walkAnimationSpeed;
    }

    /** Head pitch in degrees, the primary input to a flight attitude. */
    public float pitchDeg() {
        return state.xRot;
    }

    /** Head yaw relative to the body, in degrees. */
    public float headYawDeg() {
        return state.yRot - state.bodyRot;
    }

    /** A continuous clock for oscillators. Distinct from {@link #partialTicks()} — see the record doc. */
    public float ageInTicks() {
        return state.ageInTicks;
    }
}
