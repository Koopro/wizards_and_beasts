package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.pose.PlayerGesture;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Plays the server's one-shot {@link PlayerGesture}s.
 *
 * <p>Posture band, above the ready hold and the item holds: a gesture is something the player chose to do now, so it
 * reads over a stance, while a cast (casting band) and every locomotion pose still read over it. Progress comes from
 * the server's start moment ({@link ClientGestureState}); the pass keeps nothing per player but its {@link KindFade}.
 *
 * <p>Third person only: first person shows the held item, which {@link FirstPersonCast} moves for the gestures that
 * have one.
 */
@NullMarked
public final class GesturePosePass extends ProceduralPosePass {

    /** Posture band, over {@link WandReadyPass} (60) and {@link ItemUsePosePass} (50). */
    public static final int PRIORITY = 70;

    static final int RISE_TICKS = 4;
    static final int FALL_TICKS = 5;

    private final KindFade<PlayerGesture> fade = new KindFade<>(RISE_TICKS, FALL_TICKS);

    public GesturePosePass() {
        super("gesture", PRIORITY);
    }

    @Override
    public void pose(PoseBuilder builder, PoseContext context) {
        if (context.firstPerson().firstPerson()) {
            return;
        }
        AvatarRenderState state = context.state();
        long now = ClientCastAnimationState.clientTick();
        double time = now + context.partialTicks();
        ClientGestureState.Active active = ClientGestureState.get(state.id, now);
        PlayerGesture kind = active == null || !stillValid(state, active.gesture(), now) ? null : active.gesture();
        KindFade.Shares<PlayerGesture> shares = fade.observe(state.id, kind, time);
        if (shares.idle()) {
            return;
        }
        if (shares.previous() != null && shares.fall() > 0f) {
            builder.opWeight(shares.fall());
            emit(builder, context, shares.previous(), 1f);
        }
        if (shares.current() != null && shares.weight() > 0f && active != null) {
            builder.opWeight(shares.weight());
            emit(builder, context, shares.current(), active.progress(time));
        }
        builder.opWeight(1f);
    }

    public void sweep(double time) {
        fade.sweep(time);
    }

    /**
     * A gesture the player has since made impossible lets go early: the wand put away, or a cast begun. The server
     * does not need to be told — the gesture is presentation, and every client sees the same render state.
     */
    private static boolean stillValid(AvatarRenderState state, PlayerGesture gesture, long now) {
        if (gesture == PlayerGesture.WAND_INSPECT) {
            return state.getMainHandItemStack().getItem() instanceof WandItem
                    && !state.isUsingItem
                    && ClientCastAnimationState.get(state.id, now) == null;
        }
        // Every other gesture is a moment the server already committed to; only sleep and swimming end it early.
        return state.bedOrientation == null && !state.isVisuallySwimming;
    }

    private static void emit(PoseBuilder builder, PoseContext context, PlayerGesture gesture, float progress) {
        if (gesture == PlayerGesture.WAND_INSPECT) {
            inspect(builder, context.state(), progress);
            return;
        }
        GesturePoseConstants.Clip clip = GesturePoseConstants.clip(gesture);
        if (clip != null) {
            play(builder, context.state(), clip, progress);
        }
    }

    /**
     * One keyframed gesture: both arms by their own shares, the head, the lean, then the circling and the tremor.
     *
     * <p>The lean is laid out as vanilla lays out its crouch — the torso pitched and every part above the hips
     * dropped, the legs moved back — so the figure bends at the waist instead of the torso pivoting off the
     * shoulders. Worn robes follow, because they copy the posed parts after the layer has run.
     */
    static void play(PoseBuilder builder, AvatarRenderState state, GesturePoseConstants.Clip clip, float progress) {
        GesturePoseConstants.Key key = clip.at(progress);
        HumanoidArm arm = state.mainArm;
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
        PlayerModelPart mainArm = arm == HumanoidArm.RIGHT ? PlayerModelPart.RIGHT_ARM : PlayerModelPart.LEFT_ARM;
        PlayerModelPart freeArm = arm == HumanoidArm.RIGHT ? PlayerModelPart.LEFT_ARM : PlayerModelPart.RIGHT_ARM;
        float outer = builder.opWeight();
        double clock = state.ageInTicks;

        float circleX = 0f;
        float circleY = 0f;
        if (clip.circleDeg() > 0f) {
            double phase = progress * clip.circleTurns() * Math.PI * 2.0;
            circleY = (float) Math.sin(phase) * clip.circleDeg();
            circleX = (float) Math.cos(phase) * clip.circleDeg() * 0.5f;
        }
        float tremor = clip.tremorDeg() * Math.min(1f, progress * 2f);
        float shakeZ = tremor * (float) Math.sin(clock * 2.9);
        float shakeX = tremor * 0.6f * (float) Math.cos(clock * 3.7);

        if (key.mainShare() > 0f) {
            builder.opWeight(outer * key.mainShare());
            builder.get(mainArm)
                    .setRotDeg(PoseTarget.X_ROT, key.main().xRot() + circleX + shakeX)
                    .setRotDeg(PoseTarget.Y_ROT, (key.main().yRot() + circleY) * side)
                    .setRotDeg(PoseTarget.Z_ROT, (key.main().zRot() + shakeZ) * side);
        }
        if (key.freeShare() > 0f) {
            builder.opWeight(outer * key.freeShare());
            builder.get(freeArm)
                    .setRotDeg(PoseTarget.X_ROT, key.free().xRot() + shakeX)
                    .setRotDeg(PoseTarget.Y_ROT, key.free().yRot() * side)
                    .setRotDeg(PoseTarget.Z_ROT, (key.free().zRot() - shakeZ) * side);
        }
        builder.opWeight(outer);
        builder.get(PlayerModelPart.HEAD).addRotDeg(PoseTarget.X_ROT, key.head());
        if (key.lean() != 0f) {
            lean(builder, key.lean());
        }
        if (tremor > 0f) {
            builder.get(PlayerModelPart.CHEST).addRotDeg(PoseTarget.Z_ROT, shakeZ * 0.4f);
        }
    }

