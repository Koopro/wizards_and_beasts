package at.koopro.wizardsandbeasts.client.pose;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The pose layer's entry point from {@code PlayerModelMixin}.
 *
 * <p>A handler class rather than logic in the mixin, matching the three delegates already on that
 * hook. It runs fourth, after {@code BroomRiderPoseHandler}, {@code PetrifyRenderHandler} and
 * {@code ModelDebugPartTransforms} — those are not migrated into passes in wave 1, by ruling, and
 * the band table records them as a legacy row so it does not misrepresent what is posing the model.
 *
 * <p>The whole-avatar transform this produces is held for the renderer to consume; a
 * {@code ModelPart}-based hook has no PoseStack in scope, so {@link PlayerModelPart#BODY} cannot be
 * applied here. It is exposed via {@link #consumeBodyTransform()} for the render-side caller.
 */
@NullMarked
public final class PlayerPoseHandler {

    private static @Nullable PoseStackResult pendingBodyTransform;

    private PlayerPoseHandler() {}

    /** Called at the tail of {@code PlayerModel.setupAnim}, once vanilla has finished posing. */
    public static void applyPose(PlayerModel model, AvatarRenderState state) {
        float partialTicks = partialTicks();
        pendingBodyTransform =
                PlayerPoseLayer.get().run(model, state, FirstPersonContext.NONE, partialTicks);
    }

    /**
     * The whole-avatar transform from the most recent {@link #applyPose}, cleared on read.
     *
     * <p>Cleared so a frame that poses nothing cannot inherit the previous frame's body transform —
     * the failure mode there is a player who stays tilted after the pose releases.
     */
    public static @Nullable PoseStackResult consumeBodyTransform() {
        PoseStackResult result = pendingBodyTransform;
        pendingBodyTransform = null;
        return result;
    }

    /**
     * The true frame fraction.
     *
     * <p>Not {@code state.ageInTicks}, which is a clock rather than a fraction between two ticks.
     * The schema is explicit that conflating them makes every {@code PhaseTimer} interpolation
     * stutter, so the value is taken from the client's own delta tracker.
     */
    private static float partialTicks() {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        return minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }
}
