package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The cast in first person: the held wand rises and draws back on the wind-up, thrusts out on the release and
 * settles on recovery, read from the same {@link ClientCastAnimationState} the third-person {@link CastPosePass}
 * reads, so both views play one synced cast.
 *
 * <p>Why the wand and not the arm: holding an item, vanilla draws the item in first person and not the arm, so the
 * pose layer's first-person arm branch never shows during a wand cast. Moving the item is the whole of what a
 * player can see.
 *
 * <p><b>Leak guard.</b> {@link RenderHandEvent} fires before vanilla draws the hand, with no push/pop of its own
 * around it, and the off hand is drawn after the main hand on the same pose stack. So the transform applied to the
 * main hand is undone on the off hand's event. A frame with no off-hand draw has nothing after it to leak into.
 */
@NullMarked
public final class FirstPersonCast {

    /** Where vanilla puts a held item's hand in first person ({@code ItemInHandRenderer.applyItemArmTransform}). */
    private static final float HAND_X = 0.56f;
    private static final float HAND_Y = -0.52f;
    private static final float HAND_Z = -0.72f;

    /** The transform applied to this frame's main hand, to undo on the off hand; null when none was. */
    private static float[] applied;

    private FirstPersonCast() {}

    public static void onRenderHand(RenderHandEvent event) {
        PoseStack pose = event.getPoseStack();
        if (event.getHand() == InteractionHand.OFF_HAND) {
            if (applied != null) {
                undo(pose, applied);
                applied = null;
            }
            return;
        }
        applied = null;
        if (!ModuleManager.isEnabled(Module.PLAYER_ANIMATION)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(event.getItemStack().getItem() instanceof WandItem)) {
            return;
        }
        long now = ClientCastAnimationState.clientTick();
        ClientCastAnimationState.ActiveCast cast = ClientCastAnimationState.get(player.getId(), now);
        if (cast == null) {
            return;
        }
        float progress = cast.progress(now, event.getPartialTick());
        CastPoseConstants.FirstPersonPose fp = CastPoseConstants.firstPersonAt(cast, progress);
        float side = player.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
        float[] t = {side * HAND_X, HAND_Y, HAND_Z, fp.up(), -fp.forward(), fp.pitch()};
        apply(pose, t);
        applied = t;
    }

    /** offset, then the pitch about the hand: T(offset) · T(hand) · R · T(-hand). */
    private static void apply(PoseStack pose, float[] t) {
        pose.translate(0f, t[3], t[4]);
        pose.translate(t[0], t[1], t[2]);
        pose.mulPose(Axis.XP.rotationDegrees(t[5]));
        pose.translate(-t[0], -t[1], -t[2]);
    }

    /** The exact inverse of {@link #apply}: T(hand) · R⁻¹ · T(-hand) · T(-offset). */
    private static void undo(PoseStack pose, float[] t) {
        pose.translate(t[0], t[1], t[2]);
        pose.mulPose(Axis.XP.rotationDegrees(-t[5]));
        pose.translate(-t[0], -t[1], -t[2]);
        pose.translate(0f, -t[3], -t[4]);
    }
}
