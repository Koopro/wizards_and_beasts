package at.koopro.wizardsandbeasts.client.pose;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The pose layer's entry point from {@code PlayerModelMixin}.
 *
 * <p>A handler class rather than logic in the mixin, matching the three delegates already on that
 * hook. It runs fourth, after {@code BroomRiderPoseHandler}, {@code PetrifyRenderHandler} and
 * {@code ModelDebugPartTransforms} — those are not migrated into passes in wave 1, by ruling, and
 * the band table records them as a legacy row so it does not misrepresent what is posing the model.
 */
@NullMarked
public final class PlayerPoseHandler {

    /**
     * The whole-avatar transform, carried on the render state.
     *
     * <p>Deliberately not a static field on this class. A cleared-on-read accessor is the global
     * mutable state the schema prohibits: it silently couples two hooks whose ordering is not
     * guaranteed, and it goes wrong the moment two players render in one frame. The render state is
     * allocated fresh per entity per frame, so a state with no entry is simply a player nobody
     * posed — nothing to clear, and no way for an early-returning render pass to strand a value.
     *
     * <p>Same mechanism {@code BroomRiderRenderer.BROOM_RIDE} already uses for the rider's tilt.
     */
    public static final ContextKey<PoseStackResult> BODY_TRANSFORM =
            new ContextKey<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "pose_body_transform"));

    private PlayerPoseHandler() {}

    /** Called at the tail of {@code PlayerModel.setupAnim}, once vanilla has finished posing. */
    public static void applyPose(PlayerModel model, AvatarRenderState state) {
        PoseStackResult body = PlayerPoseLayer.get()
                .run(model, state, FirstPersonContext.NONE, partialTicks());
        if (body != null && !body.isIdentity()) {
            state.setRenderData(BODY_TRANSFORM, body);
        }
    }

    /**
     * The whole-avatar transform for this render state, or null when nothing posed the body.
     *
     * <p><b>Nothing consumes this yet.</b> {@code setupRotations} runs before {@code setupAnim} in
     * the render path, so the site that would apply a body transform has already gone by the time
     * the layer computes one. Choosing and justifying that application site is its own audited hook
     * — see schema §3.9 — and until it exists whole-body pitch does not render. Left visibly
     * incomplete rather than faked.
     */
    public static @Nullable PoseStackResult bodyTransform(EntityRenderState state) {
        return state.getRenderData(BODY_TRANSFORM);
    }

    /**
     * The true frame fraction.
     *
     * <p>Not {@code state.ageInTicks}, which is a clock rather than a fraction between two ticks.
     * The schema is explicit that conflating them makes every {@code PhaseTimer} interpolation
     * stutter, so the value is taken from the client's own delta tracker.
     */
    private static float partialTicks() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }
}
