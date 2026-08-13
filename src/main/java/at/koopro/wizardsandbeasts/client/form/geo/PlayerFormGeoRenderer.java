package at.koopro.wizardsandbeasts.client.form.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

import java.util.UUID;

/**
 * Draws a transformed player's GeckoLib form.
 *
 * <p>{@code GeoObjectRenderer} is GeckoLib's renderer for animatables that are not entities, items or
 * block entities, and its {@code performRenderPass} takes precisely the {@code PoseStack},
 * {@code SubmitNodeCollector} and {@code CameraRenderState} that {@code LivingEntityRendererMixin}
 * already holds at the moment it cancels the vanilla player render. No second render pipeline is
 * introduced and no entity is touched at render time — everything the pass needs travels as
 * {@link DataTicket}s on the render state.
 *
 * <p>One renderer, reused for every transformed player. It is stateless; the per-player playhead
 * lives on {@link PlayerFormAnimatable}, one instance per UUID.
 */
@NullMarked
public final class PlayerFormGeoRenderer extends GeoObjectRenderer<PlayerFormAnimatable, Void, GeoRenderState> {

    /**
     * Body yaw in degrees, for {@link #adjustRenderPose}.
     *
     * <p>A ticket rather than a field because {@code adjustRenderPose} runs on the render thread and
     * is handed only the render state. A field would be read by whichever player happened to draw
     * last, which is the same class of bug the form data on the render state was built to avoid.
     */
    public static final DataTicket<Float> TICKET_BODY_YAW =
            DataTicket.create("player_form_body_yaw", Float.class);

    /** Vanilla's entity-model vertical offset: these models are authored with the origin at the top. */
    private static final float MODEL_Y_OFFSET = -1.501f;

    private static @Nullable PlayerFormGeoRenderer instance;

    private PlayerFormGeoRenderer() {
        super(new PlayerFormGeoModel());
    }

    /**
     * Created on first use rather than in a static field.
     *
     * <p>Constructing a {@code GeoModel} touches GeckoLib's asset caches, which are not ready during
     * mod construction.
     */
    public static PlayerFormGeoRenderer get() {
        PlayerFormGeoRenderer local = instance;
        if (local == null) {
            local = new PlayerFormGeoRenderer();
            instance = local;
        }
        return local;
    }

    /**
     * Renders {@code formId} for one player, on top of whatever transform the caller has applied.
     *
     * <p>The caller owns scale and world position — the mixin has already applied the form's size
     * profile to the pose stack, and applying it again here would square it.
     *
     * @param walkSpeed vanilla's limb-swing amplitude, which selects idle against the movement clip
     * @param bodyYaw   body rotation in degrees, applied in {@link #adjustRenderPose}
     * @return false when the form has no rig, so the caller can fall back to the legacy model
     */
    public static boolean render(String formId, UUID playerUUID, float walkSpeed, float bodyYaw,
                                 PoseStack poseStack, SubmitNodeCollector collector,
                                 CameraRenderState camera, int packedLight) {
        PlayerFormRig rig = PlayerFormRig.forForm(formId);
        if (rig == null) {
            return false;
        }
        PlayerFormAnimatable animatable = PlayerFormAnimatable.forPlayer(playerUUID, rig, walkSpeed);
        BODY_YAW.set(bodyYaw);
        try {
            get().performRenderPass(animatable, null, poseStack, collector, camera,
                    packedLight, OverlayTexture.NO_OVERLAY);
        } finally {
            BODY_YAW.remove();
        }
        return true;
    }

    /** Convenience overload reading motion, facing and light straight off a living render state. */
    public static boolean render(String formId, UUID playerUUID, LivingEntityRenderState state,
                                 PoseStack poseStack, SubmitNodeCollector collector,
                                 CameraRenderState camera) {
        return render(formId, playerUUID, state.walkAnimationSpeed, state.bodyRot,
                poseStack, collector, camera, state.lightCoords);
    }

    /**
     * Carries the yaw from {@link #render} into {@link #addRenderData}.
     *
     * <p>{@code performRenderPass} builds the render state itself, so there is no seam to pass a
     * value through. Thread-local rather than a plain field because render-state creation and the
     * render pass happen on the same thread within one call, and nothing may leak between players —
     * hence the {@code finally} that clears it.
     */
    private static final ThreadLocal<Float> BODY_YAW = new ThreadLocal<>();

    /** Puts the rig and facing on the render state, where the model and the pose adjustment read them. */
    @Override
    public void addRenderData(PlayerFormAnimatable animatable, @Nullable Void unused,
                              GeoRenderState renderState, float partialTick) {
        super.addRenderData(animatable, unused, renderState, partialTick);
        renderState.addGeckolibData(PlayerFormGeoModel.TICKET_RIG, animatable.rig());
        Float yaw = BODY_YAW.get();
        renderState.addGeckolibData(TICKET_BODY_YAW, yaw != null ? yaw : 0.0f);
    }

    /**
     * Entity-space setup, replacing the inherited block-entity one.
     *
     * <p>{@code GeoObjectRenderer}'s own implementation translates by {@code (0.5, 0.51, 0.5)} —
     * correct for something drawn from a block corner, and half a block wrong in three axes for a
     * player. What a player form needs is what every entity render does: yaw to face the body
     * direction, flip to the Y-down convention the baked model is authored in, then drop the origin
     * from the model's top to its feet.
     *
     * <p>Identical to the transform {@code FormModelRenderer.renderVanilla} applies to the borrowed
     * Animagus models, so the two paths place a model the same way.
     */
    @Override
    public void adjustRenderPose(RenderPassInfo<GeoRenderState> info) {
        PoseStack poseStack = info.poseStack();
        float bodyYaw = info.getOrDefaultGeckolibData(TICKET_BODY_YAW, 0.0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        poseStack.translate(0.0f, MODEL_Y_OFFSET, 0.0f);
    }
}
