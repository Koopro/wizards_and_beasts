package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.model.ChocolateFrogModel;
import at.koopro.wizardsandbeasts.entity.frog.ChocolateFrogEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

/**
 * Draws an escaped Chocolate Frog as a frog.
 *
 * <p>It used to be vanilla's {@link net.minecraft.client.renderer.entity.ItemEntityRenderer}, which
 * was the right call while the frog was only a mechanic — an {@code ItemEntity} that hops needs no
 * art to work. It reads wrong, though: a spinning flat sprite is the universal Minecraft signal for
 * "loot on the floor", and the whole joke is that this one is running away from you.
 *
 * <p>Everything here is derived, nothing is synced. Facing comes from the entity's own smoothed
 * heading, and the hop pose from its vertical velocity — both already on the client, because
 * {@code ItemEntity} motion is part of the entity's normal tracking.
 */
@NullMarked
public class ChocolateFrogRenderer extends EntityRenderer<ChocolateFrogEntity, ChocolateFrogRenderer.FrogRenderState> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/entity/chocolate_frog.png");

    /** Model units to blocks. The mesh is drawn ~7px long; a real frog is a hand's width. */
    private static final float SCALE = 0.0625f;
    /**
     * Vertical speed that counts as "fully airborne". The hop kick is 0.42, so a frog reads as
     * launched almost immediately and settles back over the fall.
     */
    private static final float AIRBORNE_SPEED = 0.30f;
    /** How far the frog stretches along its jump axis at the top of a hop. */
    private static final float MAX_STRETCH = 0.28f;

    private final ChocolateFrogModel model = new ChocolateFrogModel();

    public ChocolateFrogRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.16f;
        this.shadowStrength = 0.7f;
    }

    @Override
    public FrogRenderState createRenderState() {
        return new FrogRenderState();
    }

    @Override
    public void extractRenderState(ChocolateFrogEntity frog, FrogRenderState state, float partialTick) {
        super.extractRenderState(frog, state, partialTick);
        state.facingYaw = frog.renderYaw(partialTick);
        Vec3 motion = frog.getDeltaMovement();
        // Signed, not absolute: rising stretches the frog upward, falling squashes it back down,
        // which is what sells a hop as a hop rather than a hover.
        state.verticalSpeed = (float) motion.y;
        state.grounded = frog.onGround();
    }

    @Override
    public void submit(FrogRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);

        float airborne = state.grounded
                ? 0.0f
                : Mth.clamp(Math.abs(state.verticalSpeed) / AIRBORNE_SPEED, 0.0f, 1.0f);
        float stretch = Mth.clamp(state.verticalSpeed / AIRBORNE_SPEED, -1.0f, 1.0f) * MAX_STRETCH;

        model.setup(airborne, state.ageInTicks);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - state.facingYaw));
        // Squash and stretch preserve volume about the feet, which is why the root pivot is on the
        // ground: scaling y here lengthens the frog upward instead of sinking it into the block.
        poseStack.scale((1.0f - stretch * 0.6f), (1.0f + stretch), (1.0f - stretch * 0.6f));
        // Entity model space is y-down; the flip also puts the mesh's ground plane on the entity's.
        poseStack.scale(-SCALE, -SCALE, SCALE);

        RenderType renderType = RenderTypes.entityCutoutNoCull(TEXTURE);
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            PoseStack temp = new PoseStack();
            temp.last().pose().set(pose.pose());
            temp.last().normal().set(pose.normal());
            model.render(temp, consumer, state.lightCoords, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        });
        poseStack.popPose();
    }

    public static class FrogRenderState extends EntityRenderState {
        public float facingYaw;
        public float verticalSpeed;
        public boolean grounded;
    }
}
