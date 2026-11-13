package at.koopro.wizardsandbeasts.client.equipment.curios;

import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Draws a piece of jewellery worn in a Curios slot using the item's own model: a pendant hangs on the chest from the
 * body, a diadem sits on the brow from the head, so each follows the wearer's turns and bows.
 *
 * <p>The wardrobe renderer ({@link WornGeoCurioRenderer}) only knows armour-built pieces; the Time-Turner was a
 * necklace that drew nothing when worn, and the locket and the diadem could not be worn at all. Same pattern as
 * vanilla's head-item layer: move into the body part's frame, turn to face front, scale down, draw the item model.
 *
 * <p>First person: nothing, like every worn piece.
 */
@NullMarked
public final class JewelleryCurioRenderer implements ICurioRenderer {

    /** Where the piece sits. */
    public enum Placement {
        /** On the chest, hung from the body part. */
        PENDANT(0.0F, 0.32F, -0.16F, 0.32F, 0.32F, 180.0F),
        /**
         * On the brow, from the head part. The band (4 to 5 px from the middle) is sized to the head (4 px) and kept
         * low so the eagle stands above the brow rather than towering; turned a quarter so the eagle faces front.
         */
        CIRCLET(0.0F, -0.06F, 0.0F, 1.0F, 0.7F, 90.0F);

        final float x;
        final float y;
        final float z;
        final float wide;
        final float tall;
        final float yaw;

        Placement(float x, float y, float z, float wide, float tall, float yaw) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.wide = wide;
            this.tall = tall;
            this.yaw = yaw;
        }
    }

    private final Placement placement;

    public JewelleryCurioRenderer(Placement placement) {
        this.placement = placement;
    }

    @Override
    public <S extends LivingEntityRenderState, M extends EntityModel<? super S>> void render(
            ItemStack stack, SlotContext slotContext, PoseStack poseStack, SubmitNodeCollector collector,
            int packedLight, S renderState, RenderLayerParent<S, M> renderLayerParent,
            EntityRendererProvider.Context context, float yRotation, float xRotation) {
        if (!(renderLayerParent.getModel() instanceof HumanoidModel<?> model) || renderState.isInvisible) {
            return;
        }
        poseStack.pushPose();
        if (placement == Placement.CIRCLET) {
            model.head.translateAndRotate(poseStack);
        } else {
            model.body.translateAndRotate(poseStack);
        }
        poseStack.translate(placement.x, placement.y, placement.z);
        // Model space is drawn upside down and mirrored; turn the item to face the way the wearer faces.
        poseStack.mulPose(Axis.YP.rotationDegrees(placement.yaw));
        poseStack.scale(placement.wide, -placement.tall, -placement.wide);
        ItemStackRenderState item = new ItemStackRenderState();
        Minecraft mc = Minecraft.getInstance();
        // HEAD, not FIXED: the item's lookup sends gui/ground/fixed to the flat icon and every other context to
        // its GeckoLib model, and a worn piece should be the model.
        mc.getItemModelResolver().updateForTopItem(item, stack, ItemDisplayContext.HEAD, slotContext.entity().level(),
                slotContext.entity(), slotContext.entity().getId());
        item.submit(poseStack, collector, packedLight, OverlayTexture.NO_OVERLAY, renderState.outlineColor);
        poseStack.popPose();
    }
}
