package at.koopro.wizardsandbeasts.client.pose;

import org.jspecify.annotations.NullMarked;

/**
 * Pose values for items whose hold or use is a two-handed gesture.
 *
 * <p>Split from {@link ItemUsePosePass} for the same reason {@link CastPoseConstants} is split from
 * {@link CastPosePass}: the pass is the machinery — which item, which arm, when — and the numbers
 * are art. Retuning a pose should be an edit to this file and nothing else.
 *
 * <h2>Mirroring, and why one factor covers both axes</h2>
 *
 * <p>Every pose here is written for the <em>right</em> arm and mirrored onto the left by a single
 * {@code -1} factor applied to {@code yRot} and {@code zRot}. That is vanilla's own convention in
 * both directions:
 *
 * <ul>
 *   <li>{@code yRot} — {@code ArmPose.BOW_AND_ARROW} gives the right arm {@code -0.1 + head.yRot}
 *       and the left {@code +0.1 + head.yRot}, converging both hands on the midline.
 *   <li>{@code zRot} — {@code AnimationUtils.bobArms} bobs the right arm at {@code +1.0} and the
 *       left at {@code -1.0}, so a positive {@code zRot} swings a right hand <em>outward</em>. An
 *       elbow tucked in is therefore negative on the right and positive on the left.
 * </ul>
 *
 * <p>{@code xRot} is not mirrored: negative swings either limb forward, per the frame documented in
 * {@link CastPoseConstants}.
 */
@NullMarked
public final class ItemUsePoseConstants {

    private ItemUsePoseConstants() {}

    /**
     * A symmetric two-handed hold, in degrees.
     *
     * <p>{@code armYRot} and {@code armZRot} are magnitudes for the right arm; the pass applies the
     * mirror. {@code headXRot} and {@code chestXRot} are <em>added</em> to whatever the player is
     * really doing, so the wizard keeps looking where they are looking and merely inclines toward
     * the thing in their hands.
     *
     * @param armXRot   shoulder pitch, negative forward
     * @param armYRot   shoulder yaw toward the midline
     * @param armZRot   elbow tuck
     * @param headXRot  added head pitch — a downward nod at the held object
     * @param chestXRot added torso pitch, small; a head that nods alone reads as a puppet
     */
    public record TwoHandPose(float armXRot, float armYRot, float armZRot,
                              float headXRot, float chestXRot) {}

    /**
     * Reading something held flat in both hands at chest height.
     *
     * <p>Static rather than tracking head pitch, unlike {@link #OPTIC_ARM_PITCH_OFFSET}. Reading is
     * the gesture of bringing the page to your eyes and then looking down at it, so the hands hold
     * still and the head comes to them; an arm pitch slaved to look direction would instead wave the
     * card around the moment the player glanced at the sky.
     */
    public static final TwoHandPose BOOK = new TwoHandPose(-55f, 22f, 12f, 18f, 4f);

    /**
     * A two-handed working pose where one arm strokes and the other steadies, in degrees.
     *
     * <p>Written for a right-handed worker, like {@link TwoHandPose}: {@code yRot}/{@code zRot} are
     * magnitudes and the pass mirrors them. The stroke is what separates this from a hold — a static
     * pose for repetitive work reads as a player frozen mid-gesture, which is worse than no pose,
     * because a hold at least looks intentional.
     *
     * @param workXRot     working shoulder pitch at rest, negative forward
     * @param workZRot     working elbow, angled across the body toward the work
     * @param strokeXRot   how far the working shoulder travels either side of {@link #workXRot}
     * @param strokeTicks  period of one full stroke; matched to the item's own sound and particles
     * @param steadyXRot   the other shoulder, holding the work still
     * @param steadyYRot   the other shoulder, brought in toward the midline
     * @param chestYRot    torso counter-rotation on the same period, so the body drives the arm
     * @param chestXRot    added torso pitch — a worker stoops over what they are working on
     */
    public record WorkPose(float workXRot, float workZRot, float strokeXRot, int strokeTicks,
                           float steadyXRot, float steadyYRot, float chestYRot, float chestXRot) {}

