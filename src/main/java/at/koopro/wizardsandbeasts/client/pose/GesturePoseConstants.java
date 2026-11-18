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

    // ── keyframed gestures ───────────────────────────────────────────────────

    /**
     * One key of a gesture clip.
     *
     * @param t          0..1 position in the gesture
     * @param main       the hand that acts (the main arm), right-handed
     * @param mainShare  0..1 how much of the main arm the gesture takes
     * @param free       the other arm, written as the LEFT arm of a right-handed player
     * @param freeShare  0..1 how much of the free arm the gesture takes
     * @param head       head pitch added, degrees (positive looks down)
     * @param lean       forward lean of the whole upper body, radians, laid out the way vanilla's crouch is
     */
    public record Key(float t, PartRotation main, float mainShare, PartRotation free, float freeShare,
                      float head, float lean) {}

    /**
     * A gesture: keys eased into one another, plus an optional circling of the main hand (a stir, a stroke) and an
     * optional tremor (a body fighting a change).
     */
    public record Clip(Key[] keys, float circleDeg, float circleTurns, float tremorDeg) {

        public Key at(float progress) {
            if (progress <= keys[0].t()) {
                return keys[0];
            }
            for (int i = 1; i < keys.length; i++) {
                Key b = keys[i];
                if (progress <= b.t()) {
                    Key a = keys[i - 1];
                    float s = ClientCastAnimationState.smoothstep((progress - a.t()) / (b.t() - a.t()));
                    return new Key(progress,
                            a.main().lerp(b.main(), s), Mth.lerp(s, a.mainShare(), b.mainShare()),
                            a.free().lerp(b.free(), s), Mth.lerp(s, a.freeShare(), b.freeShare()),
                            Mth.lerp(s, a.head(), b.head()), Mth.lerp(s, a.lean(), b.lean()));
                }
            }
            return keys[keys.length - 1];
        }
    }

    private static PartRotation r(float x, float y, float z) {
        return new PartRotation(x, y, z);
    }

    private static Key k(float t, PartRotation main, float mainShare, PartRotation free, float freeShare,
                         float head, float lean) {
        return new Key(t, main, mainShare, free, freeShare, head, lean);
    }

    private static final PartRotation DOWN = PartRotation.NONE;

    /** Book brought to the chest in both hands, then opened. */
    static final Clip OPEN_BOOK = new Clip(new Key[]{
            k(0f, r(-30f, -10f, 0f), 1f, r(-30f, 10f, 0f), 1f, 4f, 0f),
            k(0.35f, r(-62f, -30f, 0f), 1f, r(-62f, 30f, 0f), 1f, 10f, 0f),
            k(0.6f, r(-56f, -12f, 0f), 1f, r(-56f, 12f, 0f), 1f, 16f, 0.04f),
            k(1f, r(-56f, -12f, 0f), 1f, r(-56f, 12f, 0f), 1f, 16f, 0.04f)}, 0f, 0f, 0f);

    /** Folded sheet at the chest, then spread wide. */
    static final Clip UNFOLD = new Clip(new Key[]{
            k(0f, r(-55f, -28f, 0f), 1f, r(-55f, 28f, 0f), 1f, 8f, 0f),
            k(0.55f, r(-68f, 18f, 0f), 1f, r(-68f, -18f, 0f), 1f, 18f, 0.05f),
            k(1f, r(-66f, 16f, 0f), 1f, r(-66f, -16f, 0f), 1f, 18f, 0.05f)}, 0f, 0f, 0f);

    /** A small thing raised to the face and held there. */
    static final Clip HOLD_UP = new Clip(new Key[]{
            k(0f, r(-40f, -10f, 0f), 1f, DOWN, 0f, 0f, 0f),
            k(0.25f, r(-98f, -32f, 0f), 1f, DOWN, 0f, 2f, 0f),
            k(1f, r(-96f, -30f, 0f), 1f, DOWN, 0f, 2f, 0f)}, 0f, 0f, 0f);

    /** Raised overhead, the click, held a moment. */
    static final Clip CLICK_UP = new Clip(new Key[]{
            k(0f, r(-40f, -5f, 0f), 1f, DOWN, 0f, 0f, 0f),
            k(0.35f, r(-165f, -6f, 0f), 1f, DOWN, 0f, -18f, 0f),
            k(0.5f, r(-172f, -6f, 6f), 1f, DOWN, 0f, -20f, 0f),
            k(1f, r(-160f, -6f, 0f), 1f, DOWN, 0f, -16f, 0f)}, 0f, 0f, 0f);

    /** Leaning in over something to look into it. */
    static final Clip PEER = new Clip(new Key[]{
            k(0f, r(-20f, -6f, 0f), 0.6f, r(-20f, 6f, 0f), 0.6f, 6f, 0f),
            k(0.3f, r(-38f, -12f, 0f), 0.8f, r(-38f, 12f, 0f), 0.8f, 20f, 0.32f),
            k(1f, r(-38f, -12f, 0f), 0.8f, r(-38f, 12f, 0f), 0.8f, 22f, 0.34f)}, 0f, 0f, 0f);

    /** Blade raised in both hands and driven down. */
    static final Clip STAB = new Clip(new Key[]{
            k(0f, r(-110f, -22f, 0f), 1f, r(-100f, 22f, 0f), 1f, 0f, 0f),
            k(0.35f, r(-168f, -12f, 0f), 1f, r(-158f, 12f, 0f), 1f, -10f, -0.05f),
            k(0.6f, r(-42f, -16f, 0f), 1f, r(-48f, 16f, 0f), 1f, 18f, 0.28f),
            k(1f, r(-42f, -16f, 0f), 1f, r(-48f, 16f, 0f), 1f, 18f, 0.24f)}, 0f, 0f, 0f);

    /** Over the cauldron, the hand going round twice. */
    static final Clip STIR = new Clip(new Key[]{
            k(0f, r(-52f, -14f, 0f), 1f, r(-18f, 4f, 0f), 0.5f, 14f, 0.16f),
            k(1f, r(-52f, -14f, 0f), 1f, r(-18f, 4f, 0f), 0.5f, 14f, 0.16f)}, 16f, 2f, 0f);

    /** A pinch lifted and dropped in. */
    static final Clip TOSS = new Clip(new Key[]{
            k(0f, r(-40f, -10f, 0f), 1f, DOWN, 0f, 8f, 0f),
            k(0.4f, r(-78f, -10f, 0f), 1f, DOWN, 0f, 10f, 0.05f),
            k(0.7f, r(-50f, -20f, 0f), 1f, DOWN, 0f, 14f, 0.1f),
            k(1f, r(-45f, -15f, 0f), 1f, DOWN, 0f, 12f, 0.08f)}, 0f, 0f, 0f);

    /** A hand put out to the thing. */
    static final Clip REACH = new Clip(new Key[]{
            k(0f, r(-30f, -6f, 0f), 1f, DOWN, 0f, 0f, 0f),
            k(0.5f, r(-82f, -8f, 0f), 1f, DOWN, 0f, 4f, 0.06f),
            k(1f, r(-60f, -8f, 0f), 1f, DOWN, 0f, 2f, 0.03f)}, 0f, 0f, 0f);

    /** The powder drawn back, thrown down into the hearth, then the arms tucked in for the whirl. */
    static final Clip FLOO_THROW = new Clip(new Key[]{
            k(0f, r(-40f, -8f, 0f), 1f, DOWN, 0f, 0f, 0f),
            k(0.2f, r(-150f, -10f, 10f), 1f, DOWN, 0f, -6f, -0.04f),
            k(0.32f, r(-58f, -10f, 0f), 1f, DOWN, 0f, 10f, 0.1f),
            k(0.48f, r(-12f, 0f, 22f), 1f, r(-12f, 0f, -22f), 1f, -10f, 0f),
            k(1f, r(-8f, 0f, 14f), 1f, r(-8f, 0f, -14f), 1f, -12f, 0f)}, 0f, 0f, 0f);

    /** Grip low, then the yank. */
    static final Clip PULL = new Clip(new Key[]{
            k(0f, r(-34f, -16f, 0f), 1f, r(-34f, 16f, 0f), 1f, 18f, 0.5f),
            k(0.4f, r(-30f, -16f, 0f), 1f, r(-30f, 16f, 0f), 1f, 20f, 0.55f),
            k(0.7f, r(-125f, -14f, 0f), 1f, r(-125f, 14f, 0f), 1f, -10f, -0.05f),
            k(1f, r(-95f, -12f, 0f), 1f, r(-95f, 12f, 0f), 1f, -4f, 0f)}, 0f, 0f, 0f);

    /** A hand stroking a coat. */
    static final Clip STROKE = new Clip(new Key[]{
            k(0f, r(-48f, -6f, 0f), 1f, DOWN, 0f, 14f, 0.18f),
            k(1f, r(-48f, -6f, 0f), 1f, DOWN, 0f, 14f, 0.18f)}, 10f, 3f, 0f);

    /** Down to the ground and back up. */
    static final Clip STOOP = new Clip(new Key[]{
            k(0f, r(-20f, -4f, 0f), 1f, DOWN, 0f, 4f, 0f),
            k(0.45f, r(-42f, -6f, 0f), 1f, DOWN, 0f, 22f, 0.55f),
            k(1f, r(-20f, -4f, 0f), 1f, DOWN, 0f, 4f, 0f)}, 0f, 0f, 0f);

    /** Doubling over, arms clutched to the chest, shaking harder. */
    static final Clip HUNCH = new Clip(new Key[]{
            k(0f, r(-30f, -10f, 0f), 1f, r(-30f, 10f, 0f), 1f, 6f, 0.05f),
            k(0.3f, r(-72f, -38f, 0f), 1f, r(-72f, 38f, 0f), 1f, 18f, 0.45f),
            k(1f, r(-78f, -40f, 0f), 1f, r(-78f, 40f, 0f), 1f, 24f, 0.55f)}, 0f, 0f, 3.5f);

    /** The clip a keyframed gesture plays; null for one with its own code (the inspect). */
    public static @org.jspecify.annotations.Nullable Clip clip(at.koopro.wizardsandbeasts.pose.PlayerGesture gesture) {
        return switch (gesture) {
            case WAND_INSPECT -> null;
            case OPEN_BOOK -> OPEN_BOOK;
            case UNFOLD -> UNFOLD;
            case HOLD_UP -> HOLD_UP;
            case CLICK_UP -> CLICK_UP;
            case PEER -> PEER;
            case STAB -> STAB;
            case STIR -> STIR;
            case TOSS -> TOSS;
            case REACH -> REACH;
            case FLOO_THROW -> FLOO_THROW;
            case PULL -> PULL;
            case STROKE -> STROKE;
            case STOOP -> STOOP;
            case HUNCH -> HUNCH;
        };
    }

    /** Vanilla's crouch offsets per radian of lean (its 0.5 rad crouch: head 4.2 down, torso and arms 3.2, legs 4 back). */
    public static final float LEAN_HEAD_DROP = 4.2f / 0.5f;
    public static final float LEAN_BODY_DROP = 3.2f / 0.5f;
    public static final float LEAN_LEG_BACK = 4.0f / 0.5f;
}
