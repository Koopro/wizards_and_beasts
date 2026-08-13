package at.koopro.wizardsandbeasts.spike.pal;

import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * SPIKE ONLY -- evidence for AGENT_PROMPT_PAL_SPIKE Q10. Not a feature. Do not merge.
 *
 * <p>Three-way weighted blend of sub-animations, driven by weights a caller sets every frame from
 * live gameplay state rather than from a timeline. Written because {@code AnimationStack} exposes
 * no per-layer weight at all — it chains {@code bone = layer.get3DTransform(bone)} — and the only
 * weight PAL ships is {@code AbstractFadeModifier}, whose alpha is a function of elapsed ticks over
 * a fixed length. This is what a continuous HOVER/GLIDE/PROPELLED blend has to be built out of.
 *
 * <p>The blend itself uses PAL's own bone arithmetic ({@code scale} / {@code add}), the same pair
 * {@code AbstractFadeModifier} uses, so it composes exactly like a built-in fade would.
 */
public final class PalSpikeWeightedBlend implements IAnimation {

    private final List<IAnimation> sources;
    /** Parallel to {@link #sources}. Not normalised for you — the caller owns the sum. */
    public final float[] weights;

    public PalSpikeWeightedBlend(List<IAnimation> sources) {
        this.sources = List.copyOf(sources);
        this.weights = new float[this.sources.size()];
    }

    @Override
    public boolean isActive() {
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] != 0f && sources.get(i).isActive()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick(AnimationData state) {
        for (IAnimation source : sources) {
            source.tick(state);
        }
    }

    @Override
    public void setupAnim(AnimationData state) {
        for (IAnimation source : sources) {
            source.setupAnim(state);
        }
    }

    @Override
    public PlayerAnimBone get3DTransform(@NotNull PlayerAnimBone bone) {
        PlayerAnimBone accumulator = new PlayerAnimBone(bone.getName());
        accumulator.copyOtherBone(bone);
        accumulator.scale(0f);

        for (int i = 0; i < weights.length; i++) {
            float weight = weights[i];
            if (weight == 0f) {
                continue;
            }
            PlayerAnimBone sample = new PlayerAnimBone(bone.getName());
            sample.copyOtherBone(bone);
            sources.get(i).get3DTransform(sample);
            accumulator.add(sample.scale(weight));
        }

        bone.copyOtherBone(accumulator);
        return bone;
    }
}
