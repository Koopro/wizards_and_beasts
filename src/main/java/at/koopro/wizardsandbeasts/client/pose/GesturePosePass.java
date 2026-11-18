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
        return switch (gesture) {
            case WAND_INSPECT -> state.getMainHandItemStack().getItem() instanceof WandItem
                    && !state.isUsingItem
                    && ClientCastAnimationState.get(state.id, now) == null;
        };
    }

    private static void emit(PoseBuilder builder, PoseContext context, PlayerGesture gesture, float progress) {
        switch (gesture) {
            case WAND_INSPECT -> inspect(builder, context.state(), progress);
        }
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