    /**
     * Whittling a wand blank against a log.
     *
     * <p>Ten ticks a stroke, matching {@code WandBlankItem.STROKE_TICKS}, so the knife is fully
     * forward as the chip flies. The two constants are separate on purpose — one is common code and
     * one is client — and {@code ItemUsePosePassTest} asserts they agree.
     */
    public static final WorkPose CARVE =
            new WorkPose(-70f, 18f, 8f, 10, -45f, 20f, 6f, 8f);

    /**
     * Rubbing polish into a broom handle.
     *
     * <p>Shorter, faster strokes than the carve and a deeper stoop: a knife travels, a rag scrubs.
     * Eight ticks a stroke against the carve's ten, and twice the travel.
     */
    public static final WorkPose POLISH =
            new WorkPose(-35f, 12f, 15f, 8, -30f, 15f, 4f, 10f);

    /**
     * Both hands at the chest, one of them turning something small, in degrees.
     *
     * <p>The Time-Turner's gesture, and the reason it needs its own shape rather than a
     * {@link WorkPose}: a stroke travels back and forth, a wind goes round. The turning hand's roll
     * is a continuous rotation rather than an oscillation, which is a different animation entirely
     * and — see {@code ItemUsePosePass#spinDegrees} — a different set of traps.
     *
     * @param armXRot    both shoulders, bringing the hands up to the chest
     * @param armYRot    both shoulders, in toward the midline; magnitude, mirrored per arm
     * @param spinTicks  ticks for one full turn of the chain
     */
    public record WindPose(float armXRot, float armYRot, int spinTicks) {}

    /** Winding the Time-Turner's chain. One turn a second, which is a hand's natural pace. */
    public static final WindPose WIND = new WindPose(-75f, 25f, 20);

    /**
     * An arm held out at something in front of you, in degrees.
     *
     * <p>Asymmetric, unlike {@link TwoHandPose}: one arm does the work and the other hangs. The
     * reaching arm goes past horizontal — {@code -95} rather than {@code -90} — because an arm at
     * exactly horizontal reads as pointing, and this is pressing something onto someone.
     *
     * <p>{@code yRot} and {@code zRot} are magnitudes for the right arm, mirrored by the pass.
     *
     * @param reachXRot  the working shoulder, out and very slightly up
     * @param reachYRot  the working shoulder, in toward the midline so the hand is centred
     * @param reachZRot  the working elbow, lifted away from the body
     * @param idleXRot   the other arm, hanging back out of the way
     * @param chestXRot  added torso pitch — leaning into it
     */
    public record ReachPose(float reachXRot, float reachYRot, float reachZRot,
                            float idleXRot, float chestXRot) {}

    /** Burning the Dark Mark into somebody's forearm. */
    public static final ReachPose BRAND = new ReachPose(-95f, 10f, 8f, -25f, 6f);

    // ── Optic ────────────────────────────────────────────────────────────────
    //
    // Lifted unchanged from HumanoidModel's ArmPose.SPYGLASS case so that the free arm and the arm
    // vanilla poses land in the same place. Radians, because that is the unit vanilla wrote them in
    // and converting to degrees here would only invite a rounding difference between the two arms.

    /** How far below the look direction the shoulder sits. Vanilla's {@code 1.9198622F} — 110°. */
    public static final float OPTIC_ARM_PITCH_OFFSET = 1.9198622f;

    /** Yaw splay toward the midline, per arm. Vanilla's {@code PI/12} — 15°. */
    public static final float OPTIC_ARM_YAW_SPLAY = (float) (Math.PI / 12);

    /** Extra pitch while crouched, so the arms stay level with a lowered head. Vanilla's {@code PI/12}. */
    public static final float OPTIC_CROUCH_PITCH = (float) (Math.PI / 12);

    /** Shoulder pitch clamp, vanilla's {@code -2.4F}. Stops a downward look folding the arm through the chest. */
    public static final float OPTIC_PITCH_MIN = -2.4f;

    /** Shoulder pitch clamp, vanilla's {@code 3.3F}. */
    public static final float OPTIC_PITCH_MAX = 3.3f;
}
