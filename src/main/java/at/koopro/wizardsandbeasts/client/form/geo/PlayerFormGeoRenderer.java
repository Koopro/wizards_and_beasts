package at.koopro.wizardsandbeasts.client.form.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
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
     * @param attacking true while the player's swing is in progress
     * @param hurt      true while the player is showing vanilla's damage flash
     * @return false when the form has no rig, so the caller can fall back to the legacy model
     */
    public static boolean render(String formId, UUID playerUUID, float walkSpeed, float bodyYaw,
                                 boolean attacking, boolean hurt,
                                 PoseStack poseStack, SubmitNodeCollector collector,
                                 CameraRenderState camera, int packedLight) {
        PlayerFormRig rig = PlayerFormRig.forForm(formId);
        if (rig == null) {
            return false;
        }
        PlayerFormAnimatable animatable =
                PlayerFormAnimatable.forPlayer(playerUUID, rig, walkSpeed, attacking, hurt);
        BODY_YAW.set(bodyYaw);
        try {
            get().performRenderPass(animatable, null, poseStack, collector, camera,
                    packedLight, OverlayTexture.NO_OVERLAY);
        } finally {
            BODY_YAW.remove();
        }
        return true;
    }

    /**
     * Convenience overload reading motion, facing, light <b>and both reaction states</b> straight off
     * a living render state.
     *
     * <p>No new networking was needed for attack and hurt: {@code attackTime} is vanilla's own swing
     * progress, carried on {@code ArmedEntityRenderState} which a player's state extends, and
     * {@code hasRedOverlay} is its damage flash. Both are already computed and already here.
     */
    public static boolean render(String formId, UUID playerUUID, LivingEntityRenderState state,
                                 PoseStack poseStack, SubmitNodeCollector collector,
                                 CameraRenderState camera) {
        boolean attacking = state instanceof ArmedEntityRenderState armed && armed.attackTime > 0.0f;
        return render(formId, playerUUID, state.walkAnimationSpeed, state.bodyRot,
                attacking, state.hasRedOverlay,
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
     * player. So it does have to be replaced. What replaces it is <b>only a yaw</b>.
     *
     * <p><b>Do not add vanilla's {@code scale(-1, -1, 1)} / {@code translate(0, -1.501, 0)} here.</b>
     * That pair is the vanilla <em>model</em> convention — origin at the top, +Y running down toward
     * the feet — and a GeckoLib baked model is not in that space. GeckoLib's loader has already
     * converted the geometry on load, leaving the model upright with its origin at the feet, which is
     * why {@code GeoEntityRenderer.adjustRenderPose} applies a yaw and a 0.01 nudge and nothing else.
     * Applying the vanilla pair on top of an already-converted model flips it upside down, mirrors it
     * inside out, and buries it a block and a half into the floor — all three at once, which is
     * exactly what the first version of this method did.
     *
     * <p>This is the {@code diag(1,-1,1)} versus {@code scale(-1,-1,1)} trap the stack rules warn
     * about in as many words: <i>do not mix them</i>. The borrowed-vanilla path in
     * {@code FormModelRenderer.renderVanilla} genuinely does need the vanilla pair, because those
     * really are vanilla models. The two paths must therefore <b>not</b> share a transform, however
     * much they look like they should.
     *
     * <p>The yaw itself matches {@code GeoEntityRenderer.applyRotations} exactly:
     * {@code YP.rotationDegrees(180 - bodyYaw)}. The trailing nudge is GeckoLib's own, and is there to
     * keep the model off the surface it stands on.
     */
    @Override
    public void adjustRenderPose(RenderPassInfo<GeoRenderState> info) {
        PoseStack poseStack = info.poseStack();
        float bodyYaw = info.getOrDefaultGeckolibData(TICKET_BODY_YAW, 0.0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
        poseStack.translate(0.0f, 0.01f, 0.0f);
    }
}
