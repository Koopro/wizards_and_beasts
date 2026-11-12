package at.koopro.wizardsandbeasts.client.entity;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.layer.builtin.ItemInHandGeoLayer;

import java.util.List;

/**
 * Draws what a creature holds in its main hand as the real item, the way a skeleton holds its bow.
 *
 * <p>A rig opts in by having a bone called {@value #BONE} at its hand; a rig without one draws nothing and logs
 * nothing (GeckoLib's own layer reports a missing bone on every frame). Which arm carries the bone is the rig's
 * business: the centaur and the pukwudgie hold their bows in the left hand and draw with the right, whatever
 * handedness vanilla rolled for the mob.
 *
 * <p>The item is scaled to the creature: a bow drawn at a player's size is as tall as a pukwudgie.
 */
public class BeastHeldItemLayer<R extends EntityRenderState & GeoRenderState>
        extends ItemInHandGeoLayer<GenericBeastEntity, Void, R> {

    public static final String BONE = "held_item";
    /** The height of the body a vanilla held item is sized for. */
    private static final float PLAYER_HEIGHT = 1.9f;

    public BeastHeldItemLayer(GeoRenderer<GenericBeastEntity, Void, R> renderer) {
        super(renderer, BONE, BONE);
    }

    @Override
    protected List<RenderData<R>> getRelevantBones(R renderState, BakedGeoModel model) {
        if (model.getBone(BONE).isEmpty()) {
            return List.of();
        }
        return List.of(renderDataForHand(BONE, HumanoidArm.RIGHT, false, renderState));
    }

    @Override
    protected void submitItemStackRender(PoseStack poseStack, GeoBone bone, ItemStack stack,
                                         ItemDisplayContext displayContext, R renderState,
                                         SubmitNodeCollector renderTasks, CameraRenderState cameraState,
                                         int packedLight, int packedOverlay, int renderColor) {
        float scale = Mth.clamp(renderState.boundingBoxHeight / PLAYER_HEIGHT, 0.55f, 1.0f);
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        super.submitItemStackRender(poseStack, bone, stack, displayContext, renderState, renderTasks, cameraState,
                packedLight, packedOverlay, renderColor);
        poseStack.popPose();
    }
}
