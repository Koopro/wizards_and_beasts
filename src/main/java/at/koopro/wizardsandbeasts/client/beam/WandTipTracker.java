package at.koopro.wizardsandbeasts.client.beam;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-caster cache of wand-tip world positions so a beam can start at the tip of the GeckoLib wand
 * model rather than the caster's eye.
 *
 * <p>GeckoLib bone positions only exist during the model's render pass, so they cannot be queried
 * on demand — they are grabbed in passing. GeckoLib 5 exposes exactly the right hook
 * ({@code RenderPassInfo.addBonePositionListener}), which {@code WandRenderer} already uses; the
 * wand renderer feeds {@link #capture} the world position GeckoLib already resolved, so no
 * PoseStack transform is done here.
 *
 * <p><strong>One frame of latency is accepted and intended.</strong> Item and entity rendering run
 * in the same frame with no guaranteed order, so the beam may read the tip captured on the previous
 * frame. At 60+ fps this is invisible. Entries expire after {@link #MAX_AGE_NANOS}; a stale/absent
 * entry (caster off-screen, first-person hand hidden, wand switched away) falls back to a
 * body-rotation approximation so the beam never snaps to the world origin.
 */
public final class WandTipTracker {

    private record Anchor(Vec3 worldPos, long nanos) {}

    private static final Map<Integer, Anchor> ANCHORS = new ConcurrentHashMap<>();

    /** ~150 ms — a few frames of grace, but stale enough to drop when the listener stops firing. */
    private static final long MAX_AGE_NANOS = 150_000_000L;

    private WandTipTracker() {}

    /** Store the wand-tip world position for a caster. Called from the wand's bone listener. */
    public static void capture(int casterId, Vec3 worldTip) {
        ANCHORS.put(casterId, new Anchor(worldTip, System.nanoTime()));
    }

    /** Fresh cached tip if we have one, else a body-rotation fallback (never the world origin). */
    public static Vec3 resolve(LivingEntity caster, float partialTick) {
        Anchor anchor = ANCHORS.get(caster.getId());
        if (anchor != null && System.nanoTime() - anchor.nanos() <= MAX_AGE_NANOS) {
            return anchor.worldPos();
        }
        return fallback(caster, partialTick);
    }

    /** Forget every caster — e.g. on log-out, when the client level is torn down. */
    public static void clear() {
        ANCHORS.clear();
    }

    /** Forget one caster — e.g. when its entity leaves the level. */
    public static void forget(int casterId) {
        ANCHORS.remove(casterId);
    }

    /** Eye height, shoulder-side offset by main arm, a little forward. */
    private static Vec3 fallback(LivingEntity caster, float partialTick) {
        Vec3 eye = caster.getEyePosition(partialTick);
        Vec3 look = caster.getViewVector(partialTick);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-8 ? new Vec3(1, 0, 0) : right.normalize();
        double side = caster.getMainArm() == HumanoidArm.RIGHT ? 0.3 : -0.3;
        return eye.add(look.scale(0.6)).add(right.scale(side)).add(0, -0.1, 0);
    }
}
