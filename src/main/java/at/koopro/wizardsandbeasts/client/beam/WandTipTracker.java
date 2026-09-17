package at.koopro.wizardsandbeasts.client.beam;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-caster cache of where the wand tip was last drawn, so a beam starts at the tip of the GeckoLib
 * wand model rather than somewhere near the caster.
 *
 * <p>GeckoLib bone positions only exist during the model's render pass, so they are grabbed in
 * passing: {@code WandRenderer} registers a {@code RenderPassInfo.addBonePositionListener} for every
 * wand drawn in a hand — anyone's, keyed by the holder GeckoLib hands the item render. The listener's
 * own {@code worldPos} is useless here: it is null for items (only GeckoLib's entity and block
 * renderers set {@code DataTickets.POSITION}), and where it is set it is the bone relative to the item
 * plus a position, with the whole arm transform missing. {@link #cameraRelative} rebuilds the real
 * point from {@code localPos} instead.
 *
 * <p>A beam's render state is extracted before the wand draws, so a beam always reads the tip captured
 * on the previous frame. Each anchor is therefore stored relative to what it moves with, and
 * re-applied to that thing's current position:
 * <ul>
 *   <li><b>A wand on a body</b> (anyone drawn in third person) — as an offset from the caster. Kept as
 *       a world point, that frame is a visible gap behind a moving caster; a broom covers most of a
 *       block in one frame.</li>
 *   <li><b>The local player's first-person wand</b> — in the level's view space, re-applied to the
 *       current camera, so turning your head does not leave the beam a frame behind. The hand pass
 *       draws with its own FOV and bob, so the point is first moved onto the pixel the level pass
 *       would put it on ({@link FirstPersonProjection}).</li>
 * </ul>
 *
 * <p>Entries expire after {@link #MAX_AGE_NANOS}, and an anchor of the wrong kind for the current
 * camera is ignored — a body anchor is meaningless in first person and the other way round, which
 * matters for the frames right after switching. With no usable anchor (caster off-screen, hand
 * hidden, wand switched away) the beam falls back to a body-rotation approximation, so it never snaps
 * to the world origin.
 */
public final class WandTipTracker {

    /**
     * @param pos         offset from the caster, or a point in the level's view space when
     *                    {@code firstPerson}
     * @param firstPerson whether this came from the first-person hand pass
     */
    private record Anchor(Vec3 pos, boolean firstPerson, long nanos) {}

    private static final Map<Integer, Anchor> ANCHORS = new ConcurrentHashMap<>();

    /** ~150 ms — a few frames of grace, but stale enough to drop when the listener stops firing. */
    private static final long MAX_AGE_NANOS = 150_000_000L;

    /**
     * How far past its own height a caster's wand tip may sit. An arm raised overhead with a wand in
     * it stays well inside this. A copy of the player drawn in a screen (the inventory, a preview
     * viewport) passes every other check but lands tens of blocks away, because a GUI pose is scaled
     * to pixels — booking that would fling the beam's start across the world while the screen is open.
     */
    private static final double REACH_MARGIN = 1.5;

    private WandTipTracker() {}

    /**
     * Store where a caster's wand tip was drawn on its body this frame. Called from the wand's bone
     * listener for third-person hands.
     *
     * @param preRenderPose the pose GeckoLib captured before the wand's own transforms
     *                      ({@code RenderPassInfo#getPreRenderMatrixState})
     * @param localPos      the {@code localPos} the listener received, relative to that pose
     * @param partialTick   the partial tick the caster was drawn at
     */
    public static void capture(LivingEntity caster, Matrix4fc preRenderPose, Vec3 localPos, float partialTick) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        Vec3 offset = camera.add(cameraRelative(preRenderPose, localPos))
                .subtract(caster.getPosition(partialTick));
        if (withinReach(offset, caster.getBbHeight())) {
            ANCHORS.put(caster.getId(), new Anchor(offset, false, System.nanoTime()));
        }
    }

    /**
     * Store where the local player's first-person wand tip was drawn this frame.
     *
     * <p>{@code GameRenderer#renderItemInHand} pre-multiplies the hand's pose stack by the camera
     * rotation and takes it back out in the model-view matrix, so undoing that rotation gives the
     * point in the hand pass's own view space.
     */
    public static void captureFirstPerson(LivingEntity caster, Matrix4fc preRenderPose, Vec3 localPos) {
        Vec3 drawn = cameraRelative(preRenderPose, localPos);
        Vector3f handView = Minecraft.getInstance().gameRenderer.getMainCamera().rotation()
                .conjugate(new Quaternionf())
                .transform(new Vector3f((float) drawn.x, (float) drawn.y, (float) drawn.z));
        Vec3 levelView = FirstPersonProjection.handToLevel(new Vec3(handView.x, handView.y, handView.z));
        ANCHORS.put(caster.getId(), new Anchor(levelView, true, System.nanoTime()));
    }

    /** Fresh cached tip if we have one of the right kind, else a body-rotation fallback. */
    public static Vec3 resolve(LivingEntity caster, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        boolean firstPerson = caster == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        Anchor anchor = ANCHORS.get(caster.getId());
        if (anchor == null || anchor.firstPerson() != firstPerson
                || System.nanoTime() - anchor.nanos() > MAX_AGE_NANOS) {
            return fallback(caster, partialTick);
        }
        if (!firstPerson) {
            return caster.getPosition(partialTick).add(anchor.pos());
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 view = anchor.pos();
        Vector3f world = camera.rotation().transform(new Vector3f((float) view.x, (float) view.y, (float) view.z));
        return camera.position().add(world.x, world.y, world.z);
    }

    /** Forget every caster — e.g. on log-out, when the client level is torn down. */
    public static void clear() {
        ANCHORS.clear();
    }

    /** Forget one caster — e.g. when its entity leaves the level. */
    public static void forget(int casterId) {
        ANCHORS.remove(casterId);
    }

    /**
     * A bone's position in the pose stack it was drawn in, from what a GeckoLib position listener
     * received.
     *
     * <p>GeckoLib computes {@code localPos} as {@code inverse(preRenderPose) · bonePose · origin}
     * ({@code GeoBone#updateBonePositionListeners}), so applying {@code preRenderPose} again recovers
     * {@code bonePose · origin}. {@code LevelRenderer#submitEntities} translates each entity by its
     * position minus the camera's and leaves view rotation to the model-view matrix, so for a body
     * that point is the world position less the camera position.
     */
    static Vec3 cameraRelative(Matrix4fc preRenderPose, Vec3 localPos) {
        Vector3f point = preRenderPose.transformPosition(
                new Vector3f((float) localPos.x, (float) localPos.y, (float) localPos.z));
        return new Vec3(point.x, point.y, point.z);
    }

    /** Whether a tip offset from its caster is somewhere a hand could hold it. */
    static boolean withinReach(Vec3 offset, float casterHeight) {
        double reach = casterHeight + REACH_MARGIN;
        return offset.lengthSqr() <= reach * reach;
    }

    /**
     * Where the wand hand roughly is, for casters with no usable tip — off-screen, the hand hidden,
     * or the first frame of a channel.
     *
     * <p>Anchored at the shoulder rather than the eye. Starting a beam at eye height reads as if it
     * came out of the caster's face; these are the constants the legacy renderer used for exactly
     * this case. They are only ever roughly at the hand, from any point of view, which is why every
     * drawn wand is captured instead.
     */
    private static Vec3 fallback(LivingEntity caster, float partialTick) {
        Vec3 aim = caster.getViewVector(partialTick);
        Vec3 right = aim.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-8 ? new Vec3(1, 0, 0) : right.normalize();
        double side = caster.getMainArm() == HumanoidArm.RIGHT ? 0.32 : -0.32;

        Vec3 base = caster.getPosition(partialTick);
        Vec3 shoulder = new Vec3(base.x, base.y + caster.getBbHeight() * 0.86, base.z)
                .add(right.scale(side));
        return shoulder.add(aim.scale(0.58)).add(0, -0.12, 0);
    }
}
