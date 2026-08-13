package at.koopro.wizardsandbeasts.spike.pal;

import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import org.jetbrains.annotations.NotNull;

/**
 * SPIKE ONLY -- evidence for AGENT_PROMPT_PAL_SPIKE Q9 and Q5. Not a feature. Do not merge.
 *
 * <p>A purely procedural {@link IAnimation}: no clip, no keyframes. Every value is computed from
 * fields a caller sets from live gameplay state, which is what {@code FlightPosePass} would have to
 * become under PAL. Exists to establish whether a third-party mod can put arbitrary Java into PAL's
 * layer stack, or whether the stack is clip-only.
 *
 * <p>Bone names are PAL's, taken from its own {@code PlayerModelMixin} and
 * {@code LivingEntityRendererMixin}: {@code head}, {@code torso}, {@code right_arm},
 * {@code left_arm}, {@code right_leg}, {@code left_leg} address {@code ModelPart}s, and
 * {@code body} is the whole-avatar pose-stack transform.
 */
public final class PalSpikeProceduralAnimation implements IAnimation {

    /** Whole-avatar pitch in radians, PAL's {@code body} bone. The §3.9 gap. */
    public float bodyPitch = 0f;
    /** Arm pitch in radians, applied to both arms. */
    public float armPitch = 0f;
    /** Last {@link AnimationData} handed to {@link #setupAnim}, so the test can prove it arrives. */
    public AnimationData lastSetupData = null;
    public int tickCount = 0;
    public boolean active = true;

    @Override
    public boolean isActive() {
        return this.active;
    }

    @Override
    public void tick(AnimationData state) {
        this.tickCount++;
    }

    @Override
    public void setupAnim(AnimationData state) {
        this.lastSetupData = state;
    }

    @Override
    public PlayerAnimBone get3DTransform(@NotNull PlayerAnimBone bone) {
        switch (bone.getName()) {
            case "body" -> bone.setRotX(this.bodyPitch);
            case "right_arm", "left_arm" -> bone.setRotX(this.armPitch);
            default -> { /* untouched: whatever seeded the bone survives */ }
        }
        return bone;
    }
}
