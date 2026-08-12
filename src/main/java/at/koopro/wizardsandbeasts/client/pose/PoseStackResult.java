package at.koopro.wizardsandbeasts.client.pose;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Map;

/**
 * Accumulated ops destined for a {@link PoseStack} rather than a {@code ModelPart}.
 *
 * <p>Two things need this backend: the virtual {@link PlayerModelPart#BODY}, which has no model part
 * to write to, and the first-person arm, which is drawn straight onto a PoseStack by
 * {@code ItemInHandRenderer} with no humanoid model involved at all.
 *
 * <p>It is also the only backend that can honour the post-rotation translation group, because a
 * PoseStack can be told to translate, rotate, then translate again, and a {@code ModelPart} has
 * exactly one of each in a fixed order.
 */
@NullMarked
public final class PoseStackResult {

    private final Map<PoseTarget, Float> values = new EnumMap<>(PoseTarget.class);

    public PoseStackResult() {
        for (PoseTarget target : PoseTarget.values()) {
            values.put(target, target.identity());
        }
    }

    public float get(PoseTarget target) {
        return values.getOrDefault(target, target.identity());
    }

    public void set(PoseTarget target, float value) {
        values.put(target, value);
    }

    /** True when nothing here would change the stack, so the caller can skip the push/pop entirely. */
    public boolean isIdentity() {
        for (Map.Entry<PoseTarget, Float> entry : values.entrySet()) {
            if (entry.getValue() != entry.getKey().identity()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Applies the accumulated transform in the order the two translation groups imply:
     * translate, rotate, translate again, scale.
     *
     * <p>The caller owns the push and pop. Doing it here would hide a stack imbalance inside a
     * method that looks like it only does arithmetic.
     */
    public void applyTo(PoseStack stack) {
        stack.translate(get(PoseTarget.X), get(PoseTarget.Y), get(PoseTarget.Z));

        // Z, Y, X — the order a ModelPart uses, so a pose authored against a limb reads the same
        // way when it is applied to the whole avatar instead.
        float zRot = get(PoseTarget.Z_ROT);
        float yRot = get(PoseTarget.Y_ROT);
        float xRot = get(PoseTarget.X_ROT);
        if (zRot != 0f) stack.mulPose(com.mojang.math.Axis.ZP.rotation(zRot));
        if (yRot != 0f) stack.mulPose(com.mojang.math.Axis.YP.rotation(yRot));
        if (xRot != 0f) stack.mulPose(com.mojang.math.Axis.XP.rotation(xRot));

        stack.translate(get(PoseTarget.X2), get(PoseTarget.Y2), get(PoseTarget.Z2));

        float xs = get(PoseTarget.X_SCALE);
        float ys = get(PoseTarget.Y_SCALE);
        float zs = get(PoseTarget.Z_SCALE);
        if (xs != 1f || ys != 1f || zs != 1f) {
            stack.scale(xs, ys, zs);
        }
    }
}