    /** A whole-upper-body bend at the waist, vanilla-crouch style. Radians; positive leans forward. */
    static void lean(PoseBuilder builder, float rad) {
        float body = GesturePoseConstants.LEAN_BODY_DROP * rad;
        builder.get(PlayerModelPart.CHEST).add(PoseTarget.X_ROT, rad).add(PoseTarget.Y, body);
        builder.get(PlayerModelPart.HEAD).add(PoseTarget.Y, GesturePoseConstants.LEAN_HEAD_DROP * rad);
        builder.get(PlayerModelPart.RIGHT_ARM).add(PoseTarget.Y, body);
        builder.get(PlayerModelPart.LEFT_ARM).add(PoseTarget.Y, body);
        builder.get(PlayerModelPart.RIGHT_LEG).add(PoseTarget.Z, GesturePoseConstants.LEAN_LEG_BACK * rad);
        builder.get(PlayerModelPart.LEFT_LEG).add(PoseTarget.Z, GesturePoseConstants.LEAN_LEG_BACK * rad);
    }

    /**
     * The wand brought up before the eyes, turned along its length, and lowered toward the ready hold.
     *
     * <p>The player arm has no elbow, so "before the eyes" is the whole arm raised forward and turned across the
     * body. The free hand rises to steady the tip for the first half, then drops away.
     */
    static void inspect(PoseBuilder builder, AvatarRenderState state, float progress) {
        GesturePoseConstants.InspectPose pose = GesturePoseConstants.inspectAt(progress);
        HumanoidArm arm = state.mainArm;
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
        PlayerModelPart wandArm = arm == HumanoidArm.RIGHT ? PlayerModelPart.RIGHT_ARM : PlayerModelPart.LEFT_ARM;
        PlayerModelPart freeArm = arm == HumanoidArm.RIGHT ? PlayerModelPart.LEFT_ARM : PlayerModelPart.RIGHT_ARM;

        builder.get(wandArm)
                .setRotDeg(PoseTarget.X_ROT, pose.wandArm().xRot())
                .setRotDeg(PoseTarget.Y_ROT, pose.wandArm().yRot() * side)
                .setRotDeg(PoseTarget.Z_ROT, pose.wandArm().zRot() * side);
        if (pose.freeShare() > 0f) {
            // Its own weight on top of the pass's: the steadying hand leaves halfway through.
            float outer = builder.opWeight();
            builder.opWeight(outer * pose.freeShare());
            builder.get(freeArm)
                    .setRotDeg(PoseTarget.X_ROT, pose.freeArm().xRot())
                    .setRotDeg(PoseTarget.Y_ROT, pose.freeArm().yRot() * side)
                    .setRotDeg(PoseTarget.Z_ROT, pose.freeArm().zRot() * side);
            builder.opWeight(outer);
        }
        builder.get(PlayerModelPart.HEAD)
                .addRotDeg(PoseTarget.X_ROT, pose.headPitch())
                .addRotDeg(PoseTarget.Y_ROT, pose.headYaw() * side);
        builder.get(PlayerModelPart.CHEST).addRotDeg(PoseTarget.X_ROT, pose.chestPitch());
    }

    /**
     * Degrees the wand turns about its own length during an inspect, for {@code WandRenderer}; zero otherwise.
     *
     * <p>Read from the same synced start the arm reads, so the wand's turn and the arm's roll cannot drift apart
     * (schema D4: the pose layer owns the arm, the wand's renderer owns the wand, both read one clock).
     */
    public static float wandRoll(int entityId, long now, double time) {
        ClientGestureState.Active active = ClientGestureState.get(entityId, now);
        if (active == null || active.gesture() != PlayerGesture.WAND_INSPECT) {
            return 0f;
        }
        return GesturePoseConstants.inspectRoll(active.progress(time));
    }

    /** True while an entity is inspecting its wand; read by the first-person view. */
    public static ClientGestureState.@Nullable Active inspecting(int entityId, long now) {
        ClientGestureState.Active active = ClientGestureState.get(entityId, now);
        return active != null && active.gesture() == PlayerGesture.WAND_INSPECT ? active : null;
    }
}
